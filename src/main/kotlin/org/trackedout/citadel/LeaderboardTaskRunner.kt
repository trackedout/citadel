package org.trackedout.citadel

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Sign
import org.bukkit.block.Skull
import org.bukkit.block.data.type.Snow
import org.bukkit.block.data.type.WallSign
import org.bukkit.block.sign.Side
import org.bukkit.scheduler.BukkitRunnable
import org.trackedout.citadel.mongo.MongoDBManager
import org.trackedout.citadel.mongo.MongoPlayerStats
import org.trackedout.citadel.mongo.Stats
import org.trackedout.client.apis.ConfigApi
import kotlin.math.max
import kotlin.math.min

// WARNING: This task is processed *asynchronously*, and thus most interactions with the main Minecraft thread
// must go through a sync task scheduler. The Scoreboard lib is thread-safe, so it doesn't require a sync task.
class LeaderboardTaskRunner(
    private val plugin: Citadel,
    private val configApi: ConfigApi,
) : BukkitRunnable() {

    data class LeaderboardSlot(val signBlock: Block, val headBlock: Block, val snowBlock: Block)
    data class PodiumRegion(val rank: Int, val signBlock: Block?, val npcBlock: Block?)

    override fun run() {
        if (this.isCancelled) return

        plugin.logger.info("[Async task ${this.taskId}] Fetching leaderboard from MongoDB")

        if (!FirstRun.showLeaderboard) return

        val world = plugin.server.worlds.find { it.name == "world" } ?: return

        val activePlayers = mutableMapOf<String, PlayerWithPoints>()
        var maxPhase = 0
        val pointsForPosition = arrayOf(25, 21, 18, 16, 14, 12, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1)

        configApi.getInt("comp-season", "current-phase")?.let { currentPhase ->
            maxPhase = parsePhaseDataForAllPlayers(currentPhase, activePlayers, pointsForPosition)
        }

        // Deterministic sort: by total points descending, then by player name ascending
        val sortedPlayers = activePlayers.values.sortedWith(
            compareByDescending<PlayerWithPoints> { it.totalPoints }.thenBy { it.player }
        )

        val totalPointsRank = sortedPlayers.map { it.totalPoints }.distinct().sortedDescending()

        plugin.logger.info("[Leaderboard] ${sortedPlayers.size} players sorted")

        plugin.runOnNextTick {
            // Discover leaderboard slots from WorldGuard regions (must be on main thread)
            val slots = discoverLeaderboardSlots(world)
            plugin.logger.info("[Leaderboard] Found ${slots.size} leaderboard slots")

            // Discover podium regions
            val podiums = discoverPodiums(world)
            plugin.logger.info("[Leaderboard] Found ${podiums.size} podium regions")

            // Update leaderboard slots
            sortedPlayers.take(slots.size).forEachIndexed { index, player ->
                val slot = slots[index]
                val rank = totalPointsRank.indexOf(player.totalPoints) + 1
                val snowLayers = player.totalPoints / 4

                updateSlot(slot, player, rank, maxPhase, snowLayers)
            }

            // Clear unused slots
            for (i in sortedPlayers.size until slots.size) {
                clearSlot(slots[i])
            }

            // Update podiums for top 3
            podiums.forEach { podium ->
                val player = sortedPlayers.getOrNull(podium.rank - 1)
                updatePodium(podium, player, totalPointsRank, maxPhase)
            }
        }
    }

    private fun discoverLeaderboardSlots(world: World): List<LeaderboardSlot> {
        val regions = world.regions()?.filter { it.id.startsWith("leaderboard-") } ?: return emptyList()
        val slots = mutableListOf<LeaderboardSlot>()

        for (region in regions) {
            val min = region.minimumPoint
            val max = region.maximumPoint

            for (x in min.x..max.x) {
                for (y in min.y..max.y) {
                    for (z in min.z..max.z) {
                        val block = world.getBlockAt(x, y, z)
                        if (block.type != Material.WARPED_WALL_SIGN) continue

                        val signData = block.blockData as? WallSign ?: continue
                        val attachedBlock = block.getRelative(signData.facing.oppositeFace)
                        val headBlock = attachedBlock.getRelative(BlockFace.UP)

                        // Only valid if there's a player head in the expected location
                        if (headBlock.type != Material.PLAYER_HEAD && headBlock.type != Material.PLAYER_WALL_HEAD) continue

                        val snowBlock = headBlock.getRelative(signData.facing.oppositeFace)
                        slots.add(LeaderboardSlot(block, headBlock, snowBlock))
                    }
                }
            }
        }

        // Sort slots deterministically by position (x, then y, then z)
        return slots.sortedWith(compareBy({ it.signBlock.x }, { it.signBlock.y }, { it.signBlock.z }))
    }

    private fun discoverPodiums(world: World): List<PodiumRegion> {
        val podiums = mutableListOf<PodiumRegion>()

        for (rank in 1..3) {
            val region = world.regions()?.find { it.id == "podium-$rank" } ?: continue
            val min = region.minimumPoint
            val max = region.maximumPoint

            var signBlock: Block? = null
            var npcBlock: Block? = null

            for (x in min.x..max.x) {
                for (y in min.y..max.y) {
                    for (z in min.z..max.z) {
                        val block = world.getBlockAt(x, y, z)
                        when {
                            block.type == Material.WARPED_WALL_SIGN && signBlock == null -> signBlock = block
                            block.type == Material.RED_WOOL && npcBlock == null -> npcBlock = block
                        }
                    }
                }
            }

            podiums.add(PodiumRegion(rank, signBlock, npcBlock))
        }

        return podiums
    }

    private fun updateSlot(slot: LeaderboardSlot, player: PlayerWithPoints, rank: Int, maxPhase: Int, snowLayers: Int) {
        val playerName = player.player
        val offlinePlayer = plugin.server.getOfflinePlayerIfCached(playerName) ?: plugin.server.getOfflinePlayer(playerName)

        // Update head
        val signData = slot.signBlock.blockData as WallSign
        val desiredRotation = signData.facing.oppositeFace
        val skull = slot.headBlock.state as? Skull
        if (skull != null && (skull.owningPlayer?.uniqueId != offlinePlayer.uniqueId || skull.rotation != desiredRotation)) {
            skull.setOwningPlayer(offlinePlayer)
            skull.rotation = desiredRotation
            skull.update(false)
        }

        // Update sign
        val sign = slot.signBlock.state as Sign
        val signSide = sign.getSide(Side.FRONT)
        signSide.line(0, Component.text("#$rank (${player.totalPoints} points)").color(NamedTextColor.AQUA))
        signSide.line(1, Component.text(playerName).color(NamedTextColor.WHITE))
        signSide.line(2, Component.text(""))
        signSide.line(3, Component.text("Phase${maxPhase} tomes: ${player.stats.getOrDefault(maxPhase, null)?.tomesSubmitted ?: 0}").color(NamedTextColor.AQUA))
        sign.update()

        // Update snow layers behind head
        setSnowLayers(slot.snowBlock, snowLayers)
    }

    private fun clearSlot(slot: LeaderboardSlot) {
        // Clear sign text
        val sign = slot.signBlock.state as? Sign ?: return
        val signSide = sign.getSide(Side.FRONT)
        signSide.line(0, Component.text(""))
        signSide.line(1, Component.text(""))
        signSide.line(2, Component.text(""))
        signSide.line(3, Component.text(""))
        sign.update()

        // Clear head
        if (slot.headBlock.type == Material.PLAYER_HEAD || slot.headBlock.type == Material.PLAYER_WALL_HEAD) {
            slot.headBlock.type = Material.AIR
        }

        // Clear snow
        if (slot.snowBlock.type == Material.SNOW) {
            slot.snowBlock.type = Material.AIR
        }
    }

    private fun updatePodium(podium: PodiumRegion, player: PlayerWithPoints?, totalPointsRank: List<Int>, maxPhase: Int) {
        // Update sign if present
        podium.signBlock?.let { signBlock ->
            val sign = signBlock.state as? Sign ?: return@let
            val signSide = sign.getSide(Side.FRONT)
            if (player != null) {
                val rank = totalPointsRank.indexOf(player.totalPoints) + 1
                signSide.line(0, Component.text("#$rank").color(NamedTextColor.GOLD))
                signSide.line(1, Component.text(player.player).color(NamedTextColor.WHITE))
                signSide.line(2, Component.text("${player.totalPoints} points").color(NamedTextColor.AQUA))
                signSide.line(3, Component.text("Phase${maxPhase} tomes: ${player.stats.getOrDefault(maxPhase, null)?.tomesSubmitted ?: 0}").color(NamedTextColor.AQUA))
            } else {
                signSide.line(0, Component.text(""))
                signSide.line(1, Component.text(""))
                signSide.line(2, Component.text(""))
                signSide.line(3, Component.text(""))
            }
            sign.update()
        }

        // Spawn/update NPC at RED_WOOL location
        podium.npcBlock?.let { npcBlock ->
            val npcName = "leaderboard-podium-${podium.rank}"
            val loc = npcBlock.location.add(0.5, 1.0, 0.5) // Stand on top of the wool block

            if (player != null) {
                // Remove existing NPC if present, then create new one
                plugin.server.dispatchCommand(plugin.server.consoleSender, "npc select $npcName")
                plugin.server.dispatchCommand(plugin.server.consoleSender, "npc remove")
                plugin.server.dispatchCommand(plugin.server.consoleSender, "npc create $npcName")
                plugin.server.dispatchCommand(plugin.server.consoleSender, "npc select $npcName")
                plugin.server.dispatchCommand(plugin.server.consoleSender, "npc tp ${loc.x} ${loc.y} ${loc.z} ${loc.world.name}")
                plugin.server.dispatchCommand(plugin.server.consoleSender, "npc skin ${player.player} -C")
            } else {
                plugin.server.dispatchCommand(plugin.server.consoleSender, "npc select $npcName")
                plugin.server.dispatchCommand(plugin.server.consoleSender, "npc remove")
            }
        }
    }

    private fun setSnowLayers(startBlock: Block, layers: Int) {
        for (blockIndex in 0 until 6) {
            val snowBlock = startBlock.getRelative(BlockFace.UP, blockIndex)
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
                snow.layers = layersForBlock
                snowBlock.blockData = snow
            }
        }
    }

    private fun parsePhaseDataForAllPlayers(
        currentPhase: Int,
        activePlayers: MutableMap<String, PlayerWithPoints>,
        pointsForPosition: Array<Int>,
    ): Int {
        val database = MongoDBManager.getDatabase("dunga-dunga")
        var maxPhase = 0
        (1..currentPhase).forEach { phase ->
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
}

object FirstRun {
    var showLeaderboard = true
    var isFirstRun = false
    var skip: Int = 0
}
