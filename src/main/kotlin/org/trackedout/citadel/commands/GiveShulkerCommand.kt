package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
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


@CommandAlias("gief-shulker|give-shulker") // Spelling is intentional
class GiveShulkerCommand : BaseCommand() {
    @Dependency
    private lateinit var plugin: Citadel

    private fun isDeckedOutShulker(it: ItemStack) = it.type == Material.SHULKER_BOX

    @Default
    @Description("Add Decked Out 2 shulker into player's inventory")
    fun giveShulker(player: Player) {
        val deckedOutShulker = player.inventory.find { itemStack ->
            if (itemStack != null && isDeckedOutShulker(itemStack)) {
                return@find true
            } else {
                false
            }
        }

        if (deckedOutShulker != null) {
            plugin.logger.info("${player.name}'s inventory already contains a Decked Out shulker")
            player.sendMessage("You already have your Decked Out shulker")
            return
        }

        if (player.scoreboardTags.contains(TakeShulkerCommand.RECEIVED_SHULKER)) {
            plugin.logger.info("${player.name} has already received a Decked Out shulker (has tag ${TakeShulkerCommand.RECEIVED_SHULKER})")
            player.sendMessage("You have already been given your Decked Out shulker but it's not in your inventory. Hopefully you didn't drop it!?")
            return
        }

        plugin.logger.info("${player.name}'s inventory does not contain a Decked Out Shulker, pulling deck data from Dunga Dunga")

        if (player.inventory.addItem(createDeckedOutShulker(player)).size > 0) {
            plugin.logger.warning("Failed to give ${player.name} a Decked Out Shulker as their inventory is full")
            return
        }

        player.addScoreboardTag(TakeShulkerCommand.RECEIVED_SHULKER)
    }

    private fun createDeckedOutShulker(player: Player): ItemStack {
        var shulker = ItemStack(Material.SHULKER_BOX, 1)

        val blockStateMeta = shulker.itemMeta as BlockStateMeta
        val shulkerBoxState = blockStateMeta.blockState as ShulkerBox
        val nugget = ItemStack(Material.IRON_NUGGET, 1)
        val card = RtagItem.edit(nugget) { tag ->
            tag.setCustomModelData(106)

            tag.loadCopy()
        }

        shulkerBoxState.inventory.addItem(card)

        blockStateMeta.blockState = shulkerBoxState
        shulker.itemMeta = blockStateMeta

        shulker = RtagItem.edit(shulker) { tag ->
            tag.set("Custom Text", "display", "Name")
            tag.set(player.name, "owner")
            tag.set(player.identity().uuid().toString(), "owner-id")

            tag.loadCopy();
        }

        return shulker
    }
}