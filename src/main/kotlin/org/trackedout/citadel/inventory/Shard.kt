package org.trackedout.citadel.inventory

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

fun dungeonShard(name: String, textColor: NamedTextColor = NamedTextColor.AQUA, itemCount: Int = 1): ItemStack {
    val itemStack = ItemStack(Material.IRON_NUGGET, itemCount)
    val meta = itemStack.itemMeta
    meta.displayName(Component.text(name, textColor))
    meta.setCustomModelData(7)

    itemStack.itemMeta = meta

    return itemStack
}
