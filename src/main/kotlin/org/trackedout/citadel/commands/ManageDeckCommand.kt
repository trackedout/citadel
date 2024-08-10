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
import org.trackedout.citadel.inventory.DeckId
import org.trackedout.citadel.inventory.DeckManagementView
import org.trackedout.citadel.inventory.DeckManagementView.Companion.createContext
import org.trackedout.citadel.listeners.createJoinQueueFunc
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.models.Card
import java.util.function.BiConsumer

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

        val addCardFunc = BiConsumer<DeckId, Card> { deckId, card ->
            plugin.async(player) {
                inventoryApi.inventoryAddCardPost(
                    Card(
                        player = card.player,
                        name = card.name,
                        deckType = deckId[0].toString(),
                        server = plugin.serverName,
                    )
                )
            }
        }

        val deleteCardFunc = BiConsumer<DeckId, Card> { deckId, card ->
            plugin.async(player) {
                inventoryApi.inventoryDeleteCardPost(
                    Card(
                        player = card.player,
                        name = card.name,
                        deckType = deckId[0].toString(),
                        server = plugin.serverName,
                    )
                )
            }
        }

        val moveCardFunc = TriConsumer<DeckId, DeckId, Card> { sourceDeckId, targetDeckId, card ->
            plugin.async(player) {
                inventoryApi.inventoryDeleteCardPost(
                    Card(
                        player = card.player,
                        name = card.name,
                        deckType = sourceDeckId[0].toString(),
                        server = plugin.serverName,
                    )
                )

                inventoryApi.inventoryAddCardPost(
                    Card(
                        player = card.player,
                        name = card.name,
                        deckType = targetDeckId[0].toString(),
                        server = plugin.serverName,
                    )
                )
            }
        }

        val joinQueueFunc = createJoinQueueFunc(plugin, eventsApi, player)

        val context = createContext(plugin, player, allCards, addCardFunc, deleteCardFunc, moveCardFunc, joinQueueFunc)
        viewFrame.open(DeckManagementView::class.java, player, context)
    }
}
