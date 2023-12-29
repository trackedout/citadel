package org.trackedout.citadel

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

fun Player.sendGreenMessage(message: String) {
    this.sendMessage(Component.text().color(NamedTextColor.GREEN).content(message).build())
}

fun Player.sendRedMessage(message: String) {
    this.sendMessage(Component.text().color(NamedTextColor.RED).content(message).build())
}

fun Player.sendGreyMessage(message: String) {
    this.sendMessage(Component.text().color(NamedTextColor.GRAY).content(message).build())
}

fun ItemStack.isDeckedOutShulker() = this.type == Material.CYAN_SHULKER_BOX // TODO: Validate using NBT data; validate that this player owns it
