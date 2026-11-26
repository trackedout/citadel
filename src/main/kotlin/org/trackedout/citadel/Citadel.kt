package org.trackedout.citadel

import co.aikar.commands.InvalidCommandArgument
import co.aikar.commands.PaperCommandManager
import com.google.gson.annotations.SerializedName
import me.devnatan.inventoryframework.ViewFrame
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar
import okhttp3.OkHttpClient
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.trackedout.citadel.commands.BookCommand
import org.trackedout.citadel.commands.ConfigCommand
import org.trackedout.citadel.config.CatalogCache
import org.trackedout.citadel.config.LeagueCache
import org.trackedout.citadel.config.RunModes
import org.trackedout.citadel.commands.party.PartyCommand
import org.trackedout.citadel.commands.CubbyAdminCommand
import org.trackedout.citadel.commands.CubbyPruneCommand
import org.trackedout.citadel.cubby.CubbyArchive
import org.trackedout.citadel.cubby.CubbyReset
import org.trackedout.citadel.cubby.CubbyTemplateStore
import org.trackedout.citadel.cubby.CubbyWorld
import org.trackedout.citadel.commands.CubbyManagementCommand
import org.trackedout.citadel.commands.InventoryCommand
import org.trackedout.citadel.commands.LeaderboardCommand
import org.trackedout.citadel.commands.LeagueCommand
import org.trackedout.citadel.commands.WorldCommand
import org.trackedout.citadel.commands.WorldEditor
import org.trackedout.citadel.commands.LogEventCommand
import org.trackedout.citadel.commands.CopyDeckCommand
import org.trackedout.citadel.commands.ManageDeckCommand
import org.trackedout.citadel.commands.ScheduleJobCommand
import org.trackedout.citadel.commands.ScoreManagementCommand
import org.trackedout.citadel.commands.ShowArtifakesCommand
import org.trackedout.citadel.commands.ShulkerStyleCommand
import org.trackedout.citadel.commands.ShutdownDungeonsCommand
import org.trackedout.citadel.commands.SpectateCommand
import org.trackedout.citadel.commands.StatusCommand
import org.trackedout.citadel.commands.TestQueueCommand
import org.trackedout.citadel.commands.UnstuckCommand
import org.trackedout.citadel.commands.TrophyCommand
import org.trackedout.citadel.commands.loadTrophySections
import org.trackedout.citadel.commands.editableConfigs
import org.trackedout.citadel.commands.shulkerColors
import org.trackedout.citadel.commands.toggleableConfigs
import org.trackedout.citadel.inventory.AddACardView
import org.trackedout.citadel.inventory.AddAnItemView
import org.trackedout.citadel.inventory.BasicItemView
import org.trackedout.citadel.inventory.CardActionView
import org.trackedout.citadel.inventory.DeckInventoryView
import org.trackedout.citadel.inventory.DeckInventoryViewWithoutBack
import org.trackedout.citadel.inventory.DeckManagementView
import org.trackedout.citadel.inventory.MoveCardView
import org.trackedout.citadel.inventory.ShopView
import org.trackedout.citadel.inventory.SpectateSelectorView
import org.trackedout.citadel.listeners.EchoShardListener
import org.trackedout.citadel.listeners.FullHungerListener
import org.trackedout.citadel.listeners.PlayedDeathListener
import org.trackedout.citadel.listeners.PlayedJoinedListener
import org.trackedout.citadel.mongo.MongoDBManager
import org.trackedout.citadel.mongo.MongoDungeon
import org.trackedout.citadel.mongo.MongoPlayer
import org.trackedout.citadel.shop.ShopCommand
import org.trackedout.client.apis.CatalogApi
import org.trackedout.client.apis.ConfigApi
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.apis.LeaguesApi
import org.trackedout.client.apis.PartiesApi
import org.trackedout.client.apis.SettingsApi
import org.trackedout.client.apis.ScoreApi
import org.trackedout.client.apis.StatusApi
import org.trackedout.client.apis.TasksApi
import org.trackedout.client.apis.WorldsApi
import org.trackedout.client.infrastructure.ClientException
import org.trackedout.citadel.config.CompetitivePhases
import org.trackedout.citadel.config.ServerSettings
import org.trackedout.citadel.config.RunType
import org.trackedout.citadel.config.findRunTypeByIdFromCache
import org.trackedout.citadel.config.getRunTypesFromCache
import java.net.InetAddress
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class Citadel : JavaPlugin() {
    private val manager: PaperCommandManager by lazy { PaperCommandManager(this) }
    private lateinit var scoreboardLibrary: ScoreboardLibrary
    private lateinit var sidebar: Sidebar
    private lateinit var sidebar2: Sidebar
    val serverName by lazy { getEnvOrDefault("SERVER_NAME", InetAddress.getLocalHost().hostName) }
    val dungaAPIPath by lazy { getEnvOrDefault("DUNGA_API", "http://localhost:3000/v1") }
    val mongoURI by lazy { getEnvOrDefault("MONGODB_URL", "") }
    lateinit var leaderboardTaskRunner: LeaderboardTaskRunner
    lateinit var leagueCache: LeagueCache
    lateinit var catalogCache: CatalogCache
    lateinit var worldsApi: WorldsApi
        private set
    lateinit var runModes: RunModes
    lateinit var competitivePhases: CompetitivePhases
    lateinit var serverSettings: ServerSettings

    override fun onEnable() {
        saveDefaultConfig()
        reloadConfig()

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

        val configApi = ConfigApi(
            basePath = dungaAPIPath,
            client = OkHttpClient.Builder()
                .connectTimeout(5.seconds.toJavaDuration())
                .callTimeout(60.seconds.toJavaDuration())
                .build()
        )

        val partiesApi = PartiesApi(
            basePath = dungaAPIPath,
            client = OkHttpClient.Builder()
                .connectTimeout(5.seconds.toJavaDuration())
                .callTimeout(30.seconds.toJavaDuration())
                .build()
        )

        val leaguesApi = LeaguesApi(
            basePath = dungaAPIPath,
            client = OkHttpClient.Builder()
                .connectTimeout(5.seconds.toJavaDuration())
                .callTimeout(30.seconds.toJavaDuration())
                .build()
        )

        worldsApi = WorldsApi(
            basePath = dungaAPIPath,
            client = OkHttpClient.Builder()
                .connectTimeout(5.seconds.toJavaDuration())
                .callTimeout(30.seconds.toJavaDuration())
                .build()
        )

        leagueCache = LeagueCache(this, leaguesApi)
        catalogCache = CatalogCache(
            this,
            CatalogApi(
                basePath = dungaAPIPath,
                client = OkHttpClient.Builder()
                    .connectTimeout(5.seconds.toJavaDuration())
                    .callTimeout(30.seconds.toJavaDuration())
                    .build()
            ),
            leagueCache,
        )
        serverSettings = ServerSettings(
            this,
            SettingsApi(
                basePath = dungaAPIPath,
                client = OkHttpClient.Builder()
                    .connectTimeout(5.seconds.toJavaDuration())
                    .callTimeout(30.seconds.toJavaDuration())
                    .build()
            )
        )
        runModes = RunModes(leagueCache, serverSettings, configApi, inventoryApi, eventsApi, worldsApi, serverName, logger)
        competitivePhases = CompetitivePhases(leaguesApi, logger)

        val inventoryManager = InventoryManager(this, inventoryApi, scoreApi, eventsApi, configApi)

        MongoDBManager.initialize(mongoURI)

        registerCommandCompletions()

        // https://github.com/aikar/commands/wiki/Real-World-Examples
        manager.enableUnstableAPI("help")
        manager.registerCommand(InventoryCommand(inventoryApi, inventoryManager, scoreApi, configApi))
        manager.registerCommand(LogEventCommand(eventsApi))
        manager.registerCommand(StatusCommand(this))
        manager.registerCommand(ScoreManagementCommand(this, scoreApi, eventsApi, inventoryManager, inventoryApi))
        manager.registerCommand(ShutdownDungeonsCommand(this, eventsApi, tasksApi))
        manager.registerCommand(ScheduleJobCommand(this, tasksApi))
        val shopCommand = ShopCommand(this)
        manager.registerCommand(shopCommand)
        manager.commandCompletions.registerAsyncCompletion("shopTrades") { c ->
            c.player?.let { shopCommand.tradesForTargetedShop(it) } ?: emptyList()
        }
        manager.registerCommand(TestQueueCommand(eventsApi))

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

        sidebar = scoreboardLibrary.createSidebar()
        sidebar2 = scoreboardLibrary.createSidebar()
        val statusTaskRunner = StatusTaskRunner(this, statusApi, sidebar, sidebar2)
        statusTaskRunner.runTaskTimerAsynchronously(this, 20 * 5, 60) // Repeat every 60 ticks (3 seconds)

        leaderboardTaskRunner = LeaderboardTaskRunner(this)
        leaderboardTaskRunner.runTaskTimerAsynchronously(this, 20 * 5, 20 * 15) // Repeat every 300 ticks (15 seconds)

        val trophyTaskRunner = TrophyTaskRunner(this)
        trophyTaskRunner.runTaskTimerAsynchronously(this, 20 * 5, 20 * 60) // Repeat every 1200 ticks (1 minute)

        server.pluginManager.registerEvents(PlayedJoinedListener(this, eventsApi, configApi, inventoryManager), this)
        server.pluginManager.registerEvents(PlayedDeathListener(this, eventsApi, scoreApi, configApi), this)
        server.pluginManager.registerEvents(org.trackedout.citadel.listeners.LeaderboardSignListener(this), this)
        server.pluginManager.registerEvents(FullHungerListener(), this)

        val viewFrame: ViewFrame = ViewFrame.create(this)
            .with(
                AddACardView(),
                AddAnItemView(),
                BasicItemView(),
                CardActionView(),
                MoveCardView(),
                DeckInventoryView(),
                DeckInventoryViewWithoutBack(),
                DeckManagementView(),
                SpectateSelectorView(),
                ShopView(),
            )
            .register()
        manager.registerCommand(ManageDeckCommand(this, inventoryApi, eventsApi, viewFrame))
        manager.registerCommand(CopyDeckCommand(this, inventoryApi))
        manager.registerCommand(SpectateCommand(this, eventsApi, configApi, viewFrame))
        manager.registerCommand(ConfigCommand(this, configApi))
        manager.registerCommand(ShulkerStyleCommand(configApi, inventoryManager))
        manager.registerCommand(ShowArtifakesCommand(this, scoreApi, viewFrame))
        val cubbyTemplateStore = CubbyTemplateStore(dataFolder)
        val cubbyArchive = CubbyArchive(dataFolder)
        val cubbyReset = CubbyReset(cubbyTemplateStore, cubbyArchive)
        manager.registerCommand(CubbyManagementCommand(this, cubbyReset))
        manager.registerCommand(CubbyAdminCommand(this, cubbyTemplateStore, cubbyReset, cubbyArchive))
        manager.registerCommand(CubbyPruneCommand(this, cubbyReset))
        manager.registerCommand(LeaderboardCommand(this, configApi))
        manager.registerCommand(UnstuckCommand(this))
        manager.registerCommand(TrophyCommand(this))
        manager.registerCommand(BookCommand(this, configApi, scoreApi))
        val partyCommand = PartyCommand(this, partiesApi, inventoryManager)
        partyCommand.registerCompletions(manager)
        manager.registerCommand(partyCommand)
        val worldEditor = WorldEditor(this, worldsApi, leaguesApi)
        manager.registerCommand(LeagueCommand(this, leagueCache, leaguesApi, eventsApi, worldEditor))
        manager.registerCommand(WorldCommand(this, worldsApi, worldEditor))

        leagueCache.startRefreshTask()
        catalogCache.startRefreshTask()
        serverSettings.startRefreshTask()

        val echoShardListener = EchoShardListener(this, inventoryApi, eventsApi, scoreApi, configApi, viewFrame, inventoryManager)
        server.pluginManager.registerEvents(echoShardListener, this)

        logger.info("Citadel has been enabled. Server name: $serverName")
    }

    private fun registerCommandCompletions() {
        manager.commandCompletions.registerAsyncCompletion("runTypes") { _ ->
            getRunTypesFromCache(leagueCache).map { it.longId }
        }
        manager.commandCompletions.registerAsyncCompletion("leagueSlugs") { _ ->
            val leagues = leagueCache.getLeagues()
            leagues.mapNotNull { it.slug }
        }
        manager.commandCompletions.registerAsyncCompletion("worldSlugs") { _ ->
            worldsApi.worldsGet().results.orEmpty().map { it.slug }
        }
        manager.commandCompletions.registerAsyncCompletion("selectableRunModes") { context ->
            val playerName = context.player?.name ?: return@registerAsyncCompletion emptyList()
            runModes.selectableLeagues(playerName).mapNotNull { it.slug }
        }
        manager.commandCompletions.registerStaticCompletion("toggleableConfigs", toggleableConfigs)
        manager.commandCompletions.registerStaticCompletion("editableConfigs", editableConfigs)
        manager.commandCompletions.registerStaticCompletion("shulkerColors", shulkerColors)
        manager.commandCompletions.registerAsyncCompletion("trophySections") { _ ->
            loadTrophySections().keys.toList()
        }
        manager.commandCompletions.registerAsyncCompletion("trophyNames") { _ ->
            loadTrophySections().values.flatten()
        }

        // The league of the command comes after the card or item, so these suggest across the leagues
        manager.commandCompletions.registerAsyncCompletion("cards") { _ ->
            catalogCache.loadedCatalogs().flatMap { it.cards.keys }.distinct()
        }

        manager.commandCompletions.registerAsyncCompletion("items") { _ ->
            catalogCache.loadedCatalogs().flatMap { it.tradeItems.keys }.distinct()
        }

        manager.commandCompletions.registerAsyncCompletion("dbPlayers") { _ ->
            val database = MongoDBManager.getDatabase("dunga-dunga")
            val playerCollection = database.getCollection("players", MongoPlayer::class.java)

            val players = playerCollection.find().map { it.playerName }.toList()
            players
        }

        manager.commandCompletions.registerCompletion("cubbies") { context ->
            CubbyWorld(context.player.world).cubbies.map { it.id }
        }

        manager.commandCompletions.registerAsyncCompletion("dungeonNames") { _ ->
            val database = MongoDBManager.getDatabase("dunga-dunga")
            val collection = database.getCollection("instances", MongoDungeon::class.java)

            collection.find().map { it.name }.toList()
        }

        manager.commandContexts.registerContext(RunType::class.java) { context ->
            val arg = context.popFirstArg()
            val runType = findRunTypeByIdFromCache(leagueCache, arg) ?: throw InvalidCommandArgument("Invalid run type: $arg")
            runType
        }
    }

    override fun onDisable() {
        logger.info("Citadel has been disabled")
        Bukkit.getScheduler().cancelTasks(this)
        server.messenger.unregisterIncomingPluginChannel(this)
        scoreboardLibrary.close()
        sidebar.close()
        sidebar2.close()
        MongoDBManager.shutdown()
    }

    fun debug(message: String?) {
        if (this.config.getBoolean("debug")) {
            logger.info(message)
        }
    }
}

fun getEnvOrDefault(key: String, default: String): String {
    var value = System.getenv(key)
    if (value == null || value.isEmpty()) {
        value = default
    }
    return value
}

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

fun Citadel.runOnNextTick(unit: () -> Unit) {
    object : BukkitRunnable() {
        override fun run() {
            unit()
        }
    }.runTask(this)
}

// delay is the number of ticks to wait (20 per second)
fun Citadel.runLater(delay: Long, unit: () -> Unit) {
    object : BukkitRunnable() {
        override fun run() {
            unit()
        }
    }.runTaskLater(this, delay)
}

// delay is the number of ticks to wait (20 per second)
fun Citadel.runLaterOnATick(delay: Long, unit: () -> Unit) {
    object : BukkitRunnable() {
        override fun run() {
            runOnNextTick(unit)
        }
    }.runTaskLater(this, delay)
}

private fun getMessage(e: Exception): String? {
    return if (e is ClientException) e.userMessage() else e.message
}

data class DungaDungaException(
    @SerializedName("code") var code: Number? = null,
    @SerializedName("message") var message: String? = null,
    @SerializedName("stack") var stack: String? = null,
)
