package org.trackedout.citadel.listeners

import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.config.allowDraftMetadata
import org.trackedout.citadel.inventory.LeagueSlug
import org.trackedout.citadel.inventory.displayName
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.models.Event
import java.util.function.Consumer

fun createJoinQueueFunc(citadel: Citadel, eventsApi: EventsApi, player: Player): Consumer<String> {
    val joinQueueFunc = Consumer<LeagueSlug> { deckId ->
        citadel.async(player) {
            // The player's client protocol is injected onto the GameProfile by the
            // EmberSeeker proxy at login. Forward it so the backend can reject
            // queuing when it doesn't match the league's expected MC version.
            val clientProtocol = player.playerProfile.properties
                .firstOrNull { it.name == "clientProtocol" }
                ?.value

            val metadata = buildMap {
                // deckId is the league slug (one deck per league). The backend derives
                // dungeon-type from the league; we only send the slug.
                put("deck-id", deckId)
                put("slug", deckId)
                if (clientProtocol != null) {
                    put("mc-protocol", clientProtocol)
                }
                putAll(player.allowDraftMetadata(citadel.leagueCache.getLeagueBySlug(deckId)))
            }

            eventsApi.eventsPost(
                Event(
                    name = "joined-queue",
                    server = citadel.serverName,
                    player = player.name,
                    count = 1,
                    x = player.x,
                    y = player.y,
                    z = player.z,
                    metadata = metadata,
                )
            )

            player.sendGreenMessage("Joining dungeon queue with ${deckId.displayName(citadel.leagueCache)} Deck")
        }
    }

    return joinQueueFunc
}
