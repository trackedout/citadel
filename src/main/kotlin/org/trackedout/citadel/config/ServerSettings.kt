package org.trackedout.citadel.config

import org.bukkit.scheduler.BukkitRunnable
import org.trackedout.citadel.Citadel
import org.trackedout.client.apis.SettingsApi
import org.trackedout.client.models.Settings
import org.trackedout.client.models.SettingsLeagues
import org.trackedout.client.models.SettingsLobby

// The network reads the settings document with this slug
const val SERVER_SETTINGS_SLUG = "server"

private const val REFRESH_TICKS = 20L * 60

// The network-wide settings authored in the leagues repo: the default run mode, the
// lobby's toggles and the leagues Citadel has dedicated behaviour for. Cached and
// refreshed every minute, null until the first fetch succeeds
class ServerSettings(
    private val plugin: Citadel,
    private val settingsApi: SettingsApi,
) {
    @Volatile
    private var settings: Settings? = null

    fun startRefreshTask() {
        object : BukkitRunnable() {
            override fun run() {
                refresh()
            }
        }.runTaskTimerAsynchronously(plugin, 0, REFRESH_TICKS)
    }

    fun refresh() {
        try {
            settings = settingsApi.settingsSlugGet(SERVER_SETTINGS_SLUG)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to refresh settings '$SERVER_SETTINGS_SLUG', sync them with slug sync: ${e.message}")
        }
    }

    fun current(): Settings? = settings

    fun leagues(): SettingsLeagues? = settings?.leagues

    fun showTots(): Boolean = settings?.lobby?.showTots ?: true

    fun showLeaderboard(): Boolean = settings?.lobby?.showLeaderboard ?: false

    // Changes the lobby settings on the cluster. The leagues repo is their source, so
    // the next slug sync reverts this unless the change is copied there. Calls the
    // backend, so use it off the main thread
    fun updateLobby(change: (SettingsLobby) -> SettingsLobby) {
        val latest = settingsApi.settingsSlugGet(SERVER_SETTINGS_SLUG)
        settings = settingsApi.settingsPost(latest.copy(lobby = change(latest.lobby)))
        plugin.logger.warning("Changed lobby settings on the cluster, copy the change to the leagues repo or the next slug sync reverts it")
    }
}
