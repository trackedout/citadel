package org.trackedout.citadel.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.FILTER_MODE_SLUG_CONFIG
import org.trackedout.citadel.async
import org.trackedout.client.apis.ConfigApi
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.ScoreApi
import org.trackedout.client.models.Config
import org.trackedout.client.models.Event
import org.trackedout.citadel.config.getRunTypeByIdFromCache

class PlayedDeathListener(
    private val plugin: Citadel,
    private val eventsApi: EventsApi,
    private val scoreApi: ScoreApi,
    private val configApi: ConfigApi,
) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.player
        val playerName = player.name

        plugin.logger.info("$playerName (tags: ${player.scoreboardTags}) died at location: ${player.location} with message: ${event.deathMessage()}")
        if (!player.scoreboardTags.contains("void_death")) {
            return
        }

        plugin.logger.info("$playerName died to the pit, resetting their hardcore deck")
        val hardcoreSlug = getRunTypeByIdFromCache(plugin.leagueCache, "do2-s2-hardcore").deckType()
        plugin.async(player) {
            eventsApi.eventsPost(
                Event(
                    player = playerName,
                    server = plugin.serverName,
                    name = "hardcore-deck-reset",
                    x = player.x,
                    y = player.y,
                    z = player.z,
                    metadata = mapOf(
                        "reason" to "void_death",
                        "slug" to hardcoreSlug,
                    )
                )
            )

            // Reset the player's run mode to hardcore via the filter-mode-slug config.
            configApi.configsAddConfigPost(
                Config(
                    entity = playerName,
                    key = FILTER_MODE_SLUG_CONFIG,
                    value = "do2-s2-hardcore",
                )
            )
        }
    }
}
