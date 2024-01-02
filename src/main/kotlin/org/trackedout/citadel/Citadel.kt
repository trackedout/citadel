package org.trackedout.citadel

import co.aikar.commands.PaperCommandManager
import okhttp3.OkHttpClient
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.trackedout.citadel.commands.GiveShulkerCommand
import org.trackedout.citadel.commands.InventoryCommand
import org.trackedout.citadel.commands.LogEventCommand
import org.trackedout.citadel.commands.TakeShulkerCommand
import org.trackedout.citadel.listeners.PlayedJoinedListener
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.apis.TasksApi
import java.net.InetAddress
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration


class Citadel : JavaPlugin() {
    private val manager: PaperCommandManager by lazy { PaperCommandManager(this) }
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

        // https://github.com/aikar/commands/wiki/Real-World-Examples
        manager.registerCommand(TakeShulkerCommand())
        manager.registerCommand(GiveShulkerCommand(eventsApi, inventoryApi))
        manager.registerCommand(InventoryCommand(eventsApi, inventoryApi))
        manager.registerCommand(LogEventCommand(eventsApi))
        manager.setDefaultExceptionHandler { _, _, sender, _, throwable ->
            sender.sendMessage("Error executing command: ${throwable.message}")

            true
        }

        server.messenger.registerOutgoingPluginChannel(this, "BungeeCord");

        val scheduledTaskRunner = ScheduledTaskRunner(this, tasksApi)
        scheduledTaskRunner.runTaskTimerAsynchronously(this, 20 * 5, 60) // Repeat every 60 ticks (3 seconds)

        server.pluginManager.registerEvents(PlayedJoinedListener(this, eventsApi), this)
        logger.info("Citadel has been enabled. Server name: $serverName")
    }

    override fun onDisable() {
        logger.info("Citadel has been disabled")
        Bukkit.getScheduler().cancelTasks(this)
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

fun Citadel.async(source: CommandSender, unit: () -> Unit) {
    if (source is Player) {
        object : BukkitRunnable() {
            override fun run() {
                try {
                    unit()
                } catch (e: Exception) {
                    logger.severe("Error in async process: ${e.message}")
                    source.sendRedMessage("${e.message}")
                    e.printStackTrace()
                }
            }
        }.runTaskAsynchronously(this)
    } else {
        // Command Block commands should be executed on the main thread
        try {
            unit()
        } catch (e: Exception) {
            logger.severe("Error in command block process: ${e.message}")
            source.sendRedMessage("${e.message}")
            e.printStackTrace()
        }
    }
}
