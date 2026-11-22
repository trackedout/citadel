package org.trackedout.citadel.inventory

import com.saicone.rtag.RtagItem
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.LEGACY_DECK_ID_TAG

fun dungeonItem(
    name: String,
    customModelData: Int,
    textColor: NamedTextColor = NamedTextColor.AQUA,
    material: Material = Material.IRON_NUGGET,
    itemCount: Int = 1,
    metadata: Map<String, String> = mapOf(),
): ItemStack {
    val itemStack = ItemStack(material, amountOf(itemCount))
    val meta = itemStack.itemMeta
    meta.displayName(text(name, textColor))
    meta.setCustomModelData(customModelData)

    itemStack.itemMeta = meta

    return RtagItem.edit(itemStack, fun(tag: RtagItem): ItemStack {
        metadata.entries.forEach { entry -> tag.set(entry.value, entry.key) }
        return tag.loadCopy()
    })
}

fun menuBook(): ItemStack = dungeonItem(
    name = "❄☠ Welcome to Tracked Out ☠❄",
    material = Material.WRITTEN_BOOK,
    textColor = NamedTextColor.GOLD,
    metadata = mapOf("slug" to "", "action" to "show-main-menu"),
    itemCount = 1,
    customModelData = 0,
).withLore(text("An introduction to our server!", NamedTextColor.GOLD))

fun settingsBook(): ItemStack = dungeonItem(
    name = "❄☠ Player Settings ☠❄",
    material = Material.WRITTEN_BOOK,
    textColor = NamedTextColor.AQUA,
    metadata = mapOf("slug" to "", "action" to "show-player-settings"),
    itemCount = 1,
    customModelData = 0,
).withLore(text("Configure your Decked Out experience!", NamedTextColor.AQUA))

fun adminBook(): ItemStack = dungeonItem(
    name = "❄☠ Dungeon Ops ☠❄",
    material = Material.WRITTEN_BOOK,
    textColor = NamedTextColor.RED,
    metadata = mapOf("slug" to "", "action" to "show-admin-book"),
    itemCount = 1,
    customModelData = 0,
).withLore(text("Server management tools", NamedTextColor.RED))

fun ItemStack.withLore(vararg lore: TextComponent): ItemStack {
    return this.apply {
        val meta = this.itemMeta
        meta.lore(lore.toList())
        this.itemMeta = meta
    }
}

fun ItemStack.oldDungeonItem(): ItemStack {
    this.amount = ABSENT_ITEM_AMOUNT
    return RtagItem.edit(this, fun(tag: RtagItem): ItemStack {
        tag.remove("slug")
        tag.remove(LEGACY_DECK_ID_TAG)
        tag.remove("tradeId")
        tag.remove("canTakeIntoDungeon")
        return tag.loadCopy()
    })
}
