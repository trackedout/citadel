package org.trackedout.citadel.listeners

import com.destroystokyo.paper.event.server.ServerTickStartEvent
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.models.Event

class PlayedJoinedListener(
    private val plugin: Citadel,
    private val eventsApi: EventsApi,
) : Listener {
    @EventHandler(ignoreCancelled = true)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (insideDungeonEntrance(player)) {
            plugin.logger.info("${player.name} is within dungeon entrance at ${player.location}, teleporting them out")
            player.teleport(Location(player.world, -512.0, 114.0, 1980.0, 90f, 0f))
        }
        plugin.logger.info("${player.name} joined at location: ${player.location}")

        plugin.async(player) {
            eventsApi.eventsPost(
                Event(
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

    private fun insideDungeonEntrance(player: Player): Boolean {
        return -553 <= player.x && player.x <= -542
            && 1977 <= player.z && player.z <= 1983
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerSeen(event: ServerTickStartEvent) {
        if (event.tickNumber % 60 == 0) { // Every 3 seconds
            plugin.server.onlinePlayers.forEach { player ->
                plugin.async(player) {
                    eventsApi.eventsPost(
                        Event(
                            player = player.name,
                            server = plugin.serverName,
                            name = "player-seen",
                            x = player.x,
                            y = player.y,
                            z = player.z,
                            count = 1,
                        )
                    )
                }
            }
        }
    }
}
