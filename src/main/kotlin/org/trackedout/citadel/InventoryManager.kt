package org.trackedout.citadel

import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.inventory.dungeonCrown
import org.trackedout.citadel.inventory.dungeonShard
import org.trackedout.client.apis.ScoreApi

class InventoryManager(
    private val plugin: Citadel,
    private val scoreApi: ScoreApi,
) {
    fun updateInventoryBasedOnScore(player: Player) {
        plugin.async(player) {
            plugin.logger.info("Fetching scores for ${player.name}")
            val scores = scoreApi.scoresGet(player = player.name).results!!

            scores
                .filter { it.key!!.startsWith("do2.inventory") || it.key!!.contains("do2.lifetime.escaped.crowns") }
                .forEach { score -> updatePlayerInventoryForState(player, scores.associate { it.key!! to it.value!!.toInt() }, score.key!!, score.value!!.toInt()) }
        }
    }

    private fun updatePlayerInventoryForState(player: Player, scores: Map<String, Int>, key: String, value: Int) {
        plugin.logger.info("Player ${player.name} has inventory score $key=$value")

        when (key) {
            // Shards
            "do2.inventory.shards.practice" -> {
                player.ensureInventoryContains(dungeonShard("Practice runs (infinite!?)", itemCount = value))
            }

            "do2.inventory.shards.competitive" -> {
                player.ensureInventoryContains(dungeonShard("Competitive runs", itemCount = value))
            }

            "do2.inventory.shards.hardcore" -> {
                player.ensureInventoryContains(dungeonShard("Hardcore runs", NamedTextColor.RED, value))
            }

            // Crowns
            "practice-do2.lifetime.escaped.crowns" -> {
                val itemCount = value - scores.getOrDefault("practice-do2.lifetime.spent.crowns", 0)
                player.ensureInventoryContains(dungeonCrown("Practice", itemCount = itemCount))
            }

            "competitive-do2.lifetime.escaped.crowns" -> {
                val itemCount = value - scores.getOrDefault("competitive-do2.lifetime.spent.crowns", 0)
                player.ensureInventoryContains(dungeonCrown("Competitive", itemCount = itemCount))
            }
        }
    }

    private fun Player.ensureInventoryContains(itemStack: ItemStack) {
        plugin.logger.info("Ensuring ${this.name} has ${itemStack.amount}x ${itemStack.type.name} (name: ${itemStack.name()})")

        var attempts = 0
        while (inventory.containsAtLeast(itemStack, 1)) {
            plugin.logger.fine("Removing item $itemStack")
            inventory.removeItemAnySlot(itemStack.clone().apply { amount = 1 })
            attempts++
            if (attempts >= 200) {
                plugin.logger.warning("Failed to remove $itemStack from ${this.name}'s inventory after 200 attempts")
                return
            }
        }

        plugin.logger.info("Adding item $itemStack")
        val failedItems = inventory.addItem(itemStack)
        if (failedItems.isNotEmpty()) {
            plugin.logger.warning("$failedItems items failed to be added to ${this.name}'s inventory")
        }
    }
}
