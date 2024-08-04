package org.trackedout.citadel.inventory

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

fun dungeonShard(
    name: String,
    textColor: NamedTextColor = NamedTextColor.AQUA,
    itemCount: Int = 1,
) = dungeonItem(name, 7, textColor, itemCount)

fun dungeonCrown(
    crownType: String,
    textColor: NamedTextColor = NamedTextColor.WHITE,
    itemCount: Int = 1,
) = dungeonItem("❄☠ Decked Out Crown (${crownType}) ☠❄", 2, textColor, itemCount)

fun dungeonItem(name: String, customModelData: Int, textColor: NamedTextColor = NamedTextColor.AQUA, itemCount: Int = 1): ItemStack {
    val itemStack = ItemStack(Material.IRON_NUGGET, itemCount)
    val meta = itemStack.itemMeta
    meta.displayName(Component.text(name, textColor))
    meta.setCustomModelData(customModelData)

    itemStack.itemMeta = meta

    return itemStack
}
