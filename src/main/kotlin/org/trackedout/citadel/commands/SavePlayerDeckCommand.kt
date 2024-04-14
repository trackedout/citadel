package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Description
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.BlockStateMeta
import org.trackedout.citadel.getCard
import org.trackedout.citadel.isDeckedOutCard
import org.trackedout.citadel.isDeckedOutShulker
import org.trackedout.client.apis.InventoryApi
import org.trackedout.data.Cards

@CommandAlias("save-shulker")
class SavePlayerDeckCommand(
    private val inventoryAPI: InventoryApi,
) : BaseCommand() {

    @Default
    @CommandPermission("decked-out.inventory.save-player-deck")
    @Description("Saves the player's deck with the current cards in their shulker")
    fun savePlayerDeck(player: Player) {
        val shulkerItemStack = player.inventory.storageContents.find { item -> item?.isDeckedOutShulker() == true }
        if (shulkerItemStack == null) {
            return
        }
        if (shulkerItemStack.itemMeta !is BlockStateMeta) {
            return
        }
        val blockMeta = shulkerItemStack.itemMeta as BlockStateMeta
        if (blockMeta.blockState !is ShulkerBox) {
            return
        }
        val deck = blockMeta.blockState as ShulkerBox
        val cards = deck.inventory.storageContents
            .filterNotNull()
            .filter { item -> item.isDeckedOutCard() }
            .flatMap { item ->
                val cards = mutableListOf<Cards.Companion.Card>()
                val card = item.getCard()
                if (card !== null) {
                    repeat(item.amount) {
                        cards.add(card)
                    }
                }
                cards
            }
            .map { item -> item.name }

        inventoryAPI.inventoryOverwritePlayerDeckPut(player = player.name, deckId = "1", requestBody = cards)
    }
}
