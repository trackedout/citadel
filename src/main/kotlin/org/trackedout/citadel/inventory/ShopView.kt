package org.trackedout.citadel.inventory

import me.devnatan.inventoryframework.BukkitViewContainer
import me.devnatan.inventoryframework.View
import me.devnatan.inventoryframework.ViewConfigBuilder
import me.devnatan.inventoryframework.context.CloseContext
import me.devnatan.inventoryframework.context.OpenContext
import me.devnatan.inventoryframework.state.MutableIntState
import me.devnatan.inventoryframework.state.State
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.inventory.DeckManagementView.Companion.ADD_CARD_FUNC
import org.trackedout.citadel.inventory.DeckManagementView.Companion.DELETE_CARD_FUNC
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.client.models.Card
import org.trackedout.data.Cards
import java.util.function.BiConsumer
import java.util.function.Consumer

const val SHOP_TITLE_FORMAT = "Citadel: %s"

data class Trade(
    val runType: String,
    val sourceType: String,
    val sourceItemCount: Int,
    val targetType: String,
    val targetItemCount: Int,
) {
    fun sourceScoreboardName(): String {
        return tradeItems.getOrElse(sourceType.uppercase()) {
            throw Exception("Unknown source type '$sourceType', unable to determine source scoreboard")
        }.sourceScoreboardName(this)
    }

    fun sourceInversionScoreboardName(): String {
        return tradeItems.getOrElse(sourceType.uppercase()) {
            throw Exception("Unknown source type '$sourceType', unable to determine source inversion scoreboard")
        }.sourceInversionScoreboardName(this)
    }

    fun targetScoreboardName(): String {
        return tradeItems.getOrElse(targetType.uppercase()) {
            throw Exception("Unknown target type '$targetType', unable to determine target scoreboard")
        }.targetScoreboardName(this)
    }
}

class ShopView : View() {
    private val shopName: State<String> = initialState(SHOP_NAME)
    private val shopRules: State<List<String>> = initialState(SHOP_RULES)
    private val tradeFunc: State<BiConsumer<Trade, () -> Unit>> = initialState(TRADE_FUNC)
    private val updateInventoryFunc: State<Consumer<Player>> = initialState(UPDATE_INVENTORY_FUNC)
    private val addCardFunc: State<BiConsumer<String, Card>> = initialState(ADD_CARD_FUNC)
    private val deleteCardFunc: State<BiConsumer<String, Card>> = initialState(DELETE_CARD_FUNC)
    private val successfulTrades: MutableIntState = mutableState(0)

    companion object {
        const val SHOP_NAME: String = "shop-name"
        const val SHOP_RULES: String = "shop-rules-list"
        const val TRADE_FUNC: String = "trade-func"
        const val UPDATE_INVENTORY_FUNC: String = "update-inventory-func"
        val SHOP_RULES_REGEX = "(?<type>[pc])(?<sourceType>.*?)x(?<sourceCount>\\d{1,3})=[pc](?<targetType>.*?)x(?<targetCount>\\d{1,3})".toRegex()
    }

    override fun onInit(config: ViewConfigBuilder) {
        config
            .layout(
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
                    val type = when (groups["type"]?.value) {
                        "p" -> "practice"
                        "c" -> "competitive"
                        else -> null
                    }

                    val sourceType = groups["sourceType"]?.value
                    val sourceCount = groups["sourceCount"]?.value
                    val targetType = groups["targetType"]?.value
                    val targetCount = groups["targetCount"]?.value

                    println("Rule: Convert { type=$type, fromType=$sourceType, fromCount=$sourceCount, resultType=$targetType, resultCount=$targetCount)")
                    if (type == null || sourceType == null || sourceCount == null || targetType == null || targetCount == null) {
                        System.err.println("An expected regex group is missing from match, skipping rule: $rule")
                        return@let
                    }

                    var sendToDummy = false
                    var eventToSend: () -> Unit = {}
                    if (Cards.Companion.Card.entries.map { it.key.lowercase() }.contains(targetType.lowercase())) {
                        println("Target type is a card: $targetType")
                        val targetCard = targetType.lowercase()
                        val cardsToAdd = targetCount.toInt()
                        sendToDummy = true
                        eventToSend = {
                            println("Adding ${cardsToAdd}x $targetCard cards to ${player.name}'s deck")
                            (0 until cardsToAdd).map {
                                val newCard = Card(
                                    player = player.name,
                                    name = targetCard.replace("-", "_"),
                                )

                                // TODO: Change Deck ID based on run type
                                addCardFunc[event].accept("1", newCard)
                            }
                        }
                    }

                    itemStackForSource(type, sourceType, sourceCount.toInt())?.let { itemsToRemove ->
                        inventory.removeIfPresent(itemsToRemove) {
                            // Submit trade
                            println("Successfully removed ${sourceCount.toInt()}x$sourceType from trade view, submitting trade")
                            successfulTrades.increment(event)
                            tradeFunc[event].accept(
                                Trade(
                                    runType = type,
                                    sourceType = sourceType,
                                    sourceItemCount = sourceCount.toInt(),
                                    targetType = if (sendToDummy) "dummy" else targetType,
                                    targetItemCount = if (sendToDummy) 0 else targetCount.toInt(),
                                )
                            ) {
                                eventToSend()

                                // This is fired asynchronously, and we may have multiple copies running in parallel.
                                // The counter is incremented for each attempt, and when we complete the final trade
                                // we trigger an inventory update.
                                if (successfulTrades.decrement(event) == 0) {
                                    println("Trade succeeded, refreshing inventory")
                                    updateInventoryFunc[event].accept(player)
                                } else {
                                    println("Not last trade, skipping inventory update")
                                }

                                player.sendGreenMessage("Successfully traded ${sourceCount.toInt()}x${sourceType} for ${targetCount}x${targetType}")
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
        }?.itemStack(runType, sourceCount)
    }

    private fun Inventory.removeIfPresent(itemsToRemove: ItemStack, success: () -> Unit) {
        val startAmount = itemsToRemove.amount
        val insufficientItems = removeItemAnySlot(itemsToRemove)
        if (insufficientItems.isEmpty()) {
            try {
                success()
            } catch (e: Exception) {
                e.printStackTrace()
                println("API call failed, reverting inventory changes")
                itemsToRemove.amount = startAmount - insufficientItems.values.sumOf { it.amount }
                addItem(itemsToRemove)
            }
        } else {
            println("Inventory count not remove ${insufficientItems.values}, reverting")
            itemsToRemove.amount = startAmount - insufficientItems.values.sumOf { it.amount }
            addItem(itemsToRemove)
        }
    }
}
