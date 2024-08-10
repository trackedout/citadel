package org.trackedout.citadel

import com.saicone.rtag.RtagItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.HumanEntity
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.inventory.DeckId
import org.trackedout.data.Cards

val debugTag = "debug"

fun CommandSender.sendGreenMessage(message: String) {
    this.sendMessage(Component.text().color(NamedTextColor.GREEN).content(message).build())
}

fun CommandSender.sendRedMessage(message: String) {
    this.sendMessage(Component.text().color(NamedTextColor.RED).content(message).build())
}

fun CommandSender.sendGreyMessage(message: String) {
    this.sendMessage(Component.text().color(NamedTextColor.GRAY).content(message).build())
}

fun CommandSender.sendMessage(message: String, namedTextColor: NamedTextColor) {
    this.sendMessage(Component.text().color(namedTextColor).content(message).build())
}

fun HumanEntity.debug(message: String, tag: String = "debug.click") {
    if (this.scoreboardTags.contains(tag)) {
        this.sendGreyMessage(message)
    }
}

fun ItemStack.isDeckedOutShulker() = this.type == Material.CYAN_SHULKER_BOX && getDeckId() != null

fun ItemStack.getDeckId(): String? = RtagItem(this).get<String>("deckId")

fun ItemStack.withDeckId(deckId: DeckId): ItemStack {
    return this.withTags(mapOf("deckId" to deckId))
}

fun ItemStack.withTags(tags: Map<String, String>): ItemStack {
    return RtagItem.edit(this) { tag ->
        tags.forEach { (key, value) ->
            tag.set(value, key)
        }
    }
}

fun ItemStack.preventRemoval(): Boolean = RtagItem(this).get<String>("prevent-removal") == "1"

fun ItemStack.isDeckedOutCard(): Boolean {
    val text = this.itemMeta?.displayName() as TextComponent?
    val name = text?.content()
    return Cards.findCard(name ?: "") !== null
}

fun ItemStack.getCard(): Cards.Companion.Card? {
    val text = this.itemMeta.displayName() as TextComponent
    val name = text.content()
    return Cards.findCard(name)
}

fun ItemStack.name(): String? {
    return (this.itemMeta?.displayName() as TextComponent?)?.content()
}
