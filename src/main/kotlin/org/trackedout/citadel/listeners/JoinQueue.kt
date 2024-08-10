package org.trackedout.citadel.listeners

import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.inventory.DeckId
import org.trackedout.citadel.inventory.fullRunType
import org.trackedout.citadel.inventory.id
import org.trackedout.citadel.inventory.shortRunType
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.models.Event
import java.util.function.Consumer

fun createJoinQueueFunc(citadel: Citadel, eventsApi: EventsApi, player: Player): Consumer<String> {
    val joinQueueFunc = Consumer<DeckId> { deckId ->
        citadel.async(player) {
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
                        "run-type" to deckId.shortRunType(),
                    )
                )
            )

            player.sendGreenMessage("Joining dungeon queue with ${deckId.fullRunType()} Deck #${deckId.id()}")
        }
    }

    return joinQueueFunc
}
