package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Subcommand
import me.devnatan.inventoryframework.ViewFrame
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.inventory.DeckInventoryView
import org.trackedout.citadel.inventory.DeckManagementView.Companion.createContext
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.models.Card
import java.util.function.Consumer

@CommandAlias("decked-out|do")
class ManageDeckCommand(
    private val plugin: Citadel,
    private val inventoryApi: InventoryApi,
    private val viewFrame: ViewFrame,
) : BaseCommand() {
    @Subcommand("edit")
    @CommandPermission("decked-out.inventory.edit")
    @Description("Edit a player's deck")
    fun showDeckEditor(player: Player, args: Array<String>) {
        player.sendGreenMessage("Opening editor for your deck")

        // TODO: Make this async
        val playerCards = inventoryApi.inventoryCardsGet(player = player.name, limit = 200, deckId = "1").results!!

        val addCardFunc = Consumer<Card> {
            plugin.async(player) {
                inventoryApi.inventoryAddCardPost(it)
            }
        }

        val deleteCardFunc = Consumer<Card> {
            plugin.async(player) {
                inventoryApi.inventoryDeleteCardPost(
                    Card(
                        player = it.player,
                        name = it.name,
                        deckId = "1",
                    )
                )
            }
        }

        val context = createContext(plugin, player, playerCards, addCardFunc, deleteCardFunc)
        viewFrame.open(DeckInventoryView::class.java, player, context)
    }
}
