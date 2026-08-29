package org.trackedout.citadel.config

import org.bukkit.scheduler.BukkitRunnable
import org.trackedout.citadel.Citadel
import org.trackedout.client.apis.LeaguesApi
import org.trackedout.client.models.League
import java.util.concurrent.CopyOnWriteArrayList

class LeagueCache(
    private val plugin: Citadel,
    private val leaguesApi: LeaguesApi,
) {
    private val leagues = CopyOnWriteArrayList<League>()

    companion object {
        lateinit var instance: LeagueCache
            private set
    }

    init {
        instance = this
    }

    fun startRefreshTask() {
        object : BukkitRunnable() {
            override fun run() {
                refresh()
            }
        }.runTaskTimerAsynchronously(plugin, 0, 20 * 60 * 5) // Refresh every 5 minutes
    }

    fun refresh() {
        try {
            val response = leaguesApi.leaguesGet()
            leagues.clear()
            response.results?.let { leagues.addAll(it) }
            plugin.logger.info("Refreshed league cache: ${leagues.mapNotNull { it.slug }}")
        } catch (e: Exception) {
            plugin.logger.warning("Failed to refresh league cache: ${e.message}")
        }
    }

    fun getLeagues(): List<League> = leagues.toList()

    fun getLeagueBySlug(slug: String): League? =
        leagues.find { it.slug == slug }
}
