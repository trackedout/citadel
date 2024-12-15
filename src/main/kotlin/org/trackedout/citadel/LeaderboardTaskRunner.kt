package org.trackedout.citadel

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Sign
import org.bukkit.block.Skull
import org.bukkit.block.sign.Side
import org.bukkit.scheduler.BukkitRunnable
import org.trackedout.citadel.mongo.MongoDBManager
import org.trackedout.citadel.mongo.MongoPlayerStats
import kotlin.math.ceil
import kotlin.math.min

// WARNING: This task is processed *asynchronously*, and thus most interactions with the main Minecraft thread
// must go through a sync task scheduler. The Scoreboard lib is thread-safe, so it doesn't require a sync task.
class LeaderboardTaskRunner(
    private val plugin: Citadel,
) : BukkitRunnable() {
    override fun run() {
        if (this.isCancelled) {
            return
        }

        plugin.debug("[Async task ${this.taskId}] Fetching leaderboard from MongoDB")

        plugin.server.worlds.find { it.name == "world" }?.let { world ->
            val database = MongoDBManager.getDatabase("dunga-dunga")
            val playerStatsCollection = database.getCollection("playerStatsPhase1", MongoPlayerStats::class.java)

            val activePlayers = playerStatsCollection.find().toList()
                .sortedByDescending { it.stats.tomesSubmitted }
                .filter { it.stats.tomesSubmitted > 0 }

            val maxPerPage = 20
            val pages = ceil(activePlayers.size / maxPerPage.toDouble()).toInt()
            PageWatcher.page++
            if (PageWatcher.page >= pages) {
                PageWatcher.page = 0
            }

            val startIndex = maxPerPage * PageWatcher.page
            val upperIndex = min(startIndex + maxPerPage, activePlayers.size)
            plugin.logger.info("Active players: ${activePlayers.size}, showing $startIndex to $upperIndex (page ${PageWatcher.page + 1}/${pages})")

            activePlayers.slice(startIndex until upperIndex).forEachIndexed { index, player ->
                val playerName = player.player
                val offlinePlayer = plugin.server.getOfflinePlayer(playerName)

                val x = -534 + index
                val y = 114
                val z = 1973

                plugin.runOnNextTick {
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
                        signSide.line(3, Component.text("Points: ${player.stats.tomesSubmitted}").color(NamedTextColor.AQUA))
                        sign.update()
                    }
                }
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
                }
            }
        }
    }
}

object PageWatcher {
    var page: Int = 99
}
