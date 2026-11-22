package org.trackedout.citadel.inventory

import org.trackedout.citadel.config.CatalogData
import org.trackedout.client.models.CatalogArtifact
import org.trackedout.client.models.CatalogLobbyAction
import java.math.BigDecimal

private const val SORT_BY_EMBER_VALUE = "emberValue"
private const val OWNER_PLACEHOLDER = "{owner}"
private const val YOUR = "Your"
private const val SLOTS_PER_ROW = 9

// Entries that do not fit in the full rows start in the middle of the last row
private const val LAST_ROW_START_OFFSET = 4

// A card, artifact or item of a catalog that a collection shows, once the player has some of it
data class CollectionEntry(
    val key: String,
    val scoreboard: String,
    val emberValue: BigDecimal?,
) {
    fun countIn(scores: Map<String, Int>): Int = scores[scoreboard] ?: 0
}

fun CatalogData.collectionEntries(kind: CatalogLobbyAction.Kind): List<CollectionEntry> = when (kind) {
    CatalogLobbyAction.Kind.card -> document.cards.map { (key, card) -> CollectionEntry(key, card.tracking.played, card.emberValue) }
    CatalogLobbyAction.Kind.artifact -> document.artifacts.map { (key, artifact) -> CollectionEntry(key, artifact.tracking.scoreboard, artifact.emberValue) }
    CatalogLobbyAction.Kind.item -> tradeItems.values.mapNotNull { item ->
        item.tracking?.scoreboard?.let { CollectionEntry(item.catalogKey, it, null) }
    }
}

// Lowest ember value first, entries without one last
fun sortedCollection(entries: List<CollectionEntry>, sortBy: String?): List<CollectionEntry> = when (sortBy) {
    null -> entries
    SORT_BY_EMBER_VALUE -> entries.sortedWith(compareBy(nullsLast<BigDecimal>()) { it.emberValue })
    else -> throw IllegalArgumentException("Unsupported collection sort: $sortBy")
}

// The inventory slot of each entry: the full rows are filled in order, the rest continue from the middle of the last row
fun collectionSlots(entryCount: Int, rows: Int): List<Int> {
    require(rows >= 1) { "A collection needs at least one row, got $rows" }
    val fullRowSlots = (rows - 1) * SLOTS_PER_ROW
    val capacity = fullRowSlots + SLOTS_PER_ROW - LAST_ROW_START_OFFSET
    require(entryCount <= capacity) { "A collection of $rows rows holds $capacity entries, got $entryCount" }

    return (0 until entryCount).map { if (it < fullRowSlots) it else it + LAST_ROW_START_OFFSET }
}

// "{owner}" is "Your" for the player's own collection and "<player>'s" for someone else's
fun collectionTitle(template: String, viewerName: String, ownerName: String): String =
    template.replace(OWNER_PLACEHOLDER, if (viewerName == ownerName) YOUR else "$ownerName's")

// The scores a collection reads share the start of their names, which is all that is asked of the backend
fun scorePrefixOf(entries: List<CollectionEntry>): String? =
    entries.map { it.scoreboard }.reduceOrNull { prefix, scoreboard -> prefix.commonPrefixWith(scoreboard) }?.ifEmpty { null }

// What the catalog says an artifact shows as, in the shape of the item's NBT
data class ArtifactDisplay(val name: String, val lore: List<String>, val customModelData: Int?)

fun artifactDisplay(key: String, artifact: CatalogArtifact): ArtifactDisplay {
    val tag = artifact.tag as? Map<*, *> ?: throw IllegalArgumentException("Artifact $key has a tag that is not an object")
    val display = tag["display"] as? Map<*, *> ?: throw IllegalArgumentException("Artifact $key has no display")
    val name = display["Name"] as? String ?: throw IllegalArgumentException("Artifact $key has no name")
    val lore = (display["Lore"] as? List<*>).orEmpty().map { it as String }

    return ArtifactDisplay(name, lore, (tag["CustomModelData"] as? Number)?.toInt())
}
