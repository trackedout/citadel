package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Subcommand
import me.devnatan.inventoryframework.ViewFrame
import org.apache.logging.log4j.util.TriConsumer
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.inventory.DeckManagementView
import org.trackedout.citadel.inventory.DeckManagementView.Companion.createContext
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.models.Card
import org.trackedout.client.models.Event
import java.util.function.BiConsumer
import java.util.function.Consumer

@CommandAlias("decked-out|do")
class ManageDeckCommand(
    private val plugin: Citadel,
    private val inventoryApi: InventoryApi,
    private val eventsApi: EventsApi,
    private val viewFrame: ViewFrame,
) : BaseCommand() {
    @Subcommand("edit")
    @CommandPermission("decked-out.inventory.edit")
    @Description("Edit a player's deck")
    fun showDeckEditor(player: Player, args: Array<String>) {
        player.sendGreenMessage("Opening editor for your deck")

        // TODO: Make this async
        val allCards = inventoryApi.inventoryCardsGet(player = player.name, limit = 200).results!!

        val addCardFunc = BiConsumer<String, Card> { deckId, card ->
            plugin.async(player) {
                inventoryApi.inventoryAddCardPost(
                    Card(
                        player = card.player,
                        name = card.name,
                        deckId = deckId,
                        server = plugin.serverName,
                    )
                )
            }
        }

        val deleteCardFunc = BiConsumer<String, Card> { deckId, card ->
            plugin.async(player) {
                inventoryApi.inventoryDeleteCardPost(
                    Card(
                        player = card.player,
                        name = card.name,
                        deckId = deckId,
                        server = plugin.serverName,
                    )
                )
            }
        }

        val moveCardFunc = TriConsumer<String, String, Card> { sourceDeckId, targetDeckId, card ->
            plugin.async(player) {
                inventoryApi.inventoryDeleteCardPost(
                    Card(
                        player = card.player,
                        name = card.name,
                        deckId = sourceDeckId,
                        server = plugin.serverName,
                    )
                )

                inventoryApi.inventoryAddCardPost(
                    Card(
                        player = card.player,
                        name = card.name,
                        deckId = targetDeckId,
                        server = plugin.serverName,
                    )
                )
            }
        }

        val joinQueueFunc = Consumer<String> { deckId ->
            plugin.async(player) {
                eventsApi.eventsPost(
                    Event(
                        name = "joined-queue",
                        server = plugin.serverName,
                        player = player.name,
                        count = deckId.toInt(),
                        x = player.x,
                        y = player.y,
                        z = player.z,
                    )
                )

                player.sendGreenMessage("Joining dungeon queue with Deck #${deckId}")
            }
        }

        val context = createContext(plugin, player, allCards, addCardFunc, deleteCardFunc, moveCardFunc, joinQueueFunc)
        viewFrame.open(DeckManagementView::class.java, player, context)
    }
}
