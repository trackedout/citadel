package org.trackedout.citadel.inventory

import me.devnatan.inventoryframework.BukkitViewContainer
import me.devnatan.inventoryframework.ViewConfigBuilder
import me.devnatan.inventoryframework.context.CloseContext
import me.devnatan.inventoryframework.context.OpenContext
import me.devnatan.inventoryframework.context.RenderContext
import me.devnatan.inventoryframework.state.State
import me.devnatan.inventoryframework.state.StateValueHost
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.canTakeIntoDungeon
import org.trackedout.citadel.commands.createCard
import org.trackedout.citadel.getCard
import org.trackedout.citadel.getSlug
import org.trackedout.citadel.getTradeId
import org.trackedout.citadel.isDeckedOutCard
import org.trackedout.citadel.preventRemoval
import org.trackedout.client.models.Card
import org.trackedout.data.BrillianceCard
import org.trackedout.data.sortedList
import java.util.function.BiConsumer

open class DeckInventoryView : DeckManagementView() {
    val deckIdState: State<LeagueSlug> = initialState(SELECTED_DECK)
    val updateCardVisibilityFunc: State<BiConsumer<LeagueSlug, Map<String, Number>>> = initialState(UPDATE_CARD_VISIBILITY_FUNC)


    override fun onInit(config: ViewConfigBuilder) {
        config.title("❄☠ Frozen Assets ☠❄")
    }

    override fun onOpen(context: OpenContext) {
        val deckId = deckIdState[context]
        val cards = getCards(context, deckId)

        context
            .modifyConfig()
            .size(rowCount(cards))
    }

    override fun onClose(event: CloseContext) {
        val player = event.player
        val deckId = deckIdState[event] as LeagueSlug
        val inventory = (event.container as BukkitViewContainer).inventory

        // Put all non-card items back in the players inventory
        (0 until inventory.size)
            .map(inventory::getItem)
            .filter { it != null && it.type != Material.AIR }
            .map { it!! }
            .filterNot { it.preventRemoval() } // Keep these items in the shulker
            .filter { it.getSlug() != deckId || (!it.canTakeIntoDungeon() && !it.isDeckedOutCard(plugin[event].catalogCache)) }
            .forEach { item: ItemStack ->
                plugin[event].logger.info("Inventory contains: ${item.type}x${item.amount} - returning it to ${player.name}")
                plugin[event].logger.info("Item: $item")
                plugin[event].logger.info("LeagueSlug: ${item.getSlug()} - Can take into dungeon: ${item.canTakeIntoDungeon()} - Is Decked Out card: ${item.isDeckedOutCard(plugin[event].catalogCache)}")
                player.inventory.addItem(item)
            }

        if (player.itemOnCursor.isDeckedOutCard(plugin[event].catalogCache)) {
            player.itemOnCursor.amount = 0
        }

        // For each card in the player's inventory, hide it from the deck
        var cardsToHide = (0 until player.inventory.size).asSequence()
            .map(player.inventory::getItem)
            .filter { it != null && it.isDeckedOutCard(plugin[event].catalogCache) && it.getSlug() == deckId }
            // Count the total number of cards in the player's inventory across all stacks
            .map {
                it!!.getCard(plugin[event].catalogCache)!! to player.inventory
                    .filter { card -> card != null && card.isDeckedOutCard(plugin[event].catalogCache) && card.getSlug() == deckId }
                    .filter { p -> p?.getCard(plugin[event].catalogCache)?.shorthand == it.getCard(plugin[event].catalogCache)?.shorthand }.sumOf { p -> p.amount }
            }
            .onEach { pair: Pair<BrillianceCard, Int> ->
                val card = pair.first
                val amount = pair.second
                plugin[event].logger.info("${player.name}'s inventory contains ${amount}x${card.tag.display.name} - hiding it from Deck $deckId")
            }
            .associate {
                it.first.shorthand to it.second
            }

        // For each item in the player's inventory, hide it from the deck
        val itemsToHide = (0 until player.inventory.size).asSequence()
            .map(player.inventory::getItem)
            .filter { it != null && it.canTakeIntoDungeon() && it.getSlug() == deckId }
            // Count the total number of this item in the player's inventory across all stacks
            .map {
                it!!.getTradeId()!! to player.inventory
                    .filter { item -> item != null && item.canTakeIntoDungeon() && item.getSlug() == deckId }
                    .filter { p -> p?.getTradeId() == it.getTradeId() }.sumOf { p -> p.amount }
            }
            .onEach { pair: Pair<String, Int> ->
                val tradeId = pair.first
                val amount = pair.second
                plugin[event].logger.info("${player.name}'s inventory contains ${amount}x${tradeId} - hiding it from Deck $deckId")
            }
            .associate {
                it.first to it.second
            }
        cardsToHide = cardsToHide.plus(itemsToHide)

        plugin[event].logger.info("Updating deck visibility for Deck ID #${deckId} for ${player.name}, hiding: ${cardsToHide.map { "${it.value}x${it.key.uppercase()}" }}")
        updateCardVisibilityFunc[event].accept(deckId, cardsToHide)
    }

    override fun onFirstRender(render: RenderContext) {
        val deckId = deckIdState[render]
        val cards = getCards(render, deckId)

        if (showBackButton()) {
            render.slot(rowCount(cards), 1)
                .cancelOnClick()
                .withItem(namedItem(Material.GOLD_INGOT, "Go back"))
                .onClick { _: StateValueHost? ->
                    render.openForPlayer(DeckManagementView::class.java, getContext(render))
                }
        }

        if (deckId.isPractice(plugin[render])) {
            render.slot(rowCount(cards), 9)
                .cancelOnClick()
                .withItem(namedItem(Material.SLIME_BLOCK, "Add a card"))
                .onClick { _: StateValueHost? -> render.openForPlayer(AddACardView::class.java, getContext(render)) }

            render.slot(rowCount(cards), 8)
                .cancelOnClick()
                .withItem(namedItem(Material.CHEST, "Add an item"))
                .onClick { _: StateValueHost? -> render.openForPlayer(AddAnItemView::class.java, getContext(render)) }
        }

        val catalog = plugin[render].catalogCache.require(deckId)

        catalog.cards.sortedList().forEach { cardDefinition ->
            val count = cards.count { it.name == cardDefinition.shorthand }
            if (count == 0) {
                return@forEach
            }

            render.availableSlot(createCard(catalog, cardDefinition.shorthand, count, deckId))
        }

        catalog.intoDungeonItems.forEach { item ->
            val count = cards.count { it.name == item.tradeId }
            if (count == 0) {
                return@forEach
            }

            render.availableSlot(tradeItemSpec(deckId, item, count).toItemStack())
        }
    }

    open fun showBackButton(): Boolean {
        return true
    }

    private fun rowCount(cards: List<Card>): Int {
        val uniqueCardCount = cards.distinctBy { it.name }.size
        return if (uniqueCardCount > 25 || showBackButton()) {
            6
        } else {
            3
        }
    }
}

class DeckInventoryViewWithoutBack : DeckInventoryView() {
    override fun showBackButton(): Boolean {
        return false
    }

    override fun onClose(event: CloseContext) {
        super.onClose(event)

        event.player.playSound(event.player.location, Sound.BLOCK_BARREL_CLOSE, 1f, 1f)
    }
}
