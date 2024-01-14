package org.trackedout.citadel

import com.saicone.rtag.RtagItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.commands.DECK_NAME
import org.trackedout.data.Cards

fun CommandSender.sendGreenMessage(message: String) {
    this.sendMessage(Component.text().color(NamedTextColor.GREEN).content(message).build())
}

fun CommandSender.sendRedMessage(message: String) {
    this.sendMessage(Component.text().color(NamedTextColor.RED).content(message).build())
}

fun CommandSender.sendGreyMessage(message: String) {
    this.sendMessage(Component.text().color(NamedTextColor.GRAY).content(message).build())
}

fun ItemStack.isDeckedOutShulker() =
    RtagItem(this).get<String>("display", "Name") == DECK_NAME && this.type == Material.CYAN_SHULKER_BOX

fun ItemStack.isDeckedOutCard(): Boolean {
    val text = this.itemMeta.displayName() as TextComponent
    val name = text.content()
    return Cards.findCard(name) !== null
}

fun ItemStack.getCard(): Cards.Companion.Card? {
    val text = this.itemMeta.displayName() as TextComponent
    val name = text.content()
    return Cards.findCard(name)
}