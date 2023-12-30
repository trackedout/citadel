package org.trackedout.citadel

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.inventory.ItemStack

fun CommandSender.sendGreenMessage(message: String) {
    this.sendMessage(Component.text().color(NamedTextColor.GREEN).content(message).build())
}

fun CommandSender.sendRedMessage(message: String) {
    this.sendMessage(Component.text().color(NamedTextColor.RED).content(message).build())
}

fun CommandSender.sendGreyMessage(message: String) {
    this.sendMessage(Component.text().color(NamedTextColor.GRAY).content(message).build())
}

fun ItemStack.isDeckedOutShulker() = this.type == Material.CYAN_SHULKER_BOX // TODO: Validate using NBT data; validate that this player owns it
