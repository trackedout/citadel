package org.trackedout.citadel

import org.bukkit.GameRule
import org.bukkit.NamespacedKey
import org.bukkit.advancement.Advancement
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.commands.createCard
import org.trackedout.citadel.config.CatalogCache
import org.trackedout.citadel.config.CatalogData
import org.trackedout.citadel.inventory.ABSENT_ITEM_AMOUNT
import org.trackedout.citadel.inventory.Trade
import org.trackedout.citadel.inventory.adminBook
import org.trackedout.citadel.inventory.heldCount
import org.trackedout.citadel.inventory.lobbyEntryCount
import org.trackedout.citadel.inventory.lobbyEntrySpec
import org.trackedout.citadel.inventory.menuBook
import org.trackedout.citadel.inventory.settingsBook
import org.trackedout.citadel.inventory.toItemStack
import org.trackedout.citadel.inventory.tradeItemSpec
import org.trackedout.client.models.CatalogLobbyAction
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.apis.ScoreApi
import org.trackedout.client.apis.ConfigApi
import org.trackedout.client.infrastructure.ClientException
import org.trackedout.client.models.Event
import org.trackedout.client.models.Score
import org.trackedout.citadel.config.RunType
import org.trackedout.citadel.config.LeagueCache
import org.trackedout.citadel.config.getRunTypesFromCache
import org.trackedout.data.sortedList

// Name of the shard balance scoreboard. Same in every league namespace.
const val SHARDS_SCOREBOARD = "do2.inventory.shards"

// The trade id of shards, which practice players are topped up with
private const val SHARD_TRADE_ID = "SHARD"

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

            // The player only holds the items of their run mode's league, if they have one
            val runMode = plugin.runModes.current(playerName)
            val runTypes = effectiveRunTypes().filter { runMode == null || it == runMode }
            val unloaded = runTypes.map { it.longId }.filter { plugin.catalogCache.get(it) == null }
            if (unloaded.isNotEmpty()) {
                // Syncing without a catalog would clean up the player's items, so leave them as they are
                plugin.logger.severe("Skipping the inventory sync of $playerName, the catalogs of $unloaded have not loaded")
                return@async
            }

            plugin.logger.info("Fetching scores for $playerName (filtering for scoreboards: ${catalogs.inventoryScoreboards()})")
            var scores = scoreApi.getInventoryRelatedScores(plugin.leagueCache, catalogs, playerName)

            if (ensurePlayerHasPracticeShards(playerName, scores)) {
                // Update scores again to reflect new shard count
                scores = scoreApi.getInventoryRelatedScores(plugin.leagueCache, catalogs, playerName)
            }

            scores = scores.filter { isInventoryRelatedScore(catalogs, it) }
            val scoreValues = scores.associate { ScoreRef(it.slug!!, it.key!!) to it.value!!.toInt() }
            scores.forEach { plugin.logger.info("Player $playerName has inventory score ${it.slug}/${it.key}=${it.value}") }

            runTypes.forEach { runType ->
                val catalog = catalogs.require(runType.longId)
                want(knownItemTracker, desiredItems, lobbyItems(player, catalog, scoreValues))
                want(knownItemTracker, desiredItems, scoredTradeItems(catalog, scoreValues))
            }

            want(knownItemTracker, desiredItems, listOf(menuBook(), settingsBook()))
            if (player.hasPermission("decked-out.book.admin")) {
                want(knownItemTracker, desiredItems, listOf(adminBook()))
            }

            ensurePlayerInventoryReflectsItemsOutsideOfDeck(player, knownItemTracker, desiredItems, runTypes)
            syncAdvancements(player)

            // Clear old items FIRST so their slots are freed, then place the desired items
            // into those freed slots. Doing this in the reverse order left gaps.
            cleanUpOldItems(player, knownItemTracker)
            desiredItems.forEach { player.ensureInventoryContains(it) }
        }
    }

    private val catalogs: CatalogCache get() = plugin.catalogCache

    // A league's lobby is vended once the player is seeded for it: they have every score its entries count.
    // A league whose lobby counts no score, like a minigame's, needs no seeding
    private fun lobbyItems(player: Player, catalog: CatalogData, scores: Map<ScoreRef, Int>): List<ItemStack> {
        val missingScores = catalog.lobbyCountScoreboards().filter { ScoreRef(catalog.slug, it) !in scores }
        if (missingScores.isNotEmpty()) {
            plugin.logger.info("${player.name} is not seeded for ${catalog.slug} yet (no score for $missingScores), not vending its lobby")
            return emptyList()
        }

        return catalog.lobby.mapIndexed { index, entry ->
            val count = lobbyEntryCount(entry) { scores.getValue(ScoreRef(catalog.slug, it)) }
            lobbyEntrySpec(catalog.slug, index, entry, catalog.tradeItemOf(entry), count, materialOverrideOf(player, entry.action)).toItemStack()
        }
    }

    // The player's own choice of material for an entry, when the entry lets them choose
    private fun materialOverrideOf(player: Player, action: CatalogLobbyAction): String? = when (action.playerColour) {
        CatalogLobbyAction.PlayerColour.shulkerMinusStyle -> shulkerStyleOverride(player)
        null -> null
    }

    // Items shown by how much the player has earned of them, except those a lobby entry already vends
    private fun scoredTradeItems(catalog: CatalogData, scores: Map<ScoreRef, Int>): List<ItemStack> =
        catalog.scoredTradeItems
            .filter { catalog.lobbyEntryFor(it) == null }
            .map { item ->
                val count = item.heldCount { scores[ScoreRef(catalog.slug, it)] }
                tradeItemSpec(catalog.slug, item, count).toItemStack()
            }

    private fun ensurePlayerInventoryReflectsItemsOutsideOfDeck(
        player: Player,
        knownItemTracker: MutableSet<ItemStack>,
        desiredItems: MutableList<ItemStack>,
        runTypes: List<RunType>,
    ) {
        // For each card in the player's inventory, ensure they only have the correct amount
        val deckItems = inventoryApi.inventoryCardsGet(player = player.name, limit = 1000).results!!

        runTypes.forEach { runType ->
            val catalog = catalogs.require(runType.longId)
            fun inDeck(name: String) = deckItems.count {
                it.name == name && it.deckType == runType.deckType() && it.hiddenInDecks?.isNotEmpty() == true
            }

            // Check cards against contents of player's deck
            catalog.cards.sortedList().forEach { card ->
                val cardsThatShouldBeInInventory = inDeck(card.shorthand)
                plugin.logger.fine("${player.name} should have ${cardsThatShouldBeInInventory}x${card.shorthand} in their inventory (runType: ${runType.longId})")

                val itemStack = createCard(catalog, card.shorthand, 1, runType.deckType())
                want(knownItemTracker, desiredItems, listOf(itemStack.apply { amount = zeroSupportedItemCount(cardsThatShouldBeInInventory) }))
            }

            // Check items against contents of player's deck
            catalog.intoDungeonItems.forEach { item ->
                val itemsThatShouldBeInInventory = inDeck(item.tradeId)
                plugin.logger.fine("${player.name} should have ${itemsThatShouldBeInInventory}x${item.tradeId} in their inventory (runType: ${runType.longId})")

                want(knownItemTracker, desiredItems, listOf(tradeItemSpec(runType.longId, item, itemsThatShouldBeInInventory).toItemStack()))
            }
        }
    }

    private fun ensurePlayerHasPracticeShards(playerName: String, scores: List<Score>): Boolean {
        val practiceSlug = plugin.serverSettings.leagues()?.practice ?: return false
        val catalog = catalogs.get(practiceSlug)
        if (catalog == null) {
            plugin.logger.severe("Not topping up $playerName's practice shards, the $practiceSlug catalog has not loaded")
            return false
        }

        catalog.tradeItems[SHARD_TRADE_ID]?.let { tradeItem ->
            scores.find { score -> score.slug == practiceSlug && score.key == tradeItem.sourceScoreboard }?.let { score ->
                if (score.value!!.toInt() <= 10) {
                    val trade = Trade(
                        runType = practiceSlug,
                        sourceType = "dummy",
                        sourceItemCount = 0,
                        targetType = SHARD_TRADE_ID,
                        targetItemCount = 64 - score.value!!.toInt(),
                        catalog = catalog,
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

    private fun zeroSupportedItemCount(count: Int) = if (count <= 0) ABSENT_ITEM_AMOUNT else count

    // The player's chosen competitive shulker colour, null when they haven't picked one
    private fun shulkerStyleOverride(player: Player): String? =
        try {
            configApi.configsGet(player.name, "shulker-style").value
                ?.uppercase()
                ?.let { "${it}_SHULKER_BOX" }
        } catch (e: ClientException) {
            null
        }

    // Record the items the player should end up with. Populates the cleanup-matching set
    // (knownItemTracker) and the ordered apply list (desiredItems). No inventory mutation
    // happens here, that is deferred to the apply phase so cleared slots can be reused.
    private fun want(
        knownItemTracker: MutableSet<ItemStack>,
        desiredItems: MutableList<ItemStack>,
        itemStacks: List<ItemStack>,
    ) {
        knownItemTracker.addAll(itemStacks)
        desiredItems.addAll(itemStacks)
    }

    private fun cleanUpOldItems(player: Player, knownItemTracker: MutableSet<ItemStack>) {
        plugin.logger.info("Cleaning up old items for ${player.name}")

        val itemsToRemove = player.inventory.filter { inventoryItem ->
            inventoryItem?.isManagedDungeonItem() == true && knownItemTracker.none { knownItem -> knownItem.isSimilar(inventoryItem) }
        }

        plugin.logger.info("Removing ${itemsToRemove.size} old items from ${player.name}'s inventory:")
        itemsToRemove.forEach { item ->
            plugin.logger.info(" - Removing old item: $item")
            player.inventory.remove(item!!)
        }

        plugin.logger.info("Finished cleaning up old items for ${player.name}")
    }

    private fun Player.ensureInventoryContains(itemStack: ItemStack) {
        val targetAmount = if (itemStack.amount == ABSENT_ITEM_AMOUNT) 0 else itemStack.amount
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

        // Lobby advancements mirror what the player earned in the settings' advancements league
        val advancementSlug = plugin.serverSettings.leagues()?.advancements ?: run {
            plugin.logger.warning("Settings are not loaded, skipping advancements of $playerName")
            return
        }
        try {
            plugin.logger.info("Fetching advancement scores for $playerName from namespace '$advancementSlug'")
            val scores = scoreApi.scoresGet(
                player = playerName,
                slug = advancementSlug,
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

// Fetches the inventory scoreboards across all of the player's leagues. Each
// score carries the slug of the league it belongs to. Scores without a slug
// predate namespacing and belong to no league, so they are dropped here.
fun ScoreApi.getInventoryRelatedScores(leagueCache: LeagueCache, catalogs: CatalogCache, playerName: String): List<Score> {
    val knownSlugs = getRunTypesFromCache(leagueCache).map { it.longId }.toSet()
    return catalogs.inventoryScoreboards().flatMap {
        this.scoresGet(playerName, prefixFilter = it).results!!
    }.filter { it.slug in knownSlugs }
}

fun isInventoryRelatedScore(catalogs: CatalogCache, score: Score): Boolean {
    return catalogs.inventoryScoreboards().contains(score.key!!)
}
