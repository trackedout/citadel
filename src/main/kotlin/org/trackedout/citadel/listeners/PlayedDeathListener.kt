package org.trackedout.citadel.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.models.Event
import org.trackedout.fs.logger

class PlayedDeathListener(
    private val plugin: Citadel,
    private val eventsApi: EventsApi,
) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.player

        logger.info("${player.name} (tags: ${player.scoreboardTags}) died at location: ${player.location} with message: ${event.deathMessage()}")
        if (!player.scoreboardTags.contains("void_death")) {
            return
        }

        logger.info("${player.name} died to the pit, resetting their hardcore deck")
        plugin.async(player) {
            eventsApi.eventsPost(
                Event(
                    player = player.name,
                    server = plugin.serverName,
                    name = "hardcore-deck-reset",
                    x = player.x,
                    y = player.y,
                    z = player.z,
                    metadata = mapOf(
                        "reason" to "void_death",
                        "run-type" to "h",
                    )
                )
            )
        }
    }
}
