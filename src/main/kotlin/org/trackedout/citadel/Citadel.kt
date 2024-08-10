package org.trackedout.citadel

import co.aikar.commands.PaperCommandManager
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import me.devnatan.inventoryframework.ViewFrame
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary
import okhttp3.OkHttpClient
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.trackedout.citadel.commands.GiveShulkerCommand
import org.trackedout.citadel.commands.InventoryCommand
import org.trackedout.citadel.commands.LogEventCommand
import org.trackedout.citadel.commands.ManageDeckCommand
import org.trackedout.citadel.commands.SavePlayerDeckCommand
import org.trackedout.citadel.commands.StatusCommand
import org.trackedout.citadel.commands.TakeShulkerCommand
import org.trackedout.citadel.inventory.AddACardView
import org.trackedout.citadel.inventory.CardActionView
import org.trackedout.citadel.inventory.DeckInventoryView
import org.trackedout.citadel.inventory.DeckManagementView
import org.trackedout.citadel.inventory.EnterQueueView
import org.trackedout.citadel.inventory.MoveCardView
import org.trackedout.citadel.inventory.ShopView
import org.trackedout.citadel.listeners.EchoShardListener
import org.trackedout.citadel.listeners.PlayedJoinedListener
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.apis.ScoreApi
import org.trackedout.client.apis.StatusApi
import org.trackedout.client.apis.TasksApi
import org.trackedout.client.infrastructure.ClientError
import org.trackedout.client.infrastructure.ClientException
import java.net.InetAddress
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class Citadel : JavaPlugin() {
    private val manager: PaperCommandManager by lazy { PaperCommandManager(this) }
    private lateinit var scoreboardLibrary: ScoreboardLibrary
    val serverName by lazy { getEnvOrDefault("SERVER_NAME", InetAddress.getLocalHost().hostName) }
    val dungaAPIPath by lazy { getEnvOrDefault("DUNGA_API", "http://localhost:3000/v1") }

    override fun onEnable() {
        saveDefaultConfig()


        val eventsApi = EventsApi(
            basePath = dungaAPIPath,
            client = OkHttpClient.Builder()
                .connectTimeout(5.seconds.toJavaDuration())
                .callTimeout(30.seconds.toJavaDuration())
                .build()
        )

        val inventoryApi = InventoryApi(
            basePath = dungaAPIPath,
            client = OkHttpClient.Builder()
                .connectTimeout(5.seconds.toJavaDuration())
                .callTimeout(30.seconds.toJavaDuration())
                .build()
        )

        val tasksApi = TasksApi(
            basePath = dungaAPIPath,
            client = OkHttpClient.Builder()
                .connectTimeout(5.seconds.toJavaDuration())
                .callTimeout(60.seconds.toJavaDuration())
                .build()
        )

        val scoreApi = ScoreApi(
            basePath = dungaAPIPath,
            client = OkHttpClient.Builder()
                .connectTimeout(5.seconds.toJavaDuration())
                .callTimeout(60.seconds.toJavaDuration())
                .build()
        )

        val statusApi = StatusApi(
            basePath = dungaAPIPath,
            client = OkHttpClient.Builder()
                .connectTimeout(5.seconds.toJavaDuration())
                .callTimeout(60.seconds.toJavaDuration())
                .build()
        )

        val inventoryManager = InventoryManager(this, scoreApi)

        // https://github.com/aikar/commands/wiki/Real-World-Examples
        manager.registerCommand(TakeShulkerCommand())
        manager.registerCommand(GiveShulkerCommand(eventsApi, inventoryApi))
        manager.registerCommand(InventoryCommand(eventsApi, inventoryApi, inventoryManager))
        manager.registerCommand(LogEventCommand(eventsApi))
        manager.registerCommand(SavePlayerDeckCommand(inventoryApi))
        manager.registerCommand(StatusCommand())
        manager.setDefaultExceptionHandler { _, _, sender, _, throwable ->
            sender.sendMessage("Error executing command: ${throwable.message}")

            true
        }

        server.messenger.registerOutgoingPluginChannel(this, "BungeeCord")

        val scheduledTaskRunner = ScheduledTaskRunner(this, tasksApi, inventoryManager)
        scheduledTaskRunner.runTaskTimerAsynchronously(this, 20 * 5, 60) // Repeat every 60 ticks (3 seconds)

        try {
            scoreboardLibrary = ScoreboardLibrary.loadScoreboardLibrary(this)
        } catch (e: NoPacketAdapterAvailableException) {
            // If no packet adapter was found, fall back to the no-op implementation
            scoreboardLibrary = NoopScoreboardLibrary()
            logger.warning("No scoreboard packet adapter available!")
        }

        val sidebar = scoreboardLibrary.createSidebar()
        val statusTaskRunner = StatusTaskRunner(this, statusApi, sidebar)
        statusTaskRunner.runTaskTimerAsynchronously(this, 20 * 5, 60) // Repeat every 60 ticks (3 seconds)

        server.pluginManager.registerEvents(PlayedJoinedListener(this, eventsApi, scoreApi, inventoryManager), this)

        val viewFrame: ViewFrame = ViewFrame.create(this)
            .with(
                AddACardView(),
                CardActionView(),
                MoveCardView(),
                DeckInventoryView(),
                DeckManagementView(),
                EnterQueueView(),
                ShopView(),
            )
            .register()
        manager.registerCommand(ManageDeckCommand(this, inventoryApi, eventsApi, viewFrame))

        val echoShardListener = EchoShardListener(this, inventoryApi, eventsApi, viewFrame, inventoryManager)
        server.pluginManager.registerEvents(echoShardListener, this)

        logger.info("Citadel has been enabled. Server name: $serverName")
    }

    override fun onDisable() {
        logger.info("Citadel has been disabled")
        Bukkit.getScheduler().cancelTasks(this)
        server.messenger.unregisterIncomingPluginChannel(this)
        scoreboardLibrary.close()
    }

    fun debug(message: String?) {
        if (this.config.getBoolean("debug")) {
            logger.info(message)
        }
    }

    private fun getEnvOrDefault(key: String, default: String): String {
        var value = System.getenv(key)
        if (value == null || value.isEmpty()) {
            value = default
        }
        return value
    }
}

private val gson = Gson()

fun Citadel.async(source: CommandSender, unit: () -> Unit) {
    if (source is Player) {
        object : BukkitRunnable() {
            override fun run() {
                try {
                    unit()
                } catch (e: Exception) {
                    val message = getMessage(e)
                    logger.severe("Error in async process: $message")
                    source.sendRedMessage("$message")
                    e.printStackTrace()
                }
            }
        }.runTaskAsynchronously(this)
    } else {
        // Command Block commands must be executed on the main thread
        try {
            unit()
        } catch (e: Exception) {
            val message = getMessage(e)
            logger.severe("Error in command block process: $message")
            source.sendRedMessage("$message")
            e.printStackTrace()
        }
    }
}

private fun getMessage(e: Exception): String? {
    var message = e.message
    if (e is ClientException && e.response is ClientError<*>) {
        message = ((e.response as ClientError<*>).body as String).ifEmpty { e.message }
        try {
            gson.fromJson(message, DungaDungaException::class.java).message?.let { message = it }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return message
}

data class DungaDungaException(
    @SerializedName("code") var code: Number? = null,
    @SerializedName("message") var message: String? = null,
    @SerializedName("stack") var stack: String? = null,
)
