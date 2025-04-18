package org.trackedout.citadel.listeners

import co.aikar.commands.BaseCommand
import com.mongodb.client.model.Accumulators
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.gte
import com.mongodb.client.model.Filters.or
import me.devnatan.inventoryframework.ViewFrame
import net.kyori.adventure.text.TextComponent
import org.apache.logging.log4j.util.TriConsumer
import org.bson.Document
import org.bukkit.Material
import org.bukkit.Nameable
import org.bukkit.Sound
import org.bukkit.block.Barrel
import org.bukkit.block.BlockState
import org.bukkit.block.ShulkerBox
import org.bukkit.block.TileState
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.InventoryManager
import org.trackedout.citadel.async
import org.trackedout.citadel.debug
import org.trackedout.citadel.getCard
import org.trackedout.citadel.getDeckId
import org.trackedout.citadel.hasDeckId
import org.trackedout.citadel.inventory.DeckId
import org.trackedout.citadel.inventory.DeckInventoryViewWithoutBack
import org.trackedout.citadel.inventory.DeckManagementView.Companion.ADD_CARD_FUNC
import org.trackedout.citadel.inventory.DeckManagementView.Companion.DECK_MAP
import org.trackedout.citadel.inventory.DeckManagementView.Companion.DELETE_CARD_FUNC
import org.trackedout.citadel.inventory.DeckManagementView.Companion.JOIN_QUEUE_FUNC
import org.trackedout.citadel.inventory.DeckManagementView.Companion.PLAYER_NAME
import org.trackedout.citadel.inventory.DeckManagementView.Companion.PLUGIN
import org.trackedout.citadel.inventory.DeckManagementView.Companion.SELECTED_DECK
import org.trackedout.citadel.inventory.DeckManagementView.Companion.UPDATE_CARD_VISIBILITY_FUNC
import org.trackedout.citadel.inventory.ShopView
import org.trackedout.citadel.inventory.ShopView.Companion.SHOP_NAME
import org.trackedout.citadel.inventory.ShopView.Companion.SHOP_RULES
import org.trackedout.citadel.inventory.ShopView.Companion.SHOP_RULES_REGEX
import org.trackedout.citadel.inventory.ShopView.Companion.TRADE_FUNC
import org.trackedout.citadel.inventory.ShopView.Companion.UPDATE_INVENTORY_FUNC
import org.trackedout.citadel.inventory.Trade
import org.trackedout.citadel.inventory.isPractice
import org.trackedout.citadel.inventory.shortRunType
import org.trackedout.citadel.isDeckedOutCard
import org.trackedout.citadel.isDeckedOutShulker
import org.trackedout.citadel.mongo.MongoDBManager
import org.trackedout.citadel.sendRedMessage
import org.trackedout.citadel.shop.getShopData
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.models.Card
import org.trackedout.client.models.Event
import org.trackedout.data.RunType
import org.trackedout.data.runTypes
import java.text.SimpleDateFormat
import java.util.function.BiConsumer
import java.util.function.Consumer
import kotlin.math.pow

class EchoShardListener(
    private val plugin: Citadel,
    private val inventoryApi: InventoryApi,
    private val eventsApi: EventsApi,
    private val viewFrame: ViewFrame,
    private val inventoryManager: InventoryManager,
) : BaseCommand(), Listener {

    @EventHandler(ignoreCancelled = true)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val player = event.player
        if (player !is Player) {
            plugin.logger.info("Not a player")
            return
        }

        event.inventory.location?.let { location ->
            if (location.block.state is Barrel || location.block.state is ShulkerBox) {
                val block = (location.block.state as Nameable)
                block.customName()?.let { customName ->
                    val title = (customName as TextComponent).content()

                    if (title.startsWith("Shop ")) {
                        event.isCancelled = true
                        playSoundForOpeningBlock(location.block.state, player)

                        val titleComponents = title.removePrefix("Shop ").split(" ")
                        val shopName = titleComponents.filter { !it.matches(SHOP_RULES_REGEX) }.joinToString(" ")
                        (block as? TileState)?.let {
                            val shopData = it.getShopData(plugin)
                            if (shopData.disabled) {
                                player.sendRedMessage("This shop is not currently open")
                            } else {

                                val prices = runTypes.associateWith { runType -> 10 }.toMutableMap() // Default price is 10 for all run types
                                // Get player overrides for shop trades
                                for (runType in prices.keys) {
                                    val crownTrade = "${runType.shortId}CROWNx10=${runType.shortId}SHARDx1"
                                    if (shopData.trades.contains(crownTrade)) {
                                        shopData.name = "{p}p/{c}c/{h}h Crowns"
                                        getCostForCrownTrade(plugin, player.name, runType)?.let { cost ->
                                            shopData.trades -= crownTrade
                                            val updatedTrade = "${runType.shortId}CROWNx${cost}=${runType.shortId}SHARDx1"
                                            shopData.trades += updatedTrade
                                            prices[runType] = cost
                                        }
                                    }
                                }

                                for (runType in prices.keys) {
                                    shopData.name = shopData.name.replace("{${runType.shortId}}", prices[runType].toString())
                                }

                                showShopView(player, shopData.name.ifEmpty { shopName }, shopData.trades)
                            }
                        } ?: throw Exception("Target block is not of type TileState")
                    }
                }
            }
        }
    }

    private fun playSoundForOpeningBlock(state: BlockState, player: Player) {
        when (state) {
            is Barrel -> {
                player.playSound(player.location, Sound.BLOCK_BARREL_OPEN, 1f, 1f)
            }

            is ShulkerBox -> {
                player.playSound(player.location, Sound.BLOCK_SHULKER_BOX_OPEN, 1f, 1f)
            }

            else -> {
                player.playSound(player.location, Sound.BLOCK_CHEST_OPEN, 1f, 1f)
            }
        }
    }

    private fun showShopView(player: Player, shopName: String, shopRules: List<String>) {
        player.debug("Shop rules: $shopRules", "debug.shop")

        val performTradeFunc = TriConsumer<Trade, () -> Unit, () -> Unit> { trade, doneFunc, successFunc ->
            plugin.async(player) {
                try {
                    eventsApi.eventsPost(
                        Event(
                            name = "trade-requested", server = plugin.serverName, player = player.name, count = 1, x = player.x, y = player.y, z = player.z, metadata = mapOf(
                                "run-type" to trade.runType,
                                "source-scoreboard" to trade.sourceScoreboardName(),
                                "source-inversion-scoreboard" to trade.sourceInversionScoreboardName(),
                                "source-count" to trade.sourceItemCount.toString(),
                                "target-scoreboard" to trade.targetScoreboardName(),
                                "target-count" to trade.targetItemCount.toString(),
                            )
                        )
                    )

                    successFunc()
                    doneFunc()
                } catch (e: Exception) {
                    doneFunc()
                    throw e
                }
            }
        }

        val addCardFunc = BiConsumer<String, Card> { deckType, card ->
            plugin.async(player) {
                inventoryApi.inventoryAddCardPost(
                    Card(
                        player = card.player,
                        name = card.name,
                        deckType = deckType,
                        server = plugin.serverName,
                    )
                )
            }
        }

        val deleteCardFunc = BiConsumer<String, Card> { deckType, card ->
            plugin.async(player) {
                inventoryApi.inventoryDeleteCardPost(
                    Card(
                        player = card.player,
                        name = card.name,
                        deckType = deckType,
                        server = plugin.serverName,
                    )
                )
            }
        }

        val updateInventoryFunc = Consumer<Player> {
            inventoryManager.updateInventoryBasedOnScore(it)
        }

        val joinQueueFunc = createJoinQueueFunc(plugin, eventsApi, player)

        val context = mutableMapOf(
            PLUGIN to plugin,
            SHOP_NAME to shopName,
            SHOP_RULES to shopRules,
            TRADE_FUNC to performTradeFunc,
            ADD_CARD_FUNC to addCardFunc,
            DELETE_CARD_FUNC to deleteCardFunc,
            UPDATE_INVENTORY_FUNC to updateInventoryFunc,
            JOIN_QUEUE_FUNC to joinQueueFunc,
        )
        viewFrame.open(ShopView::class.java, player, context)
    }

    @EventHandler(ignoreCancelled = true)
    fun onDropItem(event: PlayerDropItemEvent) {
        val player = event.player
        player.debug(
            "Drop item event: ${event.eventName} - inventoryType=${event.itemDrop.type}, " + "cursorItem=${event.itemDrop.type.name}"
        )

        val item = event.itemDrop.itemStack
        if (isPracticeCard(item)) {
            event.isCancelled = true
            player.debug("Replacing item drop event with card delete (for ${item.amount}x practice card)")
            deleteCard(player, item.clone())
            item.amount = 0
        } else if (isRestrictedItem(item)) {
            player.debug("Blocking item drop event")
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onDragItem(event: InventoryDragEvent) {
        val player = event.view.player
        player.debug(
            "Drag event: ${event.type.name} - inventoryType=${event.inventory.type.name}, " + "inventorySize=${event.inventory.size}, " + "view=${event.view.type.name}, " + "cursorItem=${event.cursor?.type?.name}, " + "oldItem=${event.oldCursor.type.name}"
        )
        val slotTypes = event.rawSlots.map { it to event.view.getSlotType(it).name }
        player.debug("Slot types: $slotTypes")

        // Allow all slots if the target inventory is the player's inventory.
        // Otherwise, block any of the non-player inventory slots from being involved.
        val blockSlotsBelow = if (isPlayerInventory(event.inventory, event.view)) 0 else event.inventory.size
        player.debug("Blocking slots < $blockSlotsBelow")

        if ((!isPlayerInventory(event.inventory, event.view)) && isRestrictedItem(event.oldCursor) && event.rawSlots.any { it < blockSlotsBelow }) {
            player.debug("Blocking drag event")
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = false)
    fun onRightClickShulker(event: PlayerInteractEvent) {
        val player = event.player

        val item = event.item
        player.debug(
            "Interact event: { " + "eventType=${event.action}, " + "openInventoryType=${player.openInventory.type}, " + "currentItem=${event.clickedBlock?.type}, " + "currentItem=${item?.type?.name}, " + "offHandItem=${player.inventory.itemInOffHand.type.name} }",
            "debug.interact"
        )

        if (item == null) {
            return
        }

        if (event.action == Action.RIGHT_CLICK_AIR) {
            item.getDeckId()?.let {
                if (item.isDeckedOutShulker() || item.isDeckedOutCard()) {
                    showDeckInventory(event, player, it)
                }
            }
        }

        // Cancel interaction events for restricted items
        if ((event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK)) {
            if (isRestrictedItem(item) && !player.scoreboardTags.contains("nocheck")) {
                event.setUseItemInHand(org.bukkit.event.Event.Result.DENY)
                player.debug("Preventing item consumption on interact event ${event.action}")
            }
        }
    }

    private fun showDeckInventory(event: PlayerInteractEvent, player: Player, deckId: String): String? {
        event.isCancelled = true

        // TODO: Make this async
        val allCards = inventoryApi.inventoryCardsGet(player = player.name, limit = 200).results!!

        val updateCardVisibility = BiConsumer<DeckId, Map<String, Number>> { deckIdToUpdate, cardsToHide ->
            eventsApi.eventsPost(
                Event(
                    name = "card-visibility-updated", server = plugin.serverName, player = player.name, count = 1, x = player.x, y = player.y, z = player.z, metadata = mapOf(
                        "run-type" to deckIdToUpdate.shortRunType(),
                        "deck-id" to deckIdToUpdate,
                    ).plus(cardsToHide.map { "hide-card-${it.key}" to it.value.toString() })
                )
            )
        }

        val addCardFunc = BiConsumer<DeckId, Card> { deckId, card ->
            plugin.async(player) {
                inventoryApi.inventoryAddCardPost(
                    Card(
                        player = card.player,
                        name = card.name,
                        deckType = deckId[0].toString(),
                        server = plugin.serverName,
                    )
                )
            }
        }

        val deleteCardFunc = BiConsumer<DeckId, Card> { deckId, card ->
            plugin.async(player) {
                inventoryApi.inventoryDeleteCardPost(
                    Card(
                        player = card.player,
                        name = card.name,
                        deckType = deckId[0].toString(),
                        server = plugin.serverName,
                    )
                )
            }
        }

        val context = mutableMapOf(
            PLUGIN to plugin,
            PLAYER_NAME to player.name,
            DECK_MAP to allCards.groupBy { it.deckType!! },
            SELECTED_DECK to deckId,
            UPDATE_CARD_VISIBILITY_FUNC to updateCardVisibility,
            ADD_CARD_FUNC to addCardFunc,
            DELETE_CARD_FUNC to deleteCardFunc,
        )

        player.playSound(player.location, Sound.BLOCK_SHULKER_BOX_OPEN, 1f, 1f)
        return viewFrame.open(DeckInventoryViewWithoutBack::class.java, player, context)
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryInteract(event: InventoryClickEvent) {
        val player = event.whoClicked as Player

        player.debug(
            "Click event: { " + "eventType=${event.action}, " + "hotbarButton=${event.hotbarButton}, " + "inventoryType=${event.inventory.type}, " + "openInventoryType=${player.openInventory.type}, " + "clickedInventoryType=${event.clickedInventory?.type}, " + "cursor=${event.cursor.type.name}, " + "currentItem=${event.currentItem?.type?.name}, " + "offHandItem=${player.inventory.itemInOffHand.type.name} }"
        )

        if (player.scoreboardTags.contains("nocheck")) {
            player.debug("Skipping inventory checks as player has 'nocheck' tag")
            return
        }

        /* Actions to prevent:
        Press Q:
        { eventType=DROP_ONE_SLOT, inventoryType=CRAFTING, openInventoryType=CRAFTING, clickedInventoryType=PLAYER, topInventoryType=CRAFTING, bottomInventoryType=PLAYER, cursor=AIR, currentItem=IRON_NUGGET }

        Press F (hotbar swap):
        { eventType=HOTBAR_SWAP, inventoryType=CHEST, openInventoryType=CHEST, clickedInventoryType=CHEST, topInventoryType=CHEST, bottomInventoryType=PLAYER, cursor=AIR, currentItem=AIR }

        Press F (hotbar swap) but with item already in hotbar:
        { eventType=HOTBAR_MOVE_AND_READD, inventoryType=BARREL, openInventoryType=BARREL, clickedInventoryType=BARREL, topInventoryType=BARREL, bottomInventoryType=PLAYER, cursor=AIR, currentItem=IRON_NUGGET }

        Click to pickup, then this is the place event:
        { eventType=PLACE_ALL, inventoryType=CHEST, openInventoryType=CHEST, clickedInventoryType=PLAYER, topInventoryType=CHEST, bottomInventoryType=PLAYER, cursor=IRON_NUGGET, currentItem=AIR }

        Same, but with right click to place:
        { eventType=PLACE_ONE, inventoryType=CHEST, openInventoryType=CHEST, clickedInventoryType=CHEST, topInventoryType=CHEST, bottomInventoryType=PLAYER, cursor=IRON_NUGGET, currentItem=AIR }

        Shift click:
        { eventType=MOVE_TO_OTHER_INVENTORY, inventoryType=CHEST, openInventoryType=CHEST, clickedInventoryType=PLAYER, topInventoryType=CHEST, bottomInventoryType=PLAYER, cursor=AIR, currentItem=IRON_NUGGET }

        Right click with item in hand:
        { eventType=PLACE_ONE, inventoryType=CHEST, openInventoryType=CHEST, clickedInventoryType=CHEST, topInventoryType=CHEST, bottomInventoryType=PLAYER, cursor=IRON_NUGGET, currentItem=AIR }

        Right click on stack:
        { eventType=PICKUP_HALF, inventoryType=CHEST, openInventoryType=CHEST, clickedInventoryType=PLAYER, topInventoryType=CHEST, bottomInventoryType=PLAYER, cursor=AIR, currentItem=IRON_NUGGET }

        Drag place:
        ???
        */

        val actionsToBlockRegardlessOfInventoryType = listOf(
            InventoryAction.DROP_ONE_SLOT,
            InventoryAction.DROP_ALL_SLOT,
            InventoryAction.DROP_ONE_CURSOR,
            InventoryAction.DROP_ALL_CURSOR,
        )
        if (event.action !in actionsToBlockRegardlessOfInventoryType && isPlayerInventory(event.clickedInventory, event.view) && isPlayerInventory(event.inventory, event.view)) {
            return
        }

        val itemsToCheck = mutableListOf<ItemStack>()

        val allowForPlayerInventory = listOf(
            InventoryAction.PICKUP_ALL,
            InventoryAction.PICKUP_ONE,
            InventoryAction.PICKUP_HALF,
            InventoryAction.PICKUP_SOME,
            InventoryAction.SWAP_WITH_CURSOR,
            InventoryAction.PLACE_ALL,
            InventoryAction.PLACE_SOME,
            InventoryAction.PLACE_ONE,
        )
        if (!(event.action in allowForPlayerInventory && isPlayerInventory(event.clickedInventory, event.view))) {
            event.currentItem?.let { itemsToCheck.add(it) }
        }
        if (!(event.action in allowForPlayerInventory && isPlayerInventory(event.clickedInventory, event.view))) {
            itemsToCheck.add(event.cursor)
        }

        if (event.action === InventoryAction.HOTBAR_SWAP || event.action === InventoryAction.HOTBAR_MOVE_AND_READD) {
            if (event.hotbarButton >= 0) {
                player.debug("Hotbar item: ${player.inventory.getItem(event.hotbarButton)?.type?.name}")
                player.inventory.getItem(event.hotbarButton)?.let { itemsToCheck.add(it) }
            } else {
                itemsToCheck.add(player.inventory.itemInOffHand)
            }
        }

        itemsToCheck.removeIf { it.type == Material.AIR }

        val anyItemMatches = itemsToCheck.any { isRestrictedItem(it) }
        if (anyItemMatches) {
            if (event.action in actionsToBlockRegardlessOfInventoryType && itemsToCheck.all { isPracticeCard(it) }) {
                // Allow players to drop practice cards, but just delete it
                player.debug("Allowing click action ${event.action} for practice card")
            } else {
                player.debug("Blocking click action ${event.action}")
                event.isCancelled = true
            }
        }
    }

    private fun deleteCard(player: Player, it: ItemStack) {
        val deckType = it.getDeckId()?.shortRunType()
        val card = it.getCard()

        if (deckType == null || card == null) {
            plugin.logger.warning("Cannot delete card for item stack: $it (either deckType or resolved Card type is null)")
            return
        }

        plugin.async(player) {
            for (i in 1..it.amount) {
                plugin.logger.info("Deleting ${card.shorthand} (loop iteration ${i})")
                inventoryApi.inventoryDeleteCardPost(
                    Card(
                        player = player.name,
                        name = card.shorthand,
                        deckType = deckType,
                        server = plugin.serverName,
                    )
                )
            }
        }
    }

    private fun isPlayerInventory(inventory: Inventory?, view: InventoryView): Boolean {
        return inventory?.type == InventoryType.PLAYER || inventory?.type == InventoryType.CRAFTING || isTradeInventory(view)
    }

    private fun isTradeInventory(view: InventoryView): Boolean {
        if (view.type != InventoryType.CHEST) {
            return false
        }

        if (view.title() is TextComponent) {
            val title = (view.title() as TextComponent?)?.content() ?: return false
            return title.contains("Citadel: ") || title.contains("Frozen Assets")
        }
        return false
    }

    private fun isRestrictedItem(it: ItemStack) = it.itemMeta != null && it.hasDeckId()

    private fun isPracticeCard(it: ItemStack) = isRestrictedItem(it) && it.isDeckedOutCard() && it.getDeckId()?.isPractice() == true
}

fun getCostForCrownTrade(plugin: Citadel, playerName: String, runType: RunType): Int? {
    return if (runType.shortId == 'h') {
        getHardcoreShardCost(plugin, playerName)
    } else {
        getStandardShardCost(plugin, playerName, runType)
    }
}

private fun getHardcoreShardCost(plugin: Citadel, playerName: String): Int? {
    val database = MongoDBManager.getDatabase("dunga-dunga")
    val scoresCollection = database.getCollection("scores")
    val claimsCollection = database.getCollection("claims")

    // Hardcore shards bought = 10 - (do2.inventory.shards.hardcore + hardcore-do2.runs)
    // First we count the number of hardcore shards the player has / has used
    val filter = Filters.and(
        eq("player", playerName),
        or(
            eq("key", "hardcore-do2.runs"),
            eq("key", "do2.inventory.shards.hardcore")
        ),
        gte("value", 0)
    )
    val group = Aggregates.group(
        "\$player", Accumulators.sum("shardsForThisStreak", Document("\$toInt", "\$value"))
    )

    val doc = scoresCollection.aggregate(
        listOf(
            Aggregates.match(filter), group
        )
    ).firstOrNull()

    // If the player has an active claim, count that as a run
    val activeClaimCount = claimsCollection.countDocuments(
        Filters.and(
            eq("player", playerName),
            eq("type", "dungeon"),
            or(
                eq("state", "pending"),
                eq("state", "acquired"),
                eq("state", "in-use"),
            )
        )
    ).toInt()

    if (doc != null) {
        println(doc)
        val shardsForThisStreak = doc["shardsForThisStreak"].toString().toInt() + activeClaimCount
        plugin.logger.info("Total hardcore shards for this streak: $shardsForThisStreak")
        plugin.logger.info("Cost for hardcore shards: $shardsForThisStreak")
        return shardsForThisStreak
    } else {
        plugin.logger.warning("No matching documents found, not overriding formula")
    }

    return null
}

private fun getStandardShardCost(plugin: Citadel, playerName: String, runType: RunType): Int? {
    val database = MongoDBManager.getDatabase("dunga-dunga")
    val eventsCollection = database.getCollection("events")

    // Lobby is running on UTC time
    val phase7StartDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse("2025-03-01T16:00:00")

    val fullRunType = runType.longId
    val filter = Filters.and(
        eq("name", "trade-requested"),
        eq("player", playerName),
        eq("metadata.run-type", fullRunType),
        eq("metadata.source-scoreboard", "$fullRunType-do2.lifetime.escaped.crowns"),
        eq("metadata.target-scoreboard", "do2.inventory.shards.$fullRunType"),
        gte("createdAt", phase7StartDate)
    )

    val group = Aggregates.group(
        "\$player", Accumulators.sum("totalShardsBought", Document("\$toInt", "\$metadata.target-count"))
    )
//    println(group.toBsonDocument())

    val doc = eventsCollection.aggregate(
        listOf(
            Aggregates.match(filter), group
        )
    ).firstOrNull()

    if (doc != null) {
        println(doc)
        val shardsBought = doc["totalShardsBought"].toString().toInt()
        plugin.logger.info("Total $fullRunType shards bought: $shardsBought")
        val cost = 10 + (shardsBought.toDouble().pow(2.0)).toInt()
        plugin.logger.info("Cost for $fullRunType shards: $cost")
        return cost
    } else {
        plugin.logger.warning("No matching documents found, not overriding formula")
    }

    return null
}
