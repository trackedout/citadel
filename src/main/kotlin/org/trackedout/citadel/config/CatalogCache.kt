package org.trackedout.citadel.config

import org.bukkit.scheduler.BukkitRunnable
import org.trackedout.citadel.Citadel
import org.trackedout.client.apis.CatalogApi
import java.util.concurrent.ConcurrentHashMap

private const val CHECK_INTERVAL_TICKS = 20L * 60
private const val START_DELAY_TICKS = 20L * 5 // The league cache loads first
private const val RELOAD_AFTER_MILLIS = 5L * 60 * 1000

class CatalogNotLoadedException(slug: String) :
    CatalogException("The $slug catalog has not loaded yet, try again shortly")

// The catalog document of each league, by league slug. Loaded off the main thread, a league that
// fails to reload keeps its last good catalog, one that never loaded has none and callers must cope
class CatalogCache(
    private val plugin: Citadel,
    private val catalogApi: CatalogApi,
    private val leagueCache: LeagueCache,
) {
    private class Loaded(val catalog: CatalogData, val loadedAtMillis: Long)

    private val loaded = ConcurrentHashMap<String, Loaded>()

    fun startRefreshTask() {
        object : BukkitRunnable() {
            override fun run() {
                refresh()
            }
        }.runTaskTimerAsynchronously(plugin, START_DELAY_TICKS, CHECK_INTERVAL_TICKS)
    }

    // Loads the catalogs that are missing or old
    fun refresh() {
        val now = System.currentTimeMillis()
        leagueCache.getLeagues().mapNotNull { it.slug }
            .filter { slug -> loaded[slug]?.let { now - it.loadedAtMillis >= RELOAD_AFTER_MILLIS } ?: true }
            .forEach { load(it, now) }
    }

    private fun load(slug: String, now: Long) {
        try {
            val catalog = CatalogData(catalogApi.catalogSlugGet(slug))
            check(catalog.slug == slug) { "Asked for catalog $slug but received ${catalog.slug}" }
            loaded[slug] = Loaded(catalog, now)
            plugin.logger.info("Loaded catalog $slug: ${catalog.cards.size} cards, ${catalog.tradeItems.size} items, ${catalog.lobby.size} lobby entries")
        } catch (e: Exception) {
            val outcome = if (loaded.containsKey(slug)) "keeping the previous one" else "it has no catalog"
            plugin.logger.severe("Failed to load catalog $slug, $outcome: ${e.message}")
        }
    }

    fun get(slug: String): CatalogData? = loaded[slug]?.catalog

    fun require(slug: String): CatalogData = get(slug) ?: throw CatalogNotLoadedException(slug)

    fun loadedCatalogs(): List<CatalogData> = loaded.values.map { it.catalog }

    // The scores that drive the lobby inventory, across every loaded league
    fun inventoryScoreboards(): Set<String> = loadedCatalogs().flatMap { it.inventoryScoreboards() }.toSet()
}
