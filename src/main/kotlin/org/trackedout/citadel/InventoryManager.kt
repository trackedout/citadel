package org.trackedout.citadel

import org.bukkit.GameRule
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.advancement.Advancement
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.commands.createCard
import org.trackedout.citadel.config.cardConfig
import org.trackedout.citadel.inventory.ScoreboardDescriber
import org.trackedout.citadel.inventory.Trade
import org.trackedout.citadel.inventory.baseTradeItems
import org.trackedout.citadel.inventory.displayName
import org.trackedout.citadel.inventory.dungeonArtifacts
import org.trackedout.citadel.inventory.dungeonDeck
import org.trackedout.citadel.inventory.intoDungeonItems
import org.trackedout.citadel.inventory.menuBook
import org.trackedout.citadel.inventory.settingsBook
import org.trackedout.citadel.inventory.adminBook
import org.trackedout.citadel.inventory.tradeItems
import org.trackedout.citadel.inventory.withTradeMeta
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.apis.ScoreApi
import org.trackedout.client.apis.ConfigApi
import org.trackedout.client.infrastructure.ClientException
import org.trackedout.client.models.Event
import org.trackedout.client.models.Score
import org.trackedout.citadel.config.PRACTICE_SLUG
import org.trackedout.citadel.config.RunType
import org.trackedout.citadel.config.LeagueCache
import org.trackedout.citadel.config.runTypeFromLeague
import org.trackedout.citadel.config.getRunTypesFromCache
import org.trackedout.data.sortedList

// Per-player config key holding the canonical league SLUG the player selected as
// their run mode. This fully replaces the old numeric `do2.inventory.filter-mode-id`
// scoreboard, which was ambiguous after the slug migration (all season-N leagues
// share runTypeId 2). It is a config, not a scoreboard.
const val FILTER_MODE_SLUG_CONFIG = "filter-mode-slug"

// Default run-mode slug when a player has no filter-mode-slug set.
const val DEFAULT_FILTER_MODE_SLUG = "do2-s2-comp"

// Name of the shard balance scoreboard. Same in every league namespace.
const val SHARDS_SCOREBOARD = "do2.inventory.shards"

// Lobby advancements mirror what the player earned in the competitive league.
const val ADVANCEMENT_SOURCE_SLUG = DEFAULT_FILTER_MODE_SLUG

// Advancement scores are keyed advancement-<namespace>#<path>#<criterion>.
const val ADVANCEMENT_KEY_PREFIX = "advancement-"

// Identifies a score: its key is only unique within the league namespace (slug).
data class ScoreRef(val slug: String, val key: String)

class InventoryManager(
    private val plugin: Citadel,
    private val inventoryApi: InventoryApi,
    private val scoreApi: ScoreApi,
    private val eventsApi: EventsApi,
    private val configApi: ConfigApi,
) {
    private fun effectiveRunTypes(): List<RunType> {
        return getRunTypesFromCache(plugin.leagueCache)
    }

    fun updateInventoryBasedOnScore(player: Player) {
        val knownItemTracker = mutableSetOf<ItemStack>()
        // Ordered list of the items the player SHOULD end up with, collected before any
        // inventory mutation. We reconcile the live inventory only after old items have
        // been cleared, so newly-added items reclaim the freed slots instead of landing
        // in later empty slots (which left gaps in the hotbar when switching run modes).
        val desiredItems = mutableListOf<ItemStack>()

        plugin.async(player) {
            val playerName = player.name
            plugin.logger.info("Fetching scores for $playerName (filtering for scoreboards: ${tradeSourceScoreboards()})")
            var scores = scoreApi.getInventoryRelatedScores(plugin.leagueCache, playerName)

            if (ensurePlayerHasPracticeShards(playerName, scores)) {
                // Update scores again to reflect new shard count
                scores = scoreApi.getInventoryRelatedScores(plugin.leagueCache, playerName)
            }

            scores = scores.filter { isInventoryRelatedScore(it) }
            val inventoryFilterRunType = getInventoryFilterForPlayer(this.plugin, this.configApi, playerName)
            if (inventoryFilterRunType != null) {
                scores = scores.map { score: Score ->
                    // If the run type is valid, but the score is not for that run type, set it to zero
                    if (score.slug != inventoryFilterRunType.longId) {
                        return@map score.copy(value = 0.toBigDecimal())
                    }

                    return@map score
                }
            }

            val scoreValues = scores.associate { ScoreRef(it.slug!!, it.key!!) to it.value!!.toInt() }
            scores
                .forEach { score ->
                    updatePlayerInventoryForState(
                        player,
                        knownItemTracker,
                        desiredItems,
                        inventoryFilterRunType,
                        scoreValues,
                        ScoreRef(score.slug!!, score.key!!),
                        score.value!!.toInt()
                    )
                }

            ensurePlayerInventoryReflectsItemsOutsideOfDeck(player, knownItemTracker, desiredItems, inventoryFilterRunType)
            syncAdvancements(player)

            // Clear old items FIRST so their slots are freed, then place the desired items
            // into those freed slots. Doing this in the reverse order left gaps.
            cleanUpOldItems(player, knownItemTracker)
            desiredItems.forEach { player.ensureInventoryContains(it) }
        }
    }

    private fun ensurePlayerInventoryReflectsItemsOutsideOfDeck(
        player: Player,
        knownItemTracker: MutableSet<ItemStack>,
        desiredItems: MutableList<ItemStack>,
        inventoryFilterRunType: RunType?
    ) {
        // For each card in the player's inventory, ensure they only have the correct amount
        val deckItems = inventoryApi.inventoryCardsGet(player = player.name, limit = 1000).results!!

        effectiveRunTypes().forEach { runType ->

            // Check cards against contents of player's deck
            cardConfig.sortedList().forEach { card ->
                var maxCardsThatShouldBeInInventory = deckItems.count {
                    it.name == card.shorthand && it.deckType == runType.deckType() && it.hiddenInDecks?.isNotEmpty() == true
                }

                if (inventoryFilterRunType != null && inventoryFilterRunType != runType) {
                    maxCardsThatShouldBeInInventory = 0
                }

                plugin.logger.fine("${player.name} should have ${maxCardsThatShouldBeInInventory}x${card.shorthand} in their inventory (runType: ${runType.longId})")
                val itemStack = createCard(plugin, null, card.shorthand, 1, runType.deckType())

                itemStack?.let {
                    want(
                        knownItemTracker,
                        desiredItems,
                        it.clone().apply { amount = zeroSupportedItemCount(maxCardsThatShouldBeInInventory) }
                    )
                }
            }

            // Check items against contents of player's deck
            intoDungeonItems.entries.forEach { (itemKey, scoreboardDescriber) ->
                var maxItemsThatShouldBeInInventory = deckItems.count {
                    it.name == itemKey && it.deckType == runType.deckType() && it.hiddenInDecks?.isNotEmpty() == true
                }

                if (inventoryFilterRunType != null && inventoryFilterRunType != runType) {
                    maxItemsThatShouldBeInInventory = 0
                }

                plugin.logger.fine("${player.name} should have ${maxItemsThatShouldBeInInventory}x${itemKey} in their inventory (runType: ${runType.longId})")
                val itemStack = scoreboardDescriber.itemStack(runType.longId, 1)

                itemStack.let {
                    want(
                        knownItemTracker,
                        desiredItems,
                        it.withTradeMeta(runType.longId, itemKey).clone()
                            .apply { amount = zeroSupportedItemCount(maxItemsThatShouldBeInInventory) }
                    )
                }
            }
        }

    }

    private fun ensurePlayerHasPracticeShards(playerName: String, scores: List<Score>): Boolean {
        tradeItems["SHARD"]?.let { tradeItem ->
            scores.find { score -> score.slug == PRACTICE_SLUG && score.key == tradeItem.sourceScoreboardName() }?.let { score ->
                if (score.value!!.toInt() <= 10) {
                    val trade = Trade(
                        runType = PRACTICE_SLUG,
                        sourceType = "dummy",
                        sourceItemCount = 0,
                        targetType = "SHARD",
                        targetItemCount = 64 - score.value!!.toInt(),
                    )

                    eventsApi.eventsPost(
                        Event(
                            name = "trade-requested",
                            server = plugin.serverName,
                            player = playerName,
                            count = 1,
                            x = 0.0,
                            y = 0.0,
                            z = 0.0,
                            metadata = mapOf(
                                "slug" to trade.runType,
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

    private fun updatePlayerInventoryForState(
        player: Player,
        knownItemTracker: MutableSet<ItemStack>,
        desiredItems: MutableList<ItemStack>,
        inventoryFilterRunType: RunType?,
        scores: Map<ScoreRef, Int>,
        score: ScoreRef,
        value: Int
    ) {
        val (slug, key) = score
        plugin.logger.info("Player ${player.name} has inventory score $slug/$key=$value")

        val runType = effectiveRunTypes().find { it.longId == slug }
        if (runType == null) {
            plugin.logger.warning("Ignoring score $slug/$key for ${player.name}: no league with that slug")
            return
        }

        baseTradeItems.forEach { it: Map.Entry<String, ScoreboardDescriber> ->
            val sb = it.value
            val sourceScoreKey = sb.sourceScoreboardName()
            if (sourceScoreKey == key) {
                plugin.logger.info("Score $slug/$key matches ${it.key}")

                var itemCount = value
                val inversionScoreKey = sb.sourceInversionScoreboardName()
                if (sourceScoreKey != inversionScoreKey) {
                    itemCount -= scores.getOrDefault(ScoreRef(slug, inversionScoreKey), 0)
                }

                want(
                    knownItemTracker,
                    desiredItems,
                    sb.itemStack(slug, itemCount).withTradeMeta(slug, it.key)
                )
            }
        }

        if (key == SHARDS_SCOREBOARD) {
            var itemCount = 1
            if (inventoryFilterRunType != null && inventoryFilterRunType != runType) {
                itemCount = 0
            }
            val materialOverride = try {
                configApi.configsGet(player.name, "shulker-style").value
                    ?.uppercase()
                    ?.let { Material.valueOf("${it}_SHULKER_BOX") }
            } catch (e: ClientException) {
                null
            }
            want(knownItemTracker, desiredItems, dungeonDeck(runType, itemCount, materialOverride))
            want(knownItemTracker, desiredItems, dungeonArtifacts(runType, itemCount))
        }

        want(knownItemTracker, desiredItems, menuBook())
        want(knownItemTracker, desiredItems, settingsBook())
        if (player.hasPermission("decked-out.book.admin")) {
            want(knownItemTracker, desiredItems, adminBook())
        }
    }

    // Record an item the player should end up with. Populates the cleanup-matching set
    // (knownItemTracker) and the ordered apply list (desiredItems). No inventory mutation
    // happens here — that is deferred to the apply phase so cleared slots can be reused.
    private fun want(
        knownItemTracker: MutableSet<ItemStack>,
        desiredItems: MutableList<ItemStack>,
        itemStack: ItemStack
    ) {
        knownItemTracker.add(itemStack)
        desiredItems.add(itemStack)
    }

    private fun cleanUpOldItems(player: Player, knownItemTracker: MutableSet<ItemStack>) {
        plugin.logger.info("Cleaning up old items for ${player.name}")

        val itemsToRemove = player.inventory.filter { inventoryItem ->
            inventoryItem?.hasSlug() == true && knownItemTracker.none { knownItem -> knownItem.isSimilar(inventoryItem) }
        }

        plugin.logger.info("Removing ${itemsToRemove.size} old items from ${player.name}'s inventory:")
        itemsToRemove.forEach { item ->
            plugin.logger.info(" - Removing old item: $item")
            player.inventory.remove(item!!)
        }

        plugin.logger.info("Finished cleaning up old items for ${player.name}")
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
            plugin.logger.info("Fetching advancement scores for $playerName from namespace '$ADVANCEMENT_SOURCE_SLUG'")
            val scores = scoreApi.scoresGet(
                player = playerName,
                slug = ADVANCEMENT_SOURCE_SLUG,
                prefixFilter = ADVANCEMENT_KEY_PREFIX,
                limit = 10000,
            )

            plugin.runOnNextTick {
                try {
                    // If this is the first time the player has logged in, we don't want to spam everyone with advancements
                    server.getAdvancement(NamespacedKey("do2", "visible/cards/get_deck_box"))
                        ?.let { advancement: Advancement ->
                            player.getAdvancementProgress(advancement).let { progress ->
                                if (!progress.isDone) {
                                    plugin.logger.info("Preventing advancement spam")
                                    player.world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false)
                                }
                            }
                        }

                    scores.results!!
                        .asSequence()
                        .filter { it.key!!.startsWith(ADVANCEMENT_KEY_PREFIX) }
                        .map { it.copy(key = it.key?.substring(ADVANCEMENT_KEY_PREFIX.length)) }
                        .filter { it.key!!.isNotBlank() && it.key!!.contains("#") }
                        .filter { it.key!!.contains("hidden") || it.key!!.contains("visible") }
                        .filter { it.value!!.toInt() > 0 }
                        .toList()
                        .forEach { score ->
                            // e.g. advancement-do2#hidden/survival/win_50_times#given_by_commands
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

// The scoreboards that drive the player's inventory. Their names are the same in
// every league: a league's scores live in its own namespace (the score's slug).
fun tradeSourceScoreboards() = baseTradeItems.values.flatMap {
    listOf(
        it.sourceScoreboardName(),
        it.sourceInversionScoreboardName(),
    )
}.filterNot { it.isBlank() }.distinct()

// Fetches the inventory scoreboards across all of the player's leagues. Each
// score carries the slug of the league it belongs to. Scores without a slug
// predate namespacing and belong to no league, so they are dropped here.
fun ScoreApi.getInventoryRelatedScores(leagueCache: LeagueCache, playerName: String): List<Score> {
    val knownSlugs = getRunTypesFromCache(leagueCache).map { it.longId }.toSet()
    return tradeSourceScoreboards().flatMap {
        this.scoresGet(playerName, prefixFilter = it).results!!
    }.filter { it.slug in knownSlugs }
}

fun isInventoryRelatedScore(score: Score): Boolean {
    return tradeSourceScoreboards().contains(score.key!!)
}

fun getInventoryFilterForPlayer(
    plugin: Citadel, configApi: ConfigApi, playerName: String
): RunType? {
    // The run-mode filter is the league SLUG, stored per-player in the
    // `filter-mode-slug` config. Defaults to do2-s2-comp when unset.
    val slug = try {
        configApi.configsGet(playerName, FILTER_MODE_SLUG_CONFIG).value
    } catch (e: Exception) {
        null
    }?.takeIf { it.isNotEmpty() } ?: DEFAULT_FILTER_MODE_SLUG

    val runType = plugin.leagueCache.getLeagueBySlug(slug)?.let { runTypeFromLeague(it) }
    if (runType == null) {
        plugin.logger.warning("Inventory filter slug '$slug' for $playerName did not resolve to a league")
    } else {
        plugin.logger.info("Inventory filter for $playerName resolved by slug '$slug' -> ${runType.longId}")
    }
    return runType
}
