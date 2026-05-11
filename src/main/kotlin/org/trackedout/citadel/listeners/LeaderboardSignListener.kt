package org.trackedout.citadel.listeners

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.block.Sign
import org.bukkit.block.Skull
import org.bukkit.block.sign.Side
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.trackedout.citadel.Citadel

class LeaderboardSignListener(private val plugin: Citadel) : Listener {

    @EventHandler
    fun onSignClick(event: PlayerInteractEvent) {
        val block = event.clickedBlock ?: return

        // Get player name from sign or head
        val playerName = when (val state = block.state) {
            is Sign -> {
                val line = state.getSide(Side.FRONT).line(1)
                (line as? net.kyori.adventure.text.TextComponent)?.content()?.trim()
            }
            is Skull -> state.owningPlayer?.name
            else -> return
        }
        if (playerName.isNullOrBlank()) return

        // Check if block or nearby blocks are in a leaderboard/podium region
        val regions = com.sk89q.worldguard.WorldGuard.getInstance().platform.regionContainer
            .get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(block.world)) ?: return
        val checkPositions = listOf(
            com.sk89q.worldedit.math.BlockVector3.at(block.x, block.y, block.z),
            com.sk89q.worldedit.math.BlockVector3.at(block.x, block.y - 1, block.z),
            com.sk89q.worldedit.math.BlockVector3.at(block.x + 1, block.y - 1, block.z),
            com.sk89q.worldedit.math.BlockVector3.at(block.x - 1, block.y - 1, block.z),
            com.sk89q.worldedit.math.BlockVector3.at(block.x, block.y - 1, block.z + 1),
            com.sk89q.worldedit.math.BlockVector3.at(block.x, block.y - 1, block.z - 1),
        )
        val inLeaderboard = checkPositions.any { pos ->
            regions.getApplicableRegions(pos).any { it.id.startsWith("leaderboard-") || it.id.startsWith("podium-") }
        }
        if (!inLeaderboard) return

        val data = plugin.leaderboardTaskRunner.playerData[playerName] ?: return
        val maxPhase = plugin.leaderboardTaskRunner.currentMaxPhase
        val player = event.player

        player.sendMessage(Component.text(""))
        val rank = plugin.leaderboardTaskRunner.playerData.values
            .map { it.totalPoints }.distinct().sortedDescending()
            .indexOf(data.totalPoints) + 1
        player.sendMessage(Component.text("--- #$rank $playerName - ${data.totalPoints} pts ---", NamedTextColor.GOLD))
        for (phase in 1..maxPhase) {
            val tomes = data.stats[phase]?.tomesSubmitted ?: 0
            val pts = data.pointsPerPhase[phase] ?: 0
            player.sendMessage(Component.text("  Phase $phase: $tomes tomes ($pts pts)", NamedTextColor.AQUA))
        }
        player.sendMessage(Component.text(""))
    }
}
