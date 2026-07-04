package org.trackedout.citadel.listeners

import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.inventory.DeckId
import org.trackedout.citadel.inventory.displayName
import org.trackedout.citadel.inventory.id
import org.trackedout.citadel.inventory.isCompetitive
import org.trackedout.citadel.inventory.isHardcore
import org.trackedout.citadel.inventory.isPractice
import org.trackedout.citadel.inventory.shortRunType
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.client.apis.ConfigApi
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.infrastructure.ClientException
import org.trackedout.client.models.Event
import java.util.function.Consumer

private fun resolveRunTypeLabel(deckId: DeckId): String = when {
    deckId.isPractice() -> "practice"
    deckId.isCompetitive() -> "competitive"
    deckId.isHardcore() -> "hardcore"
    else -> "unknown"
}

fun createJoinQueueFunc(citadel: Citadel, eventsApi: EventsApi, configApi: ConfigApi, player: Player): Consumer<String> {
    val joinQueueFunc = Consumer<DeckId> { deckId ->
        citadel.async(player) {
            val runTypeLabel = resolveRunTypeLabel(deckId)
            val dungeonType = try {
                configApi.configsGet(runTypeLabel, "default-dungeon-type").value ?: "default"
            } catch (e: ClientException) { "default" }

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
                        "dungeon-type" to dungeonType,
                    )
                )
            )

            player.sendGreenMessage("Joining dungeon queue with ${deckId.displayName()} Deck #${deckId.id()}")
        }
    }

    return joinQueueFunc
}
