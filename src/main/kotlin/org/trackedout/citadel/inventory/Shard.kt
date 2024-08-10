package org.trackedout.citadel.inventory

import com.saicone.rtag.RtagItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

fun practiceShard(value: Int) = dungeonShard("Practice runs", NamedTextColor.GREEN, itemCount = value)

fun competitiveShard(value: Int) = dungeonShard("Competitive runs", itemCount = value)

fun dungeonShard(
    name: String,
    textColor: NamedTextColor = NamedTextColor.AQUA,
    itemCount: Int = 1,
) = dungeonItem(name, 7, textColor, itemCount = itemCount)

fun dungeonCrown(
    crownType: String,
    textColor: NamedTextColor = NamedTextColor.AQUA,
    itemCount: Int = 1,
) = dungeonItem("❄☠ Decked Out Crown (${crownType}) ☠❄", 2, textColor, itemCount = itemCount)

fun practiceDeck() = dungeonDeck(
    "❄☠ Frozen Assets (Practice Deck #1) ☠❄",
    textColor = NamedTextColor.GREEN,
    material = Material.LIME_SHULKER_BOX,
    deckId = "p1"
)

fun competitiveDeck() = dungeonDeck("❄☠ Frozen Assets (Competitive Deck #1) ☠❄", deckId = "c1")

fun dungeonDeck(
    name: String = "❄☠ Frozen Assets ☠❄",
    textColor: NamedTextColor = NamedTextColor.AQUA,
    material: Material = Material.CYAN_SHULKER_BOX,
    deckId: String,
) = dungeonItem(name, 7, textColor, material = material, metadata = mapOf("deckId" to deckId))

fun dungeonItem(
    name: String,
    customModelData: Int,
    textColor: NamedTextColor = NamedTextColor.AQUA,
    material: Material = Material.IRON_NUGGET,
    itemCount: Int = 1,
    metadata: Map<String, String> = mapOf(),
): ItemStack {
    val itemStack = ItemStack(material, if (itemCount <= 0) 999 else itemCount)
    val meta = itemStack.itemMeta
    meta.displayName(Component.text(name, textColor))
    meta.setCustomModelData(customModelData)

    itemStack.itemMeta = meta

    return RtagItem.edit(itemStack, fun(tag: RtagItem): ItemStack {
        metadata.entries.forEach { entry -> tag.set(entry.value, entry.key) }
        return tag.loadCopy();
    })
}
