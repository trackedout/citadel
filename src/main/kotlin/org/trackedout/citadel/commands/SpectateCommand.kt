package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Subcommand
import com.google.common.io.ByteStreams
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Filters.eq
import me.devnatan.inventoryframework.ViewFrame
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.inventory.SpectateSelectorView
import org.trackedout.citadel.mongo.MongoClaim
import org.trackedout.citadel.mongo.MongoDBManager
import org.trackedout.citadel.mongo.MongoEvent
import org.trackedout.citadel.mongo.MongoPlayer
import org.trackedout.citadel.sendRedMessage
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.models.Event
import java.util.function.BiConsumer

@CommandAlias("decked-out|do")
class SpectateCommand(
    private val plugin: Citadel,
    private val eventsApi: EventsApi,
    private val viewFrame: ViewFrame,
) : BaseCommand() {

    @Subcommand("spectate")
    @CommandPermission("decked-out.inventory.admin")
    @Description("Spectate a player's game")
    fun spectate(source: CommandSender, args: Array<String>) {
        plugin.async(source) {
            val games = mutableListOf<Game>()

            val database = MongoDBManager.getDatabase("dunga-dunga")
            val playerCollection = database.getCollection("players", MongoPlayer::class.java)
            val claimCollection = database.getCollection("claims", MongoClaim::class.java)
            val eventCollection = database.getCollection("events", MongoEvent::class.java)

            val playersInDungeon = playerCollection.find(
                Filters.and(
                    eq("state", "in-dungeon"),
                )
            )

            playersInDungeon.forEach { mongoPlayer: MongoPlayer ->
                plugin.logger.info("MongoDB player: $mongoPlayer")

                val claims = claimCollection.find(
                    Filters.and(
                        eq("player", mongoPlayer.playerName),
                        eq("type", "dungeon"),
                        eq("state", "in-use"),
                    )
                ).toList()

                plugin.logger.info("Found ${claims.size} claims for ${mongoPlayer.playerName}: $claims")
                claims.firstOrNull()?.let { claim ->
                    val runType = claim.metadata["run-type"]
                    val deckId = claim.metadata["deck-id"]
                    val server = claim.claimant
                    val runId = claim.id()

                    if (runId == null || runType == null || deckId == null || server == null) {
                        plugin.logger.warning("Something is null for this claim: { runId=$runId, runType=$runType, deckId=$deckId, claimant=$server }")
                        return@forEach
                    }

                    val gameStartedEvents = eventCollection.find(
                        Filters.and(
                            eq("player", mongoPlayer.playerName),
                            eq("name", "game-started"),
                            eq("metadata.run-id", runId),
                        )
                    ).toList()

                    gameStartedEvents.firstOrNull()?.let {
                        games += Game(mongoPlayer.playerName, server, runId, runType, deckId)
                    } ?: plugin.logger.warning("Did not find game-started event for $claim")
                } ?: plugin.logger.warning("Did not find claim for ${mongoPlayer.playerName}")
            }

            plugin.logger.info("Spectate-able games: $games")

            if (games.isEmpty()) {
                source.sendRedMessage("There are no active games for you to spectate. Players must start their game before you can start spectating")
                return@async
            }

            if (source is Player) {
                val spectatePlayerFunc = BiConsumer<Player, Game> { player, game ->
                    plugin.async(source) {
                        val out = ByteStreams.newDataOutput()
                        listOf("Connect", game.server).forEach(out::writeUTF)
                        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray())

                        eventsApi.eventsPost(
                            Event(
                                player = player.name,
                                server = plugin.serverName,
                                name = "spectating-game",
                                x = source.x,
                                y = source.y,
                                z = source.z,
                                count = 1,
                                metadata = mapOf("run-id" to game.runId, "server" to game.server)
                            )
                        )
                    }
                }

                runOnNextTick {
                    val context = SpectateSelectorView.createContext(plugin, source, games, spectatePlayerFunc)
                    viewFrame.open(SpectateSelectorView::class.java, source, context)
                }
            }
        }
    }

    private fun runOnNextTick(unit: () -> Unit) {
        object : BukkitRunnable() {
            override fun run() {
                unit()
            }
        }.runTask(plugin)
    }
}

data class Game(
    val playerName: String,
    val server: String,
    val runId: String,
    val shortRunType: String,
    val deckId: String,
)
