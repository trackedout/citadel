package org.trackedout.citadel.commands

import com.saicone.rtag.RtagItem
import com.saicone.rtag.tag.TagCompound
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.config.CatalogData
import org.trackedout.citadel.inventory.SLUG_TAG
import org.trackedout.data.find

private const val SHORTHAND_TAG = "shorthand"

// The card as the catalog of its league defines it, tagged with the league `slug` it is held in
fun createCard(catalog: CatalogData, cardKey: String, count: Int, slug: String? = null): ItemStack {
    val card = catalog.cards.find(cardKey) ?: throw IllegalArgumentException("Unknown card $cardKey in catalog ${catalog.slug}")

    return RtagItem.edit(ItemStack(Material.IRON_NUGGET, count), fun(tag: RtagItem): ItemStack {
        tag.merge(TagCompound.newTag(card.tagRaw), true)
        card.lore?.let { lore -> tag.set(lore, "display", "Lore") }
        slug?.let { slugValue -> tag.set(slugValue, SLUG_TAG) }
        tag.set(card.shorthand, SHORTHAND_TAG)

        return tag.loadCopy()
    })
}
