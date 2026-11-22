package org.trackedout.citadel.inventory

import me.devnatan.inventoryframework.ViewFrame
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.commands.createCard
import org.trackedout.citadel.config.CatalogData
import org.trackedout.citadel.runOnNextTick
import org.trackedout.client.apis.ScoreApi
import org.trackedout.client.models.CatalogArtifact
import org.trackedout.client.models.CatalogLobbyAction
import org.trackedout.client.models.CatalogLobbyActionMissing

private const val MAX_SCORES = 1000
private val MISSING_COLOUR = NamedTextColor.GRAY
private val TITLE_COLOUR = NamedTextColor.BLUE

// Opens a grid of a league's cards, artifacts or items for `viewer`, showing what `ownerName` has
// of each and a placeholder for what they have none of. A viewer that is no player gets a list
fun showCollection(
    plugin: Citadel,
    viewFrame: ViewFrame,
    scoreApi: ScoreApi,
    viewer: CommandSender,
    ownerName: String,
    slug: String,
    action: CatalogLobbyAction,
) {
    plugin.async(viewer) {
        val catalog = plugin.catalogCache.require(slug)
        val kind = action.kind ?: throw IllegalArgumentException("The show-collection action of $slug has no kind")
        val rows = action.rows ?: throw IllegalArgumentException("The show-collection action of $slug has no rows")
        val missing = action.missing ?: throw IllegalArgumentException("The show-collection action of $slug has no missing item")
        val title = action.title ?: throw IllegalArgumentException("The show-collection action of $slug has no title")

        val entries = sortedCollection(catalog.collectionEntries(kind), action.sortBy)
        val scores = scoresOf(scoreApi, ownerName, slug, entries)
        val counts = entries.map { it.countIn(scores) }

        if (viewer !is Player) {
            viewer.sendMessage("${kind.value}s of $ownerName ($slug):")
            entries.zip(counts).filter { (_, count) -> count > 0 }.forEach { (entry, count) -> viewer.sendMessage("${entry.scoreboard} = $count") }
            return@async
        }

        val stacks = entries.zip(counts).map { (entry, count) -> collectionStack(catalog, kind, entry, count, missing) }
        val items = collectionSlots(entries.size, rows).zip(stacks).toMap()
        val viewTitle = Component.text(collectionTitle(title, viewer.name, ownerName)).color(TITLE_COLOUR).decorate(TextDecoration.BOLD)

        plugin.runOnNextTick {
            val context = BasicItemView.createContext(plugin, viewer, viewTitle, items, rows)
            viewFrame.open(BasicItemView::class.java, viewer, context)
        }
    }
}

private fun scoresOf(scoreApi: ScoreApi, ownerName: String, slug: String, entries: List<CollectionEntry>): Map<String, Int> {
    if (entries.isEmpty()) return emptyMap()

    return scoreApi.scoresGet(ownerName, slug = slug, prefixFilter = scorePrefixOf(entries), limit = MAX_SCORES).results!!
        .associate { it.key!! to it.value!!.toInt() }
}

private fun collectionStack(
    catalog: CatalogData,
    kind: CatalogLobbyAction.Kind,
    entry: CollectionEntry,
    count: Int,
    missing: CatalogLobbyActionMissing,
): ItemStack {
    val stack = stackOf(catalog, kind, entry.key, count.coerceAtLeast(1))
    return if (count > 0) stack else missingStack(missing, stack)
}

private fun stackOf(catalog: CatalogData, kind: CatalogLobbyAction.Kind, key: String, count: Int): ItemStack = when (kind) {
    CatalogLobbyAction.Kind.card -> createCard(catalog, catalog.cards.getValue(key).shorthand, count)
    CatalogLobbyAction.Kind.artifact -> artifactStack(key, catalog.document.artifacts.getValue(key), count)
    CatalogLobbyAction.Kind.item -> {
        val item = catalog.tradeItems.values.first { it.catalogKey == key }
        tradeItemSpec(catalog.slug, item, count).toItemStack()
    }
}

private fun artifactStack(key: String, artifact: CatalogArtifact, count: Int): ItemStack {
    val display = artifactDisplay(key, artifact)
    val stack = ItemStack(materialOf(artifact.id), count)
    val meta = stack.itemMeta
    meta.displayName(JSONComponentSerializer.json().deserialize(display.name))
    meta.lore(display.lore.map { JSONComponentSerializer.json().deserialize(it) })
    display.customModelData?.let { meta.setCustomModelData(it) }
    stack.itemMeta = meta
    return stack
}

// What stands in for an entry the owner has none of, in the material of the entry unless the catalog gives one
private fun missingStack(missing: CatalogLobbyActionMissing, entryStack: ItemStack): ItemStack {
    val stack = ItemStack(missing.material?.let(::materialOf) ?: entryStack.type, 1)
    val meta = stack.itemMeta
    val name = Component.text(missing.name).color(MISSING_COLOUR)
    meta.displayName(if (missing.obfuscated == true) name.decorate(TextDecoration.OBFUSCATED) else name)
    meta.lore(missing.lore.orEmpty().map { Component.text(it).color(MISSING_COLOUR) })
    stack.itemMeta = meta
    return stack
}
