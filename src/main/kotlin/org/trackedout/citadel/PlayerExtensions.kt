package org.trackedout.citadel

import com.saicone.rtag.RtagItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.HumanEntity
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.config.cardConfig
import org.trackedout.citadel.inventory.DeckId
import org.trackedout.citadel.inventory.intoDungeonItems
import org.trackedout.data.BrillianceCard
import org.trackedout.data.RunType
import org.trackedout.data.find
import org.trackedout.data.runTypes

val debugTag = "debug"

val playerNameRegex = Regex("^[a-zA-Z0-9_]{2,16}$")

fun CommandSender.sendMiniMessage(message: String) {
    val parsed = MiniMessage.miniMessage().deserialize(message)
    this.sendMessage(parsed)
}

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

fun ItemStack.isDeckedOutShulker(): Boolean {
    for (runType in runTypes) {
        if (this.type == Material.valueOf(runType.deckMaterial) && getDeckId() != null) {
            return true
        }
    }

    return false
}

fun ItemStack.hasDeckId(): Boolean = RtagItem(this).hasTag("deckId")

fun ItemStack.getDeckId(): String? = RtagItem(this).get<String>("deckId")

fun ItemStack.withDeckId(deckId: DeckId): ItemStack {
    return this.withTags(mapOf("deckId" to deckId))
}

fun ItemStack.getAction(): String? = RtagItem(this).get<String>("action")

fun ItemStack.withTags(tags: Map<String, String>): ItemStack {
    return RtagItem.edit(this) { tag ->
        tags.forEach { (key, value) ->
            tag.set(value, key)
        }
    }
}

fun ItemStack.preventRemoval(): Boolean = RtagItem(this).let { it.hasTag("prevent-removal") && it.get<String>("prevent-removal") == "1" }

fun ItemStack.isTradeItem(): Boolean = RtagItem(this).let { it.hasTag("tradeId") && it.get<String>("tradeId").isNotEmpty() }

fun ItemStack.getTradeId(): String? = RtagItem(this).get<String>("tradeId")

fun ItemStack.getShorthand(): String? = RtagItem(this).get<String>("shorthand")

fun ItemStack.canTakeIntoDungeon(): Boolean = RtagItem(this).let { it.hasTag("canTakeIntoDungeon") && it.get<String>("canTakeIntoDungeon") == "1" }

fun ItemStack.isDeckedOutCard(): Boolean {
    this.getShorthand()?.let {
        return cardConfig.find(it) !== null || intoDungeonItems.keys.contains(it)
    }

    if (this.itemMeta?.displayName() is TextComponent?) {
        val text = this.itemMeta?.displayName() as TextComponent?
        val name = text?.content()
        return cardConfig.find(name ?: "") !== null || intoDungeonItems.keys.contains(name ?: "")
    }

    return false
}

fun ItemStack.getCard(): BrillianceCard? {
    this.getShorthand()?.let {
        cardConfig.find(it)?.let { card ->
            return card
        }
    }

    if (this.itemMeta?.displayName() is TextComponent?) {
        val text = this.itemMeta?.displayName() as TextComponent?
        return text?.content()?.let { cardConfig.find(it) }
    }

    return null
}

fun ItemStack.name(): String? {
    return (this.itemMeta?.displayName() as TextComponent?)?.content()
}

fun RunType.displayNamedText(): NamedTextColor {
    return NamedTextColor.namedColor(this.displayColour) ?: NamedTextColor.GRAY
}
