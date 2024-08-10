package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Dependency
import co.aikar.commands.annotation.Description
import com.saicone.rtag.RtagItem
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.isDeckedOutShulker
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendGreyMessage
import org.trackedout.citadel.sendRedMessage
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.models.Card
import org.trackedout.data.Cards

const val DECK_NAME = "{\"text\":\"❄☠ Frozen Assets ☠❄\"}"

@CommandAlias("gief-shulker|give-shulker") // Spelling is intentional
class GiveShulkerCommand(
    private val eventsApi: EventsApi,
    private val inventoryApi: InventoryApi,
) : BaseCommand() {
    @Dependency
    private lateinit var plugin: Citadel

    @Default
    @CommandPermission("decked-out.inventory.get-shulker")
    @Description("Add Decked Out 2 shulker into player's inventory")
    fun giveShulker(player: Player) {
        if (!playerMayReceiveShulker(player)) {
            return
        }

        plugin.logger.info("${player.name}'s inventory does not contain a Decked Out Shulker, pulling deck data from Dunga Dunga")
        player.sendGreyMessage("Fetching your Decked Out shulker from Dunga Dunga...")

        plugin.async(player) {
            val playerCards = inventoryApi.inventoryCardsGet(player = player.name, limit = 200, deckId = "1").results!!

            // Validate deck state a second time as the API call could take 10 seconds and the player could have run this command again
            if (!playerMayReceiveShulker(player)) {
                return@async
            }

            if (player.inventory.addItem(createDeckedOutShulker(player, playerCards)).size > 0) {
                plugin.logger.warning("Failed to give ${player.name} a Decked Out Shulker as their inventory is full")
                player.sendRedMessage("Failed to give you your Decked Out Shulker as your inventory is full")
                return@async
            }

            player.addScoreboardTag(TakeShulkerCommand.RECEIVED_SHULKER)
            player.sendGreenMessage("Your Decked Out shulker has been placed in your inventory")
        }
    }

    private fun playerMayReceiveShulker(player: Player): Boolean {
        val deckedOutShulker = player.inventory.find { itemStack ->
            if (itemStack != null && itemStack.isDeckedOutShulker()) {
                return@find true
            } else {
                false
            }
        }

        if (deckedOutShulker != null) {
            plugin.logger.info("${player.name}'s inventory already contains a Decked Out shulker")
            player.sendRedMessage("You already have your Decked Out shulker")
            return false
        }

        if (player.scoreboardTags.contains(TakeShulkerCommand.RECEIVED_SHULKER)) {
            plugin.logger.info("${player.name} has already received a Decked Out shulker (has tag ${TakeShulkerCommand.RECEIVED_SHULKER})")
            player.sendRedMessage("You have already been given your Decked Out shulker but it's not in your inventory. Hopefully you didn't drop it!?")
            return false
        }

        return true
    }

    private fun createDeckedOutShulker(player: Player, playerCards: List<Card>): ItemStack {
        var shulker = ItemStack(Material.CYAN_SHULKER_BOX, 1)

        val blockStateMeta = shulker.itemMeta as BlockStateMeta
        val shulkerBoxState = blockStateMeta.blockState as ShulkerBox

        val cardCount = playerCards.groupingBy { it.name!! }.eachCount()
        cardCount.forEach { (cardName, count) ->
            plugin.logger.info("${player.name}'s shulker should contain ${count}x $cardName")

            createCard(plugin, player, cardName, count)?.let {
                shulkerBoxState.inventory.addItem(it)
            }
        }

        blockStateMeta.blockState = shulkerBoxState
        shulker.itemMeta = blockStateMeta

        shulker = RtagItem.edit(shulker, fun(tag: RtagItem): ItemStack {
            tag.set(DECK_NAME, "display", "Name")
            tag.set(player.name, "owner")
            tag.set(player.identity().uuid().toString(), "owner-id")
            return tag.loadCopy();
        })

        return shulker
    }

    companion object {
        fun createCard(plugin: Citadel?, player: Player?, cardName: String, count: Int, deckId: String? = null): ItemStack? {
            try {
                val nugget = ItemStack(Material.IRON_NUGGET, count)
                val card = Cards.findCard(cardName)?.let {
                    return@let RtagItem.edit(nugget, fun(tag: RtagItem): ItemStack {
                        tag.setCustomModelData(it.modelData)
                        val nameJson = "{\"color\":\"${it.colour}\",\"text\":\"${it.displayName}\"}"
                        tag.set(nameJson, "display", "Name")
                        tag.set("{\"color\":\"${it.colour}\",\"OriginalName\":\"${nameJson}\"}", "display", "NameFormat");
                        deckId?.let { deckIdValue -> tag.set(deckIdValue, "deckId") }

                        return tag.loadCopy()
                    })
                }

                if (card == null) {
                    player?.sendRedMessage("Failed to add $cardName to your shulker as it's metadata is missing")
                    plugin?.logger?.warning("Failed to add $cardName to your shulker as card data for this card was not found")
                }

                return card
            } catch (e: Exception) {
                player?.sendRedMessage("Failed to add $cardName to your shulker, error: ${e.message}")
                plugin?.logger?.warning("Failed to add $cardName to your shulker, error: ${e.message}")
                e.printStackTrace()
                return null
            }
        }
    }
}
