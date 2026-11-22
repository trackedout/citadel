package org.trackedout.citadel.inventory

import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.trackedout.citadel.config.LobbyCount
import org.trackedout.citadel.config.TradeItem
import org.trackedout.client.models.CatalogItemLobbyPotion
import org.trackedout.client.models.CatalogLobbyEntry

// NBT tags that mark an item as Citadel's. The slug is the league the item belongs to
const val SLUG_TAG = "slug"
const val TRADE_ID_TAG = "tradeId"
const val CAN_TAKE_INTO_DUNGEON_TAG = "canTakeIntoDungeon"
const val LOBBY_ENTRY_TAG = "lobbyEntry"

private const val FLAG_SET = "1"

// An item stack cannot hold 0 of an item, so "none of this item" is a stack of this many
// that the inventory sync treats as a target of 0
const val ABSENT_ITEM_AMOUNT = 999

// Trade items that a player can only hold one of, however often they earned it
private val SINGLE_COPY_TRADE_IDS = setOf("GAUNTLET_TROPHY")

// How many of the item a player holds: what they earned, less what they spent
fun TradeItem.heldCount(scoreOf: (scoreboard: String) -> Int?): Int {
    val source = sourceScoreboard ?: return 0
    val earned = scoreOf(source) ?: 0
    val spent = inversionScoreboard?.takeIf { it != source }?.let(scoreOf) ?: 0
    val held = earned - spent
    return if (tradeId in SINGLE_COPY_TRADE_IDS) held.coerceAtMost(1) else held
}

// Everything that makes an item what it is, without the server types, so it can be tested
data class ItemSpec(
    val material: String,
    val name: String,
    val colour: TextColor,
    val customModelData: Int?,
    val lore: List<LoreLine>,
    val potion: CatalogItemLobbyPotion?,
    val tags: Map<String, String>,
    val amount: Int,
)

data class LoreLine(val text: String, val colour: TextColor)

fun amountOf(count: Int): Int = if (count <= 0) ABSENT_ITEM_AMOUNT else count

fun colourOf(hex: String): TextColor = TextColor.fromHexString(hex) ?: throw IllegalArgumentException("Invalid colour: $hex")

fun TradeItem.tags(slug: String): Map<String, String> = buildMap {
    put(SLUG_TAG, slug)
    put(TRADE_ID_TAG, tradeId)
    if (takeIntoDungeon) put(CAN_TAKE_INTO_DUNGEON_TAG, FLAG_SET)
}

fun tradeItemSpec(slug: String, item: TradeItem, count: Int): ItemSpec = ItemSpec(
    material = item.lobby.material,
    name = item.lobby.name,
    colour = colourOf(item.lobby.colour),
    customModelData = item.lobby.customModelData,
    lore = dungeonLore(item),
    potion = item.lobby.potion,
    tags = item.tags(slug),
    amount = amountOf(count),
)

private fun dungeonLore(item: TradeItem): List<LoreLine> = when {
    !item.takeIntoDungeon -> emptyList()
    item.returnedOnWin -> listOf(LoreLine("Returned to deck on win", NamedTextColor.GREEN))
    else -> listOf(LoreLine("Consumed on dungeon entry", NamedTextColor.RED))
}

// A lobby entry as the player holds it. `item` is the entry's catalog item when it has one, `materialOverride`
// replaces the entry's material (a player's own choice of colour)
fun lobbyEntrySpec(
    slug: String,
    index: Int,
    entry: CatalogLobbyEntry,
    item: TradeItem?,
    count: Int,
    materialOverride: String? = null,
): ItemSpec = ItemSpec(
    material = materialOverride ?: entry.material,
    name = entry.name,
    colour = colourOf(entry.colour),
    customModelData = entry.customModelData,
    lore = entry.lore.orEmpty().map { LoreLine(it, NamedTextColor.GRAY) },
    potion = null,
    tags = (item?.tags(slug) ?: mapOf(SLUG_TAG to slug)) + (LOBBY_ENTRY_TAG to index.toString()),
    amount = amountOf(count),
)

// How many of a lobby entry the player holds, given their scores in the entry's league
fun lobbyEntryCount(entry: CatalogLobbyEntry, scoreOf: (scoreboard: String) -> Int): Int =
    when (val count = LobbyCount.of(entry.count)) {
        is LobbyCount.Fixed -> count.amount
        is LobbyCount.Scoreboard -> scoreOf(count.name)
    }
