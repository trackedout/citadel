package org.trackedout.citadel

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.inventory.ScoreboardDescriber
import org.trackedout.citadel.inventory.Trade
import org.trackedout.citadel.inventory.baseTradeItems
import org.trackedout.citadel.inventory.competitiveDeck
import org.trackedout.citadel.inventory.intoDungeonItems
import org.trackedout.citadel.inventory.oldDungeonItem
import org.trackedout.citadel.inventory.practiceDeck
import org.trackedout.citadel.inventory.tradeItems
import org.trackedout.citadel.inventory.withTradeMeta
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.ScoreApi
import org.trackedout.client.models.Event
import org.trackedout.client.models.Score

class InventoryManager(
    private val plugin: Citadel,
    private val scoreApi: ScoreApi,
    private val eventsApi: EventsApi,
) {
    fun updateInventoryBasedOnScore(player: Player) {
        cleanUpOldItems(player)

        plugin.async(player) {
            plugin.logger.info("Fetching scores for ${player.name}")
            var scores = scoreApi.scoresGet(player = player.name).results!!

            scores = ensurePlayerHasPracticeShards(player, scores)

            scores.filter(::isInventoryRelatedScore)
                .forEach { score ->
                    updatePlayerInventoryForState(
                        player,
                        scores.associate { it.key!! to it.value!!.toInt() },
                        score.key!!,
                        score.value!!.toInt()
                    )
                }
        }
    }

    private fun ensurePlayerHasPracticeShards(player: Player, currentScores: List<Score>): List<Score> {
        var scores = currentScores

        tradeItems["SHARD"]?.let { tradeItem ->
            scores.find { score -> score.key == tradeItem.sourceScoreboardName("practice") }?.let { score ->
                if (score.value!!.toInt() <= 10) {
                    val trade = Trade(
                        runType = "practice",
                        sourceType = "dummy",
                        sourceItemCount = 0,
                        targetType = "SHARD",
                        targetItemCount = 64 - score.value!!.toInt(),
                    )

                    eventsApi.eventsPost(
                        Event(
                            name = "trade-requested",
                            server = plugin.serverName,
                            player = player.name,
                            count = 1,
                            x = 0.0,
                            y = 0.0,
                            z = 0.0,
                            metadata = mapOf(
                                "run-type" to trade.runType,
                                "source-scoreboard" to trade.sourceScoreboardName(),
                                "source-inversion-scoreboard" to trade.sourceInversionScoreboardName(),
                                "source-count" to trade.sourceItemCount.toString(),
                                "target-scoreboard" to trade.targetScoreboardName(),
                                "target-count" to trade.targetItemCount.toString(),
                                "reason" to "Granted by Citadel - shard low-water check",
                            )
                        )
                    )

                    scores = scoreApi.scoresGet(player = player.name).results!!
                }
            }
        }

        return scores
    }

    private fun cleanUpOldItems(player: Player) {
        plugin.logger.info("Cleaning up old items for ${player.name}")
        for (runType in listOf("practice", "competitive")) {
            baseTradeItems.plus(intoDungeonItems).values.forEach { trade ->
                val itemStack = trade.itemStack(runType, 0)
                if (!listOf(Material.AIR, Material.STICK).contains(itemStack.type)) {
                    player.ensureInventoryContains(itemStack.oldDungeonItem())
                }
            }
        }
        plugin.logger.info("Finished cleaning up old items")
    }

    private fun updatePlayerInventoryForState(player: Player, scores: Map<String, Int>, key: String, value: Int) {
        plugin.logger.info("Player ${player.name} has inventory score $key=$value")

        for (runType in listOf("practice", "competitive")) {
            baseTradeItems.forEach { it: Map.Entry<String, ScoreboardDescriber> ->
                val sb = it.value
                val sourceScoreKey = sb.sourceScoreboardName(runType)
                if (sourceScoreKey == key) {
                    plugin.logger.info("Score $key matches ${it.key} ($runType)")

                    var itemCount = value
                    val inversionScoreKey = sb.sourceInversionScoreboardName(runType)
                    if (sourceScoreKey != inversionScoreKey) {
                        itemCount -= scores.getOrDefault(inversionScoreKey, 0)
                    }

                    player.ensureInventoryContains(sb.itemStack(runType, itemCount).withTradeMeta(runType, it.key))
                }
            }
        }

        when (key) {
            // Shards
            "do2.inventory.shards.practice" -> {
                player.ensureInventoryContains(practiceDeck())
            }

            "do2.inventory.shards.competitive" -> {
                player.ensureInventoryContains(competitiveDeck())
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

fun isInventoryRelatedScore(score: Score): Boolean {
    val key = score.key!!

    val tradeSourceScores = baseTradeItems.values.flatMap {
        listOf(it.sourceScoreboardName("competitive"), it.sourceScoreboardName("practice"))
    }

    return tradeSourceScores.contains(key)
}
