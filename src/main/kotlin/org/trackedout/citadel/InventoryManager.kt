package org.trackedout.citadel

import org.bukkit.GameRule
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.advancement.Advancement
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.commands.createCard
import org.trackedout.citadel.config.cardConfig
import org.trackedout.citadel.config.legacyRunTypes
import org.trackedout.citadel.inventory.ScoreboardDescriber
import org.trackedout.citadel.inventory.Trade
import org.trackedout.citadel.inventory.baseTradeItems
import org.trackedout.citadel.inventory.displayName
import org.trackedout.citadel.inventory.dungeonDeck
import org.trackedout.citadel.inventory.intoDungeonItems
import org.trackedout.citadel.inventory.oldCards
import org.trackedout.citadel.inventory.oldDungeonItem
import org.trackedout.citadel.inventory.tradeItems
import org.trackedout.citadel.inventory.withTradeMeta
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.apis.ScoreApi
import org.trackedout.client.models.Event
import org.trackedout.client.models.Score
import org.trackedout.data.RunType
import org.trackedout.data.runTypes
import org.trackedout.data.sortedList

class InventoryManager(
    private val plugin: Citadel,
    private val inventoryApi: InventoryApi,
    private val scoreApi: ScoreApi,
    private val eventsApi: EventsApi,
) {
    fun updateInventoryBasedOnScore(player: Player) {
        cleanUpOldItems(player)
        removeLegacyCardsFromInventory(player)

        plugin.async(player) {
            plugin.logger.info("Fetching scores for ${player.name} (filtering for prefixes: ${tradeSourceScores})")
            var scores = scoreApi.getInventoryRelatedScores(player.name)

            if (ensurePlayerHasPracticeShards(player, scores)) {
                // Update scores again to reflect new shard count
                scores = scoreApi.getInventoryRelatedScores(player.name)
            }

            scores.filter(::isInventoryRelatedScore)
                .forEach { score ->
                    updatePlayerInventoryForState(
                        player,
                        scores.associate { it.key!! to it.value!!.toInt() },
                        score.key!!,
                        score.value!!.toInt()
                    )
                }

            ensurePlayerInventoryReflectsItemsOutsideOfDeck(player)
            syncAdvancements(player)
        }
    }

    private fun ensurePlayerInventoryReflectsItemsOutsideOfDeck(player: Player) {
        // For each card in the player's inventory, ensure they only have the correct amount
        val deckItems = inventoryApi.inventoryCardsGet(player = player.name, limit = 200).results!!

        runTypes.forEach { runType ->

            // Check cards against contents of player's deck
            cardConfig.sortedList().forEach { card ->
                val maxCardsThatShouldBeInInventory = deckItems.count {
                    it.name == card.shorthand && it.deckType == runType.deckType() && it.hiddenInDecks?.isNotEmpty() == true
                }

                plugin.logger.fine("${player.name} should have ${maxCardsThatShouldBeInInventory}x${card.shorthand} in their inventory (runType: ${runType.longId})")
                val itemStack = createCard(plugin, null, card.shorthand, 1, "${runType.shortId}1")

                itemStack?.let {
                    player.ensureInventoryContains(it.clone().apply { amount = zeroSupportedItemCount(maxCardsThatShouldBeInInventory) })
                }
            }

            // Check items against contents of player's deck
            intoDungeonItems.entries.forEach { (itemKey, scoreboardDescriber) ->
                val maxItemsThatShouldBeInInventory = deckItems.count {
                    it.name == itemKey && it.deckType == runType.deckType() && it.hiddenInDecks?.isNotEmpty() == true
                }
                plugin.logger.fine("${player.name} should have ${maxItemsThatShouldBeInInventory}x${itemKey} in their inventory (runType: ${runType.longId})")
                val itemStack = scoreboardDescriber.itemStack(runType.longId, 1)

                itemStack.let {
                    player.ensureInventoryContains(it.withTradeMeta(runType.longId, itemKey).clone().apply { amount = zeroSupportedItemCount(maxItemsThatShouldBeInInventory) })
                }
            }
        }

    }

    private fun ensurePlayerHasPracticeShards(player: Player, scores: List<Score>): Boolean {
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

                    return true
                }
            }
        }

        return false
    }

    private fun zeroSupportedItemCount(count: Int) = if (count <= 0) 999 else count

    private fun cleanUpOldItems(player: Player) {
        plugin.logger.info("Cleaning up old items for ${player.name}")
        for (runType in legacyRunTypes) {
            baseTradeItems.plus(intoDungeonItems).values.forEach { trade ->
                val itemStack = trade.itemStack(runType, 0)
                if (!listOf(Material.AIR, Material.STICK).contains(itemStack.type)) {
                    val oldDungeonItem = itemStack.oldDungeonItem()
                    player.ensureInventoryContains(oldDungeonItem)
                }
            }
        }
        plugin.logger.info("Finished cleaning up old items")
    }

    private fun removeLegacyCardsFromInventory(player: Player) {
        plugin.logger.info("Cleaning up legacy cards for ${player.name}")
        for (runType in legacyRunTypes) {
            oldCards().values.forEach { trade ->
                val itemStack = trade.itemStack(runType, 999)
                plugin.logger.fine("Cleaning up old card: $itemStack")
                if (!listOf(Material.AIR, Material.STICK).contains(itemStack.type)) {
                    player.ensureInventoryContains(itemStack)
                }
            }
        }
        plugin.logger.info("Finished cleaning up legacy cards for ${player.name}")
    }

    private fun updatePlayerInventoryForState(player: Player, scores: Map<String, Int>, key: String, value: Int) {
        plugin.logger.info("Player ${player.name} has inventory score $key=$value")

        for (runType in runTypes.map { it.longId }) {
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

        for (runType in runTypes) {
            when (key) {
                // Shards
                "do2.inventory.shards.${runType.longId}" -> {
                    player.ensureInventoryContains(dungeonDeck(runType))
                }
            }
        }
    }

    private fun Player.ensureInventoryContains(itemStack: ItemStack) {
        val targetAmount = if (itemStack.amount == 999) 0 else itemStack.amount
        plugin.logger.fine("Ensuring ${this.name} has ${targetAmount}x ${itemStack.type.name} (name: ${itemStack.name()})")

        var attempts = 0

        // Offhand stacks are not counted in the main inventory, which allows for unwanted duplication
        // So we just remove the offhand stack if it matches the item we're trying to add
        if (inventory.itemInOffHand.isSimilar(itemStack)) {
            inventory.itemInOffHand.amount = 0
        }

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
                plugin.logger.fine("Adding item ${itemStack}x${targetAmount - i}")
                val failedItems = inventory.addItem(itemStack.clone().apply { amount = targetAmount - i })
                if (failedItems.isNotEmpty()) {
                    plugin.logger.warning("$failedItems items failed to be added to ${this.name}'s inventory")
                }
                break
            }
        }
    }

    private fun syncAdvancements(player: Player) {
        val server = player.server
        val playerName = player.name

        try {
            val runType = "c".displayName().lowercase()
            val advancementFilter = "$runType-advancement-"

            plugin.logger.info("Score filter for advancements: $advancementFilter")
            val scores = scoreApi.scoresGet(
                player = playerName,
                prefixFilter = advancementFilter,
                limit = 10000,
            )

            plugin.runOnNextTick {
                try {
                    // If this is the first time the player has logged in, we don't want to spam everyone with advancements
                    server.getAdvancement(NamespacedKey("do2", "visible/cards/get_deck_box"))?.let { advancement: Advancement ->
                        player.getAdvancementProgress(advancement).let { progress ->
                            if (!progress.isDone) {
                                plugin.logger.info("Preventing advancement spam")
                                player.world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false)
                            }
                        }
                    }

                    scores.results!!
                        .asSequence()
                        .filter { it.key!!.startsWith(advancementFilter) }
                        .map { it.copy(key = it.key?.substring(advancementFilter.length)) }
                        .filter { it.key!!.isNotBlank() && it.key!!.contains("#") }
                        .filter { it.key!!.contains("hidden") || it.key!!.contains("visible") }
                        .filter { it.value!!.toInt() > 0 }
                        .toList()
                        .forEach { score ->
                            // e.g. competitive-advancement-do2#hidden/survival/win_50_times#given_by_commands
                            val split = score.key!!.split("#")
                            var namespace = "do2"
                            var key = split[0]
                            var criterion = split[1]

                            if (split.size == 3) {
                                namespace = split[0]
                                key = split[1]
                                criterion = split[2]
                            }

                            server.getAdvancement(NamespacedKey(namespace, key))?.let { advancement: Advancement ->
                                player.getAdvancementProgress(advancement).let { progress ->
                                    if (progress.isDone || progress.awardedCriteria.contains(criterion)) {
//                                        plugin.logger.info("$playerName already has advancement ${key}#${criterion}")
                                    } else {
                                        progress.awardCriteria(criterion)
//                                        plugin.logger.info("Granted advancement ${key}#${criterion} to $playerName")
                                    }
                                }
                            }
                        }

                    player.world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, true)
                } catch (e: Exception) {
                    e.printStackTrace()
                    player.sendRedMessage(
                        "An error occurred when attempting to apply your data from dunga-dunga, " +
                            "and your advancement data could not be imported."
                    )
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            player.sendRedMessage(
                "An error occurred when attempting to fetch your data from dunga-dunga, " +
                    "and your advancement data could not be imported."
            )
        }
    }
}

val tradeSourceScores = baseTradeItems.values.flatMap {
    runTypes.map(RunType::longId).map { runType ->
        listOf(
            it.sourceScoreboardName(runType),
            it.sourceInversionScoreboardName(runType),
        )
    }.flatten()
}.filterNot { it.isBlank() }.distinct()

fun ScoreApi.getInventoryRelatedScores(playerName: String): List<Score> {
    return tradeSourceScores.flatMap {
        this.scoresGet(playerName, prefixFilter = it).results!!
    }
}

fun isInventoryRelatedScore(score: Score): Boolean {
    return tradeSourceScores.contains(score.key!!)
}
