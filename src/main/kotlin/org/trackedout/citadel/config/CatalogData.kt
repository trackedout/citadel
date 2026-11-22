package org.trackedout.citadel.config

import org.trackedout.client.models.Catalog
import org.trackedout.client.models.CatalogItemLobby
import org.trackedout.client.models.CatalogItemTracking
import org.trackedout.client.models.CatalogLobbyAction
import org.trackedout.client.models.CatalogLobbyEntry
import org.trackedout.data.BrillianceCard
import org.trackedout.data.LeagueCatalog

// An item the lobby can hand out, trade and put in decks: a catalog item with a `lobby` facet
data class TradeItem(
    val catalogKey: String,
    val lobby: CatalogItemLobby,
    val tracking: CatalogItemTracking?,
) {
    val tradeId: String get() = lobby.tradeId
    val takeIntoDungeon: Boolean get() = lobby.takeIntoDungeon == true
    val returnedOnWin: Boolean get() = lobby.returnedOnWin == true

    // The score that earns the item, or the only score of items without a spent counterpart
    val sourceScoreboard: String? get() = tracking?.earned ?: tracking?.scoreboard

    // The score that is drawn down when the item is spent
    val inversionScoreboard: String? get() = tracking?.spent ?: tracking?.scoreboard
}

// A catalog that is missing, or lacks something that was asked of it
open class CatalogException(message: String) : RuntimeException(message)

class UnknownTradeItemException(slug: String, tradeId: String) :
    CatalogException("Catalog $slug has no item with trade id $tradeId")

class UnknownLobbyEntryException(slug: String, index: Int) :
    CatalogException("Catalog $slug has no lobby entry $index, the item is out of date and will be replaced")

// One league's catalog document, with the parts Citadel reads indexed
class CatalogData(val document: Catalog) {
    val slug: String get() = document.slug
    val lobby: List<CatalogLobbyEntry> get() = document.lobby

    // Keyed by `<namespace>:<name>`, parsed by the Agronet library the dungeon uses
    val cards: Map<String, BrillianceCard> = LeagueCatalog.from(document).cards

    val tradeItems: Map<String, TradeItem> = tradeItemsOf(document)

    // Items that go from the deck into the dungeon with the player
    val intoDungeonItems: List<TradeItem> = tradeItems.values.filter { it.takeIntoDungeon }

    // Items earned and spent in the dungeon, shown in the lobby by their score
    val scoredTradeItems: List<TradeItem> = tradeItems.values.filter { !it.takeIntoDungeon && it.sourceScoreboard != null }

    fun tradeItem(tradeId: String): TradeItem = tradeItems[tradeId] ?: throw UnknownTradeItemException(slug, tradeId)

    fun isIntoDungeonItem(tradeId: String): Boolean = tradeItems[tradeId]?.takeIntoDungeon == true

    fun lobbyAction(index: Int): CatalogLobbyAction =
        lobby.getOrNull(index)?.action ?: throw UnknownLobbyEntryException(slug, index)

    // The lobby entry that vends this item, when there is one
    fun lobbyEntryFor(item: TradeItem): IndexedValue<CatalogLobbyEntry>? =
        lobby.withIndex().firstOrNull { it.value.item == item.catalogKey }

    // The item a lobby entry vends, when it names one
    fun tradeItemOf(entry: CatalogLobbyEntry): TradeItem? = entry.item?.let { key ->
        tradeItems.values.firstOrNull { it.catalogKey == key } ?: throw IllegalStateException("A lobby entry of $slug names $key, which is not an item of the catalog")
    }

    // The scores a lobby entry counts, which exist once the player is seeded for this league
    fun lobbyCountScoreboards(): Set<String> =
        lobby.mapNotNull { (LobbyCount.of(it.count) as? LobbyCount.Scoreboard)?.name }.toSet()

    // The scores that drive what the lobby shows for this league
    fun inventoryScoreboards(): Set<String> =
        scoredTradeItems.flatMap { listOfNotNull(it.sourceScoreboard, it.inversionScoreboard) }.toSet() + lobbyCountScoreboards()

    private companion object {
        fun tradeItemsOf(document: Catalog): Map<String, TradeItem> {
            val items = document.items.mapNotNull { (key, item) -> item.lobby?.let { TradeItem(key, it, item.tracking) } }
            val duplicate = items.groupBy { it.tradeId }.entries.firstOrNull { it.value.size > 1 }
            check(duplicate == null) { "Catalog ${document.slug} has several items with trade id ${duplicate?.key}" }
            return items.associateBy { it.tradeId }
        }
    }
}

// How many of an item a lobby entry vends: a fixed number, or the player's score
sealed interface LobbyCount {
    data class Fixed(val amount: Int) : LobbyCount
    data class Scoreboard(val name: String) : LobbyCount

    companion object {
        private const val SCOREBOARD_FIELD = "scoreboard"

        // The API client reads a JSON number as a Double and an object as a Map
        fun of(count: Any?): LobbyCount = when {
            count is Number && count.toDouble() % 1.0 == 0.0 -> Fixed(count.toInt())
            count is Map<*, *> && count[SCOREBOARD_FIELD] is String -> Scoreboard(count[SCOREBOARD_FIELD] as String)
            else -> throw IllegalArgumentException("Unsupported lobby count: $count")
        }
    }
}
