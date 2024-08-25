package org.trackedout.citadel

import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.inventory.competitiveCrown
import org.trackedout.citadel.inventory.competitiveDeck
import org.trackedout.citadel.inventory.competitiveShard
import org.trackedout.citadel.inventory.competitiveTome
import org.trackedout.citadel.inventory.dungeonShard
import org.trackedout.citadel.inventory.practiceCrown
import org.trackedout.citadel.inventory.practiceDeck
import org.trackedout.citadel.inventory.practiceShard
import org.trackedout.citadel.inventory.practiceTome
import org.trackedout.client.apis.ScoreApi

class InventoryManager(
    private val plugin: Citadel,
    private val scoreApi: ScoreApi,
) {
    fun updateInventoryBasedOnScore(player: Player) {
        plugin.async(player) {
            plugin.logger.info("Fetching scores for ${player.name}")
            val scores = scoreApi.scoresGet(player = player.name).results!!

            scores.filter {
                it.key!!.startsWith("do2.inventory")
                    || it.key!!.contains("do2.lifetime.escaped.crowns")
                    || it.key!!.contains("do2.lifetime.escaped.tomes")
            }.forEach { score -> updatePlayerInventoryForState(player, scores.associate { it.key!! to it.value!!.toInt() }, score.key!!, score.value!!.toInt()) }
        }
    }

    private fun updatePlayerInventoryForState(player: Player, scores: Map<String, Int>, key: String, value: Int) {
        plugin.logger.info("Player ${player.name} has inventory score $key=$value")

        when (key) {
            // Shards
            "do2.inventory.shards.practice" -> {
                player.ensureInventoryContains(practiceShard(value))
                player.ensureInventoryContains(practiceDeck())
            }

            "do2.inventory.shards.competitive" -> {
                player.ensureInventoryContains(competitiveShard(value))
                player.ensureInventoryContains(competitiveDeck())
            }

            "do2.inventory.shards.hardcore" -> {
                player.ensureInventoryContains(dungeonShard("Hardcore runs", NamedTextColor.RED, value))
            }

            // Crowns
            "practice-do2.lifetime.escaped.crowns" -> {
                val itemCount = value - scores.getOrDefault("practice-do2.lifetime.spent.crowns", 0)
                player.ensureInventoryContains(practiceCrown(itemCount))
            }

            "competitive-do2.lifetime.escaped.crowns" -> {
                val itemCount = value - scores.getOrDefault("competitive-do2.lifetime.spent.crowns", 0)
                player.ensureInventoryContains(competitiveCrown(itemCount))
            }

            // Tomes
            "practice-do2.lifetime.escaped.tomes" -> {
                val itemCount = value - scores.getOrDefault("practice-do2.lifetime.spent.tomes", 0)
                player.ensureInventoryContains(practiceTome(itemCount))
            }

            "competitive-do2.lifetime.escaped.tomes" -> {
                val itemCount = value - scores.getOrDefault("competitive-do2.lifetime.spent.tomes", 0)
                player.ensureInventoryContains(competitiveTome(itemCount))
            }
        }
    }

    private fun Player.ensureInventoryContains(itemStack: ItemStack) {
        val targetAmount = if (itemStack.amount == 999) 0 else itemStack.amount
        plugin.logger.info("Ensuring ${this.name} has ${targetAmount}x ${itemStack.type.name} (name: ${itemStack.name()})")

        var attempts = 0
        while (inventory.containsAtLeast(itemStack, targetAmount + 1)) {
            plugin.logger.fine("Removing item $itemStack")
            inventory.removeItemAnySlot(itemStack.clone().apply { amount = 1 })
            attempts++
            if (attempts >= 200) {
                plugin.logger.warning("Failed to remove $itemStack from ${this.name}'s inventory after 200 attempts")
                return
            }
        }

        if (inventory.containsAtLeast(itemStack, targetAmount)) {
            return
        }

        for (i in targetAmount - 1 downTo 0) {
            if (inventory.containsAtLeast(itemStack, i) || i == 0) {
                plugin.logger.info("Adding item ${itemStack}x${targetAmount - i}")
                val failedItems = inventory.addItem(itemStack.clone().apply { amount = targetAmount - i })
                if (failedItems.isNotEmpty()) {
                    plugin.logger.warning("$failedItems items failed to be added to ${this.name}'s inventory")
                }
                break
            }
        }
    }
}
