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
        val pointsForPosition = arrayOf(10, 8, 6, 5, 4, 3, 2, 1)

        if (FirstRun.skip > 0) {
            plugin.logger.info("Skipping this task (skip=${FirstRun.skip})")
            FirstRun.skip--
            return
        }

        plugin.server.worlds.find { it.name == "world" }?.let { world ->
            val database = MongoDBManager.getDatabase("dunga-dunga")
            val playerStatsCollection = database.getCollection("playerStatsPhase1", MongoPlayerStats::class.java)

            val activePlayers = playerStatsCollection.find().toList().filter { it.stats.tomesSubmitted > 0 }

            val maxPerPage = 20
            val pages = ceil(activePlayers.size / maxPerPage.toDouble()).toInt()
            PageWatcher.page++
            if (PageWatcher.page >= pages) {
                PageWatcher.page = 0
            }

            val startIndex = maxPerPage * PageWatcher.page
            val upperIndex = min(startIndex + maxPerPage, activePlayers.size)
            plugin.logger.info("Active players: ${activePlayers.size}, showing $startIndex to $upperIndex (page ${PageWatcher.page + 1}/${pages})")

            val lifetimeEmbersMap = mutableMapOf<String, Int>()
            activePlayers.forEach { activePlayer ->
                val scores = scoreApi.scoresGet(player = activePlayer.player).results!!

                val relevantScores = scores.filter { activePlayers.map(MongoPlayerStats::player).contains(it.player) }
                    .filter { it.key == "competitive-do2.lifetime.escaped.embers" }

                lifetimeEmbersMap += relevantScores.associate { it.player!! to it.value!!.toInt() }
            }

            val sortedPlayers = activePlayers.sortedWith(
                compareByDescending<MongoPlayerStats> { it.stats.tomesSubmitted }
                    .thenByDescending { getAverageEmbersPerWin(lifetimeEmbersMap, it) }
            )

            sortedPlayers.slice(startIndex until upperIndex).forEachIndexed { index, player ->
                val playerName = player.player
                val offlinePlayer = plugin.server.getOfflinePlayer(playerName)
                val points = pointsForPosition.getOrElse(index) { 0 }

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
                        signSide.line(0, Component.text("# ${startIndex + index + 1}").color(NamedTextColor.AQUA))
                        signSide.line(1, Component.text(playerName).color(NamedTextColor.WHITE))
                        signSide.line(2, Component.text(""))
                        signSide.line(3, Component.text("Points: $points").color(NamedTextColor.AQUA))

//                        signSide.line(2, Component.text("Embers/win: ${getAverageEmbersPerWin(lifetimeEmbersMap, player)}"))
//                        signSide.line(3, Component.text("Tomes: ${player.stats.tomesSubmitted}").color(NamedTextColor.AQUA))
                        sign.update()
                    }

                    setSnowLayers(world, x, y, z, points)
                }

                if (FirstRun.isFirstRun) {
                    var delay = (((upperIndex + 1) - index) * 30).toLong()
                    if (index <= 2) {
                        delay += (30 * (3 - index))
                    }

                    plugin.runLater(delay, unit)
                    plugin.runLater(delay) {
                        plugin.server.onlinePlayers.forEach { onlinePlayer ->
                            onlinePlayer.playSound(Sound.sound(Key.key("do2:events.card_reveal"), Sound.Source.MASTER, 1f, 0f))
                        }
                    }

                    if (index == 0) {
                        plugin.runLater(delay + 60) {
                            plugin.server.onlinePlayers.forEach { onlinePlayer ->
                                onlinePlayer.showTitle(
                                    Title.title(
                                        Component.text(playerName).color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                                        Component.text("Phase 1 Winner!").color(NamedTextColor.AQUA)
                                    )
                                )
                                onlinePlayer.playSound(Sound.sound(Key.key("do2:events.artifact_retrived"), Sound.Source.MASTER, 1f, 0f))
                            }
                        }
                    }
                } else {
                    plugin.runOnNextTick(unit)
                }
            }

            if (upperIndex > 0) {
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

    private fun getAverageEmbersPerWin(lifetimeEmbersMap: Map<String, Int>, it: MongoPlayerStats): Int {
        return lifetimeEmbersMap.getOrDefault(it.player, 0) / max(1, it.stats.competitive.wins)
    }

    private fun setSnowLayers(world: World, x: Int, y: Int, z: Int, layers: Int) {
        // blockIndex = snowLayers / 8 (e.g. 0 for first 8 layers)
        for (blockIndex in 0 until 6) { // support 6 blocks worth (48 points)
            val snowBlock = world.getBlockAt(x, y + blockIndex, z - 1)
            val layersForBlock = min(8, layers - (blockIndex * 8))

            if (layersForBlock <= 0) {
                if (snowBlock.type != Material.AIR) {
                    snowBlock.type = Material.AIR
                }
            } else {
                if (snowBlock.type != Material.SNOW) {
                    snowBlock.type = Material.SNOW
                }

                val snow = snowBlock.blockData as Snow
                plugin.logger.info("Setting snow layers at $x, $y, $z to $layersForBlock at index $blockIndex")
                snow.layers = layersForBlock
                snowBlock.blockData = snow
            }
        }
    }
}

object PageWatcher {
    var page: Int = 99
}

object FirstRun {
    var isFirstRun = false
    var skip: Int = 0
}
