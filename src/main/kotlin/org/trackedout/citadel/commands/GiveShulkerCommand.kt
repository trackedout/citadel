package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Dependency
import co.aikar.commands.annotation.Description
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
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

        plugin.logger.info("${player.name}'s inventory does not contain a Decked Out Shulker, pulling deck data from Dunga Dunga")

        if (player.inventory.addItem(createDeckedOutShulker(player)).size > 0) {
            plugin.logger.warning("Failed to give ${player.name} a Decked Out Shulker as their inventory is full")
        }
    }

    private fun createDeckedOutShulker(player: Player): ItemStack {
        val shulker = ItemStack(Material.SHULKER_BOX, 1)
        val itemMeta = shulker.itemMeta
        itemMeta.setCustomModelData(8)
        itemMeta.setDisplayName("☠ ${player.name}'s Deck ☠")
        itemMeta.persistentDataContainer.set(NamespacedKey(plugin, "owner"), PersistentDataType.STRING, player.name)
        itemMeta.persistentDataContainer.set(NamespacedKey(plugin, "owner-id"), PersistentDataType.STRING, player.identity().uuid().toString())
        shulker.setItemMeta(itemMeta)
        return shulker
    }
}