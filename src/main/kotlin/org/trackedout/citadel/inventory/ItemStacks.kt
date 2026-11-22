package org.trackedout.citadel.inventory

import net.kyori.adventure.text.Component.text
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionData
import org.bukkit.potion.PotionType
import org.trackedout.citadel.config.CatalogData
import org.trackedout.citadel.withTags

fun materialOf(name: String): Material = Material.matchMaterial(name) ?: throw IllegalArgumentException("Unknown material: $name")

fun ItemSpec.toItemStack(): ItemStack {
    val stack = ItemStack(materialOf(material), amount)
    val meta = stack.itemMeta
    meta.displayName(text(name, colour))
    customModelData?.let { meta.setCustomModelData(it) }
    if (lore.isNotEmpty()) {
        meta.lore(lore.map { text(it.text, it.colour) })
    }
    potion?.let {
        (meta as PotionMeta).basePotionData = PotionData(PotionType.valueOf(it.type), it.extended, it.upgraded)
    }
    stack.itemMeta = meta
    return stack.withTags(tags)
}

// The item as a shop takes it and the lobby hands it out. An item that has a lobby entry is held the way
// that entry builds it, otherwise a shop would not recognise the stack the player holds
fun CatalogData.tradeItemStack(tradeId: String, count: Int): ItemStack {
    val item = tradeItem(tradeId)
    val entry = lobbyEntryFor(item)
        ?: return tradeItemSpec(slug, item, count).toItemStack()
    return lobbyEntrySpec(slug, entry.index, entry.value, item, count).toItemStack()
}
