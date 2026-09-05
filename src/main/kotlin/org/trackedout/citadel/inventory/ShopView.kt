package org.trackedout.citadel.inventory

import me.devnatan.inventoryframework.BukkitViewContainer
import me.devnatan.inventoryframework.View
import me.devnatan.inventoryframework.ViewConfigBuilder
import me.devnatan.inventoryframework.context.CloseContext
import me.devnatan.inventoryframework.context.OpenContext
import me.devnatan.inventoryframework.state.MutableIntState
import me.devnatan.inventoryframework.state.State
import org.apache.logging.log4j.util.TriConsumer
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.config.LeagueItemResolver
import org.trackedout.citadel.config.cardConfig
import org.trackedout.citadel.inventory.DeckManagementView.Companion.ADD_CARD_FUNC
import org.trackedout.citadel.inventory.DeckManagementView.Companion.DELETE_CARD_FUNC
import org.trackedout.citadel.inventory.DeckManagementView.Companion.JOIN_QUEUE_FUNC
import org.trackedout.citadel.inventory.DeckManagementView.Companion.PLUGIN
import org.trackedout.client.models.League
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendRedMessage
import org.trackedout.client.models.Card
import org.trackedout.data.find
import org.trackedout.citadel.config.getRunTypeByIdFromCache
import org.trackedout.citadel.config.getRunTypesFromCache
import org.trackedout.citadel.config.unknownRunType
import java.util.function.BiConsumer
import java.util.function.Consumer

const val SHOP_TITLE_FORMAT = "Citadel: %s"

data class Trade(
    val runType: String,
    val sourceType: String,
    val sourceItemCount: Int,
    val targetType: String,
    val targetItemCount: Int,
    val league: League? = null,
) {
    fun sourceScoreboardName(): String {
        league?.let { l ->
            LeagueItemResolver.sourceScoreboardName(l, sourceType.uppercase())?.let { return it }
        }
        return tradeItemsWithQueueTypes.getOrElse(sourceType.uppercase()) {
            throw Exception("Unknown source type '$sourceType', unable to determine source scoreboard")
        }.sourceScoreboardName(this.runType)
    }

    fun sourceInversionScoreboardName(): String {
        league?.let { l ->
            LeagueItemResolver.sourceInversionScoreboardName(l, sourceType.uppercase())?.let { return it }
        }
        return tradeItemsWithQueueTypes.getOrElse(sourceType.uppercase()) {
            throw Exception("Unknown source type '$sourceType', unable to determine source inversion scoreboard")
        }.sourceInversionScoreboardName(this.runType)
    }

    fun targetScoreboardName(): String {
        league?.let { l ->
            LeagueItemResolver.targetScoreboardName(l, targetType.uppercase())?.let { return it }
        }
        return tradeItemsWithQueueTypes.getOrElse(targetType.uppercase()) {
            throw Exception("Unknown target type '$targetType', unable to determine target scoreboard")
        }.targetScoreboardName(this.runType)
    }
}

class ShopView : View() {
    private val plugin: State<Citadel> = initialState(PLUGIN)
    private val shopName: State<String> = initialState(SHOP_NAME)
    private val shopRules: State<List<String>> = initialState(SHOP_RULES)
    private val tradeFunc: State<TriConsumer<Trade, () -> Unit, () -> Unit>> = initialState(TRADE_FUNC)
    private val updateInventoryFunc: State<Consumer<Player>> = initialState(UPDATE_INVENTORY_FUNC)
    private val joinQueueFunc: State<Consumer<String>> = initialState(JOIN_QUEUE_FUNC)
    private val addCardFunc: State<BiConsumer<String, Card>> = initialState(ADD_CARD_FUNC)
    private val deleteCardFunc: State<BiConsumer<String, Card>> = initialState(DELETE_CARD_FUNC)
    private val successfulTrades: MutableIntState = mutableState(0)

    companion object {
        const val SHOP_NAME: String = "shop-name"
        const val SHOP_RULES: String = "shop-rules-list"
        const val TRADE_FUNC: String = "trade-func"
        const val UPDATE_INVENTORY_FUNC: String = "update-inventory-func"
        // Slug-native grammar (hard cutover from the legacy single-char run-type prefix):
        //   <slug>:<SOURCE>x<n>=<TARGET>x<m>[,<TARGET>x<m>...][:r]
        // The league slug is stated once for the whole rule; targets carry no prefix.
        // A trailing ":r" marks the trade as repeating.
        val SHOP_RULES_REGEX =
            "^(?<slug>[a-z0-9-]+):(?<sourceType>.+?)x(?<sourceCount>\\d{1,3})=(?<targetTypes>(?:.+?x\\d{1,3},?)+?)(?::(?<repeat>r))?$".toRegex()
        val SHOP_RULE_TARGET_REGEX = "^(?<targetType>.+?)x(?<targetCount>\\d{1,3})$".toRegex()
    }

    override fun onInit(config: ViewConfigBuilder) {
        config.layout(
            "         ",
            "         ",
            "         ",
        )
    }

    override fun onOpen(open: OpenContext) {
        var name = shopName[open]
        if (name.isNullOrBlank()) {
            name = "Purchase Item"
        }
        open.modifyConfig().title(SHOP_TITLE_FORMAT.format(name))
    }

    override fun onClose(event: CloseContext) {
        val player = event.player
        val inventory = (event.container as BukkitViewContainer).inventory

        try {
            shopRules[event].forEach { rule ->
                SHOP_RULES_REGEX.matchEntire(rule)?.let { matchResult ->
                    val groups = matchResult.groups
                    val slug = groups["slug"]?.value ?: "u"
                    val runType = getRunTypeByIdFromCache(plugin[event].leagueCache, slug)
                    val longType = runType.longId
                    val league = plugin[event].leagueCache.getLeagueBySlug(longType)

                    val sourceType = groups["sourceType"]?.value
                    val sourceCount = groups["sourceCount"]?.value
                    val targetTypes = groups["targetTypes"]?.value
                    val repeat = groups["repeat"]?.value?.let { it == "r" } ?: false

                    println("Rule: Convert { slug=$longType, sourceType=$sourceType, sourceCount=$sourceCount, targetTypes=$targetTypes)")
                    if (longType == unknownRunType.longId || sourceType == null || sourceCount == null || targetTypes == null) {
                        System.err.println("An expected regex group is missing from match, skipping rule: $rule")
                        return@let
                    }

                    val targetTypesList = targetTypes.split(",")
                    val invalidTargets = targetTypesList.filter { !SHOP_RULE_TARGET_REGEX.matches(it) }
                    if (invalidTargets.isNotEmpty()) {
                        player.sendRedMessage("The following trade targets are invalid for this shop:")
                        invalidTargets.forEach {
                            player.sendRedMessage(" - $it")
                        }
                        return@let
                    }

                    // Pick a random target if there's more than one option
                    var targetCount = 0
                    println("Target types: $targetTypesList")
                    val targetType = targetTypesList.random().let {
                        println("Selected target type: $it (match check: ${SHOP_RULE_TARGET_REGEX.matches(it)})")

                        SHOP_RULE_TARGET_REGEX.matchEntire(it)?.let { matchResult ->
                            val targetGroups = matchResult.groups
                            val targetType = targetGroups["targetType"]?.value
                            targetCount = targetGroups["targetCount"]?.value?.toInt() ?: 0
                            targetType
                        } ?: throw Exception("Shop target $it does not match regex")
                    }

                    var sendTradeMessage = true
                    var sendToDummy = false
                    var eventToSend: (count: Int) -> Unit = {}
                    if (cardConfig.find(targetType) != null) {
                        println("Target type is a card: $targetType")
                        val card = cardConfig.find(targetType)!!
                        val targetCard = card.shorthand
                        val cardsToAdd = targetCount
                        sendTradeMessage = false
                        sendToDummy = true
                        eventToSend = {
                            println("Adding ${cardsToAdd}x $targetCard (card) to ${player.name}'s deck")
                            (0.until(cardsToAdd)).forEach { _ ->
                                val newCard = Card(
                                    player = player.name,
                                    name = targetCard.replace("-", "_"),
                                )

                                addCardFunc[event].accept(runType.deckType(), newCard)
                            }
                            player.sendGreenMessage("Added ${cardsToAdd}x ${card.name} to your $longType deck")
                        }
                    } else if (targetType.equals("QUEUE", ignoreCase = true)) {
                        sendTradeMessage = false
                        eventToSend = {
                            println("Placing ${player.name} in queue with Deck ${longType}")
                            joinQueueFunc[event].accept(longType)
                        }
                    } else if (sourceType == "TOME" && targetType.equals("DUMMY", ignoreCase = true)) {
                        sendTradeMessage = false
                        eventToSend = { count ->
                            println("${player.name} submitted a victory tome")
                            player.sendGreenMessage("Successfully submitted ${count}x VICTORY TOME!")
                        }
                    } else if (intoDungeonItems.keys.contains(targetType)) {
                        println("Target type is an dungeon item: $targetType")

                        val targetItem = targetType
                        val itemsToAdd = targetCount
                        sendTradeMessage = false
                        sendToDummy = true
                        eventToSend = {
                            println("Adding ${itemsToAdd}x $targetItem (item) to ${player.name}'s deck")
                            (0.until(itemsToAdd)).forEach { _ ->
                                val newCard = Card(
                                    player = player.name,
                                    name = targetItem,
                                )

                                addCardFunc[event].accept(runType.deckType(), newCard)
                            }
                            player.sendGreenMessage("Added ${itemsToAdd}x $targetItem to your $longType deck")
                        }
                    }

                    val updateInventoryHandler: () -> Unit = {
                        // This is fired asynchronously, and we may have multiple copies running in parallel.
                        // The counter is incremented for each attempt, and when we complete the final trade
                        // we trigger an inventory update.
                        if (successfulTrades.decrement(event) == 0) {
                            println("Trade succeeded, refreshing inventory")
                            updateInventoryFunc[event].accept(player)
                        } else {
                            println("Not last trade, skipping inventory update")
                        }
                    }

                    val sourceCountInt = sourceCount.toInt()

                    // If it's a repeating trade, we're going to try to use as many resources as possible
                    println("Repeating trade? - $repeat")
                    val numberOfItemsToRemove = if (repeat) sourceCountInt * 100 else sourceCountInt

                    itemStackForSource(longType, sourceType, numberOfItemsToRemove)?.let { itemsToRemove ->
                        inventory.removeIfPresent(itemsToRemove, repeat) { numberOfItemsRemoved ->
                            // Submit trade
                            println("Successfully removed ${numberOfItemsRemoved}x$sourceType from trade view, submitting trade")
                            successfulTrades.increment(event)
                            tradeFunc[event].accept(
                                Trade(
                                    runType = longType,
                                    sourceType = sourceType,
                                    sourceItemCount = if (repeat) numberOfItemsRemoved / sourceCountInt else sourceCountInt,
                                    targetType = if (sendToDummy) "dummy" else targetType,
                                    targetItemCount = if (sendToDummy) 0 else targetCount,
                                    league = league,
                                ),
                                updateInventoryHandler, // This is fired for both success and fail
                            ) {
                                // Success handler
                                eventToSend(numberOfItemsRemoved)

                                if (sendTradeMessage) {
                                    player.sendGreenMessage("Successfully traded ${numberOfItemsRemoved}x${sourceType} for ${targetCount}x${targetType}")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        (0 until inventory.size)
            .map(inventory::getItem)
            .filter { it != null && it.type != Material.AIR }
            .map { it!! }
            .forEach { item: ItemStack ->
                println("Inventory contains: ${item.type}x${item.amount} - returning it to player")
                player.inventory.addItem(item)
            }
    }

    private fun itemStackForSource(runType: String, sourceType: String, sourceCount: Int): ItemStack? {
        return tradeItems.getOrElse(sourceType) {
            System.err.println("Unable to determine item stack for source type $sourceType")
            null
        }?.itemStack(runType, sourceCount)?.withTradeMeta(runType, sourceType)
    }

    private fun Inventory.removeIfPresent(itemsToRemove: ItemStack, allowLeftOvers: Boolean, success: (count: Int) -> Unit) {
        val startAmount = itemsToRemove.amount
        val insufficientItems = removeItemAnySlot(itemsToRemove)
        val insufficientItemCount = insufficientItems.values.sumOf { it.amount }
        val successCount = startAmount - insufficientItemCount

        if (insufficientItemCount == 0 || (successCount > 0 && allowLeftOvers)) {
            try {
                success(successCount)
            } catch (e: Exception) {
                e.printStackTrace()
                println("API call failed, reverting inventory changes")
                itemsToRemove.amount = successCount
                addItem(itemsToRemove)
            }
        } else {
            println("Could not remove ${insufficientItems.values} from shop inventory, reverting")
            itemsToRemove.amount = successCount
            addItem(itemsToRemove)
        }
    }
}
