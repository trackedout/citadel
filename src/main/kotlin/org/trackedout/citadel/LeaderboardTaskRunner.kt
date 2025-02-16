package org.trackedout.citadel

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Sign
import org.bukkit.block.Skull
import org.bukkit.block.data.type.Snow
import org.bukkit.block.sign.Side
import org.bukkit.scheduler.BukkitRunnable
import org.trackedout.citadel.mongo.MongoDBManager
import org.trackedout.citadel.mongo.MongoPlayerStats
import org.trackedout.citadel.mongo.Stats
import org.trackedout.client.apis.ScoreApi
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// WARNING: This task is processed *asynchronously*, and thus most interactions with the main Minecraft thread
// must go through a sync task scheduler. The Scoreboard lib is thread-safe, so it doesn't require a sync task.
class LeaderboardTaskRunner(
    private val plugin: Citadel,
    private val scoreApi: ScoreApi,
) : BukkitRunnable() {
    override fun run() {
        if (this.isCancelled) {
            return
        }

        plugin.logger.info("[Async task ${this.taskId}] Fetching leaderboard from MongoDB (skip=${FirstRun.skip})")
        val pointsForPosition = arrayOf(25, 21, 18, 16, 14, 12, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1)

        if (FirstRun.skip > 0) {
            plugin.logger.info("Skipping this task (skip=${FirstRun.skip})")
            FirstRun.skip--
            return
        }

        plugin.server.worlds.find { it.name == "world" }?.let { world ->
            val activePlayers = mutableMapOf<String, PlayerWithPoints>()
            var maxPhase = 0
            if (FirstRun.showLeaderboard) {
                maxPhase = parsePhaseDataForAllPlayers(activePlayers, pointsForPosition)
            }

            // Calculate overall rank based on total points, allowing for multiple players to have the same rank
            val totalPointsRank = activePlayers.values.map { it.totalPoints }.distinct().sortedWith(
                compareByDescending { it }
            )

            val maxPerPage = 20
            val pages = ceil(activePlayers.size / maxPerPage.toDouble()).toInt()
            PageWatcher.page++
            if (PageWatcher.page >= pages) {
                PageWatcher.page = 0
            }

            val startIndex = maxPerPage * PageWatcher.page
            val upperIndex = min(startIndex + maxPerPage, activePlayers.size)
            plugin.logger.info("[Leaderboard] Active players: ${activePlayers.size}, showing $startIndex to $upperIndex (page ${PageWatcher.page + 1}/${pages})")
            val minRank = totalPointsRank.indexOf(activePlayers.values.minOfOrNull { it.totalPoints } ?: -1)

            val soundScheduled = mutableMapOf<Int, Boolean>()

            activePlayers.values.toList().slice(startIndex until upperIndex).forEachIndexed { index, player ->
                val playerName = player.player
                val offlinePlayer = plugin.server.getOfflinePlayer(playerName)
                val points = player.totalPoints
                val rank = totalPointsRank.indexOf(points) + 1
                val snowLayers = player.totalPoints / 4 // Divide by 4 to make the snow layers look nicer

                val x = -534 + index
                val y = 114
                val z = 1973

                val unit = {
                    val block: Block = world.getBlockAt(x, y, z)
                    val signBlock: Block = world.getBlockAt(x, y - 1, z + 1)

                    if (block.type !== Material.PLAYER_HEAD) {
                        block.type = Material.PLAYER_HEAD
                    }

                    offlinePlayer.let { offlinePlayer ->
                        val skull: Skull = block.state as Skull
                        if (skull.owningPlayer?.uniqueId != offlinePlayer.uniqueId || skull.rotation != BlockFace.NORTH) {
                            skull.setOwningPlayer(offlinePlayer)
                            skull.rotation = BlockFace.NORTH
                            skull.update(false)
                        }
                    }

                    if (signBlock.type == Material.WARPED_WALL_SIGN) {
                        val sign = signBlock.state as Sign
                        val signSide = sign.getSide(Side.FRONT)
//                        signSide.line(0, Component.text("# $rank").color(NamedTextColor.AQUA))
                        signSide.line(0, Component.text("#$rank ($points points)").color(NamedTextColor.AQUA))
                        signSide.line(1, Component.text(playerName).color(NamedTextColor.WHITE))
                        signSide.line(3, Component.text("Phase${maxPhase} tomes: ${player.stats.getOrDefault(maxPhase, null)?.tomesSubmitted ?: 0}").color(NamedTextColor.AQUA))
//                        signSide.line(3, Component.text("Points: $points").color(NamedTextColor.AQUA))
                        signSide.line(2, Component.text("").color(NamedTextColor.AQUA))

//                        signSide.line(2, Component.text("Embers/win: ${getAverageEmbersPerWin(lifetimeEmbersMap, player)}"))
//                        signSide.line(3, Component.text("Tomes: ${player.stats.tomesSubmitted}").color(NamedTextColor.AQUA))
                        sign.update()
                    }
                }

                if (FirstRun.isFirstRun && FirstRun.showLeaderboard) {
                    // If the lowest rank is still rank #5, then we don't want to wait a bunch before showing that rank
                    val upperDelay = totalPointsRank.size + 1 - (totalPointsRank.size - minRank)
                    val delayMs = 60
                    var delay = ((upperDelay - rank) * delayMs).toLong()
                    if (rank <= 3) {
                        delay += (delayMs * (4 - rank))
                    }

                    plugin.runLaterOnATick(delay) {
                        // Set snow layers, then at the end set the player head and sign
                        setSnowLayers(world, x, y, z, snowLayers, true)
                    }

                    delay += points + 5 // Delay sounds until the end of the snow layers
                    plugin.runLaterOnATick(delay, unit)

                    // Only play the sound once per rank
                    if (!soundScheduled.getOrDefault(rank, false)) {
                        soundScheduled[rank] = true
                        plugin.runLaterOnATick(delay) {
                            plugin.server.onlinePlayers.forEach { onlinePlayer ->
                                onlinePlayer.playSound(Sound.sound(Key.key("do2:events.card_reveal"), Sound.Source.MASTER, 1f, 0f))
                            }
                        }

                        if (rank == 1) {
                            plugin.runLaterOnATick(delay + 60) {
                                plugin.server.onlinePlayers.forEach { onlinePlayer ->
                                    onlinePlayer.showTitle(
                                        Title.title(
                                            Component.text(playerName).color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                                            Component.text("Phase $maxPhase Winner!").color(NamedTextColor.AQUA)
                                        )
                                    )
                                    onlinePlayer.playSound(Sound.sound(Key.key("do2:events.artifact_retrived"), Sound.Source.MASTER, 1f, 0f))
                                }
                            }
                        }
                    }
                } else {
                    plugin.runOnNextTick {
                        // Set snow layers, then at the end set the player head and sign
                        setSnowLayers(world, x, y, z, snowLayers, false)
                        unit()
                    }
                }
            }

            if (upperIndex > 0 && FirstRun.isFirstRun) {
                FirstRun.isFirstRun = false
                FirstRun.skip = 5
            }

            // Clear remaining slots
            for (i in upperIndex until startIndex + maxPerPage) {
                plugin.runOnNextTick {
                    val x = -534 + i - startIndex
                    val y = 114
                    val z = 1973

                    val block: Block = world.getBlockAt(x, y, z)
                    if (block.type != Material.AIR) {
                        block.type = Material.AIR
                    }

                    val signBlock: Block = world.getBlockAt(x, y - 1, z + 1)
                    if (signBlock.type == Material.WARPED_WALL_SIGN) {
                        val sign = signBlock.state as Sign
                        val signSide = sign.getSide(Side.FRONT)
                        signSide.line(0, Component.text(""))
                        signSide.line(1, Component.text(""))
                        signSide.line(2, Component.text(""))
                        signSide.line(3, Component.text(""))
                        sign.update()
                    }

                    setSnowLayers(world, x, y, z, 0)
                }
            }
        }
    }

    private fun parsePhaseDataForAllPlayers(
        activePlayers: MutableMap<String, PlayerWithPoints>,
        pointsForPosition: Array<Int>,
    ): Int {
        val database = MongoDBManager.getDatabase("dunga-dunga");
        var maxPhase = 0
        listOf(1, 2, 3, 4, 5).forEach { phase ->
            val playerStatsCollection = database.getCollection("playerStatsPhase${phase}", MongoPlayerStats::class.java)

            val activePlayersInPhase = playerStatsCollection.find().toList().filter { it.stats.tomesSubmitted > 0 }.sortedBy { it.player }
            val tomesSubmitted = activePlayersInPhase.map { it.stats.tomesSubmitted }.distinct().sortedWith(
                compareByDescending { it }
            )
            plugin.logger.fine("[Leaderboard] Tomes submitted for Phase${phase}: $tomesSubmitted")

            if (activePlayersInPhase.isNotEmpty()) {
                maxPhase = max(maxPhase, phase)
            }

            activePlayersInPhase.forEach { player ->
                val playerWithPoints = activePlayers.getOrDefault(player.player, PlayerWithPoints(player.player, mapOf()))

                activePlayers[player.player] = playerWithPoints.copy(
                    stats = playerWithPoints.stats + mapOf(phase to player.stats),
                    totalPoints = playerWithPoints.totalPoints + pointsForPosition.getOrElse(tomesSubmitted.indexOf(player.stats.tomesSubmitted)) { 0 }
                )
            }
        }
        return maxPhase
    }

    data class PlayerWithPoints(
        val player: String,
        val stats: Map<Int, Stats>,
        val totalPoints: Int = 0,
    )

    // This runs on a tick, so we don't need to schedule a tick task for each block
    private fun setSnowLayers(world: World, x: Int, y: Int, z: Int, layers: Int, shouldAnimate: Boolean = false) {
        // blockIndex = snowLayers / 8 (e.g. 0 for first 8 layers)
        for (blockIndex in 0 until 6) { // support 6 blocks worth (48 points)
            val snowBlock = world.getBlockAt(x, y + blockIndex, z - 1)
            val layersForBlock = min(8, layers - (blockIndex * 8))

            if (layersForBlock <= 0) {
                if (snowBlock.type != Material.AIR) {
                    snowBlock.type = Material.AIR
                }
            } else {
//                plugin.logger.info("Setting snow layers at $x, $y, $z to $layersForBlock at index $blockIndex")

                if (shouldAnimate) {
                    for (i in 1..layersForBlock) {
                        plugin.runLaterOnATick((blockIndex * 8) + i.toLong()) {
                            setSnowLayersOfBlock(snowBlock, i)
                        }
                    }
                } else {
                    setSnowLayersOfBlock(snowBlock, layersForBlock)
                }
            }
        }
    }

    private fun setSnowLayersOfBlock(snowBlock: Block, layersForBlock: Int) {
        if (snowBlock.type != Material.SNOW) {
            snowBlock.type = Material.SNOW
        }

        val snow = snowBlock.blockData as Snow
        snow.layers = layersForBlock
        snowBlock.blockData = snow
    }
}

object PageWatcher {
    var page: Int = 99
}

object FirstRun {
    var showLeaderboard = true
    var isFirstRun = false
    var skip: Int = 0
}
