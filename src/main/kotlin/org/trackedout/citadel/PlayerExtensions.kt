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
import org.trackedout.citadel.config.CatalogCache
import org.trackedout.citadel.inventory.CAN_TAKE_INTO_DUNGEON_TAG
import org.trackedout.citadel.inventory.LOBBY_ENTRY_TAG
import org.trackedout.citadel.inventory.LeagueSlug
import org.trackedout.citadel.inventory.SLUG_TAG
import org.trackedout.citadel.inventory.TRADE_ID_TAG
import org.trackedout.data.BrillianceCard
import org.trackedout.citadel.config.RunType
import org.trackedout.data.find

val opsLogsTag = "debug" // Show ops notification messages
val disableScoreboardTag = "noscoreboard" // Disable scoreboard display
val debugDetailsTag = "details" // Show detailed instance scoreboard

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

fun ItemStack.hasSlug(): Boolean = RtagItem(this).hasTag(SLUG_TAG)

// Items handed out before league slugs carry a positional `deckId` tag (p1, c1, h1)
// instead of `slug`. Citadel no longer creates them, so they are always out of date.
const val LEGACY_DECK_ID_TAG = "deckId"

fun ItemStack.hasLegacyDeckId(): Boolean = RtagItem(this).hasTag(LEGACY_DECK_ID_TAG)

// Items Citadel owns in a player's inventory: tagged with a league slug, or a
// pre-slug item it should replace
fun ItemStack.isManagedDungeonItem(): Boolean = hasSlug() || hasLegacyDeckId()

// Items are tagged with their league `slug` in NBT.
fun ItemStack.getSlug(): String? {
    return RtagItem(this).get<String>(SLUG_TAG)
}

fun ItemStack.withSlug(slug: LeagueSlug): ItemStack {
    return this.withTags(mapOf("slug" to slug))
}

fun ItemStack.getAction(): String? = RtagItem(this).get<String>("action")

// The position of the lobby entry of the item's league that this item was vended from
fun ItemStack.getLobbyEntryIndex(): Int? = RtagItem(this).get<String>(LOBBY_ENTRY_TAG)?.toInt()

fun ItemStack.withTags(tags: Map<String, String>): ItemStack {
    return RtagItem.edit(this) { tag ->
        tags.forEach { (key, value) ->
            tag.set(value, key)
        }
    }
}

fun ItemStack.preventRemoval(): Boolean = RtagItem(this).let { it.hasTag("prevent-removal") && it.get<String>("prevent-removal") == "1" }

fun ItemStack.isTradeItem(): Boolean = RtagItem(this).let { it.hasTag(TRADE_ID_TAG) && it.get<String>(TRADE_ID_TAG).isNotEmpty() }

fun ItemStack.getTradeId(): String? = RtagItem(this).get<String>(TRADE_ID_TAG)

fun ItemStack.getShorthand(): String? = RtagItem(this).get<String>("shorthand")

fun ItemStack.canTakeIntoDungeon(): Boolean = RtagItem(this).let { it.hasTag(CAN_TAKE_INTO_DUNGEON_TAG) && it.get<String>(CAN_TAKE_INTO_DUNGEON_TAG) == "1" }

fun ItemStack.isDeckedOutCard(catalogs: CatalogCache): Boolean = getCard(catalogs) != null

// The card this item is, by the catalog of the league the item belongs to
fun ItemStack.getCard(catalogs: CatalogCache): BrillianceCard? {
    val cards = getSlug()?.let { catalogs.get(it) }?.cards ?: return null
    return getShorthand()?.let { cards.find(it) } ?: name()?.let { cards.find(it) }
}

fun ItemStack.name(): String? {
    return (this.itemMeta?.displayName() as TextComponent?)?.content()
}

fun RunType.displayNamedText(): NamedTextColor {
    return NamedTextColor.namedColor(this.displayColour) ?: NamedTextColor.GRAY
}
