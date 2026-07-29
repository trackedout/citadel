package org.trackedout.citadel.commands.party

import org.bukkit.scheduler.BukkitRunnable
import org.trackedout.citadel.Citadel
import org.trackedout.client.apis.GameRegistryApi
import org.trackedout.client.models.GameRegistryEntry
import java.util.concurrent.ConcurrentHashMap

class GameRegistryCache(
    private val plugin: Citadel,
    private val gameRegistryApi: GameRegistryApi,
) {
    private val games = ConcurrentHashMap<String, GameRegistryEntry>()

    fun startRefreshTask() {
        object : BukkitRunnable() {
            override fun run() {
                refresh()
            }
        }.runTaskTimerAsynchronously(plugin, 0, 20 * 60 * 5) // Refresh every 5 minutes
    }

    fun refresh() {
        try {
            val response = gameRegistryApi.gameRegistryGet()
            games.clear()
            response.results?.forEach { game ->
                game.name?.let { games[it] = game }
            }
            plugin.logger.info("Refreshed game registry: ${games.keys}")
        } catch (e: Exception) {
            plugin.logger.warning("Failed to refresh game registry: ${e.message}")
        }
    }

    fun getGameNames(): List<String> = games.keys.toList()

    fun getGame(name: String): GameRegistryEntry? = games[name]
}
