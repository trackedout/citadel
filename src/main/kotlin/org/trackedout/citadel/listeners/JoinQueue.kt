package org.trackedout.citadel.listeners

import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.inventory.LeagueSlug
import org.trackedout.citadel.inventory.displayName
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.models.Event
import java.util.function.Consumer

fun createJoinQueueFunc(citadel: Citadel, eventsApi: EventsApi, player: Player): Consumer<String> {
    val joinQueueFunc = Consumer<LeagueSlug> { deckId ->
        citadel.async(player) {
            // deckId is the league slug (one deck per league). The backend derives
            // dungeon-type from the league; we only send the slug.
            eventsApi.eventsPost(
                Event(
                    name = "joined-queue",
                    server = citadel.serverName,
                    player = player.name,
                    count = 1,
                    x = player.x,
                    y = player.y,
                    z = player.z,
                    metadata = mapOf(
                        "deck-id" to deckId,
                        "slug" to deckId,
                    )
                )
            )

            player.sendGreenMessage("Joining dungeon queue with ${deckId.displayName(citadel.leagueCache)} Deck")
        }
    }

    return joinQueueFunc
}
