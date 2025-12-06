package org.trackedout.citadel.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.INVENTORY_FILTER_MODE_SCOREBOARD
import org.trackedout.citadel.async
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.ScoreApi
import org.trackedout.client.models.Event
import org.trackedout.client.models.Score
import org.trackedout.data.getRunTypeById

class PlayedDeathListener(
    private val plugin: Citadel,
    private val eventsApi: EventsApi,
    private val scoreApi: ScoreApi,
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
                        "run-type" to "h",
                    )
                )
            )

            scoreApi.scoresPost(
                listOf(
                    Score(
                        player = playerName,
                        key = INVENTORY_FILTER_MODE_SCOREBOARD,
                        value = getRunTypeById("hardcore").runTypeId.toBigDecimal(),
                    )
                )
            )
        }
    }
}
