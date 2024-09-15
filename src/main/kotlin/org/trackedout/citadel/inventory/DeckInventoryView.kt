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
import org.trackedout.citadel.commands.GiveShulkerCommand.Companion.createCard
import org.trackedout.citadel.getCard
import org.trackedout.citadel.getDeckId
import org.trackedout.citadel.getTradeId
import org.trackedout.citadel.isDeckedOutCard
import org.trackedout.citadel.preventRemoval
import org.trackedout.client.models.Card
import org.trackedout.data.Cards
import java.util.function.BiConsumer

open class DeckInventoryView : DeckManagementView() {
    val deckIdState: State<DeckId> = initialState(SELECTED_DECK)
    val updateCardVisibilityFunc: State<BiConsumer<DeckId, Map<String, Number>>> = initialState(UPDATE_CARD_VISIBILITY_FUNC)


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
        val deckId = deckIdState[event] as DeckId
        val inventory = (event.container as BukkitViewContainer).inventory

        // Put all non-card items back in the players inventory
        (0 until inventory.size)
            .map(inventory::getItem)
            .filter { it != null && it.type != Material.AIR }
            .map { it!! }
            .filterNot { it.preventRemoval() } // Keep these items in the shulker
            .filter { it.getDeckId() != deckId || (!it.canTakeIntoDungeon() && !it.isDeckedOutCard()) }
            .forEach { item: ItemStack ->
                println("Inventory contains: ${item.type}x${item.amount} - returning it to player")
                player.inventory.addItem(item)
            }

        if (player.itemOnCursor.isDeckedOutCard()) {
            player.itemOnCursor.amount = 0
        }

        // For each card in the player's inventory, hide it from the deck
        var cardsToHide = (0 until player.inventory.size).asSequence()
            .map(player.inventory::getItem)
            .filter { it != null && it.isDeckedOutCard() && it.getDeckId() == deckId }
            // Count the total number of cards in the player's inventory across all stacks
            .map {
                it!!.getCard()!! to player.inventory
                    .filter { it != null && it.isDeckedOutCard() && it.getDeckId() == deckId }
                    .filter { p -> p?.getCard()?.name == it.getCard()?.name }.sumOf { p -> p.amount }
            }
            .onEach { pair: Pair<Cards.Companion.Card, Int> ->
                val card = pair.first
                val amount = pair.second
                println("Player's inventory contains ${amount}x${card.name} - hiding it from Deck $deckId")
            }
            .associate {
                it.first.key to it.second
            }

        // For each item in the player's inventory, hide it from the deck
        val itemsToHide = (0 until player.inventory.size).asSequence()
            .map(player.inventory::getItem)
            .filter { it != null && it.canTakeIntoDungeon() && it.getDeckId() == deckId }
            // Count the total number of this item in the player's inventory across all stacks
            .map {
                it!!.getTradeId()!! to player.inventory
                    .filter { it != null && it.canTakeIntoDungeon() && it.getDeckId() == deckId }
                    .filter { p -> p?.getTradeId() == it.getTradeId() }.sumOf { p -> p.amount }
            }
            .onEach { pair: Pair<String, Int> ->
                val tradeId = pair.first
                val amount = pair.second
                println("Player's inventory contains ${amount}x${tradeId} - hiding it from Deck $deckId")
            }
            .associate {
                it.first to it.second
            }
        cardsToHide = cardsToHide.plus(itemsToHide)

        println("Updating deck visibility for Deck ID #${deckId}, hiding: ${cardsToHide.map { "${it.value}x${it.key.uppercase()}" }}")
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

        if (deckId.shortRunType() == "p") {
            render.slot(rowCount(cards), 9)
                .cancelOnClick()
                .withItem(namedItem(Material.SLIME_BLOCK, "Add a card"))
                .onClick { _: StateValueHost? -> render.openForPlayer(AddACardView::class.java, getContext(render)) }
        }

//        if (cards.isNotEmpty()) {
//            render.slot(6, 5)
//                 .cancelOnClick()
//                .withItem(namedItem(Material.ECHO_SHARD, "QUEUE"))
//                .onClick { _: StateValueHost? -> joinQueue(render, deckId) }
//        }

        Cards.Companion.Card.entries.sortedBy { it.colour + it.key }.forEach { cardDefinition ->
            val count = cards.count { it.name == cardDefinition.key }
            if (count == 0) {
                return@forEach
            }

            val itemStack = createCard(null, null, cardDefinition.key, count, deckId)

            itemStack?.let {
                val slot = render.availableSlot(it)

//                if (deckId.shortRunType() == "p") {
//                    slot.onClick { _: StateValueHost? ->
//                        val newCard = Card(
//                            player = playerName[render],
//                            name = cardDefinition.key,
//                            deckType = deckId.shortRunType(),
//                            server = plugin[render].serverName,
//                        )
//
//                        render.openForPlayer(CardActionView::class.java, getContext(render).plus(SELECTED_CARD to newCard))
//                    }
//                }
            }
        }

        intoDungeonItems.entries.forEach { entry ->
            val count = cards.count { it.name == entry.key }
            if (count == 0) {
                return@forEach
            }

            val itemStack = entry.value.itemStack(deckId.fullRunType().lowercase(), count).withTradeMeta(deckId.shortRunType(), entry.key)
            render.availableSlot(itemStack)
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
