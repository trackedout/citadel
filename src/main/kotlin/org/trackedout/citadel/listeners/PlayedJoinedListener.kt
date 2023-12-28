package org.trackedout.citadel.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.models.EventsPostRequest

class PlayedJoinedListener(
    private val plugin: Citadel,
    private val eventsApi: EventsApi
) : Listener {
    @EventHandler(ignoreCancelled = true)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        plugin.async {
            eventsApi.eventsPost(
                EventsPostRequest(
                    player = player.name,
                    server = plugin.serverName,
                    name = "joined-network",
                    x = player.x,
                    y = player.y,
                    z = player.z,
                )
            )

            plugin.logger.info("${player.name} joined the server. Dunga Dunga has been notified")
        }
    }
}
