package org.trackedout.citadel.commands

import com.saicone.rtag.RtagItem
import com.saicone.rtag.tag.TagCompound
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.config.cardConfig
import org.trackedout.citadel.sendRedMessage
import org.trackedout.data.find

fun createCard(plugin: Citadel?, player: Player?, cardKey: String, count: Int, deckId: String? = null): ItemStack? {
    try {
        val nugget = ItemStack(Material.IRON_NUGGET, count)
        val card = cardConfig.find(cardKey)?.let {
            return@let RtagItem.edit(nugget, fun(tag: RtagItem): ItemStack {

                tag.merge(TagCompound.newTag(it.tagRaw), true)
                it.lore?.let { lore ->
                    tag.set(lore, "display", "Lore")
                }
                deckId?.let { deckIdValue -> tag.set(deckIdValue, "deckId") }
                tag.set(it.shorthand, "shorthand")

                return tag.loadCopy()
            })
        }

        if (card == null) {
            player?.sendRedMessage("Failed to add $cardKey to your shulker as it's metadata is missing")
            plugin?.logger?.warning("Failed to add $cardKey to your shulker as card data for this card was not found")
        }

        return card
    } catch (e: Exception) {
        player?.sendRedMessage("Failed to add $cardKey to your shulker, error: ${e.message}")
        plugin?.logger?.warning("Failed to add $cardKey to your shulker, error: ${e.message}")
        e.printStackTrace()
        return null
    }
}
