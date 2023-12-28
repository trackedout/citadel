package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Dependency
import co.aikar.commands.annotation.Description
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendGreyMessage
import org.trackedout.citadel.sendRedMessage

@CommandAlias("take-shulker")
class TakeShulkerCommand : BaseCommand() {
    @Dependency
    private lateinit var plugin: Citadel

    private fun isDeckedOutShulker(it: ItemStack) = it.type == Material.SHULKER_BOX // TODO: Validate using NBT data; validate that this player owns it

    @Default
    @Description("Take Decked Out 2 shulker from player's inventory")
    fun takeShulker(player: Player) {
        val deckedOutShulker = player.inventory.storageContents.find { itemStack ->
            if (itemStack != null && isDeckedOutShulker(itemStack)) {
                return@find true
            } else {
                false
            }
        }

        if (deckedOutShulker != null) {
            plugin.logger.info("Removing Decked Out shulker from ${player.name}'s inventory (contains=${player.inventory.contains(deckedOutShulker)})")
            player.sendGreyMessage("Removing Decked Out shulker from your inventory")
            player.inventory.removeItemAnySlot(deckedOutShulker)
            player.removeScoreboardTag(RECEIVED_SHULKER)
            player.sendGreenMessage("Your Decked Out shulker has been removed your inventory (it's stored in Dunga Dunga)")
        } else {
            plugin.logger.info("${player.name}'s inventory does not contain a Decked Out Shulker")
            player.sendRedMessage("Your inventory does not contain a Decked Out Shulker")
        }
    }

    companion object {
        const val RECEIVED_SHULKER = "do2.received_shulker"
    }
}