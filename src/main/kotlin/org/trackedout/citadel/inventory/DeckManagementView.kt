package org.trackedout.citadel.inventory

import me.devnatan.inventoryframework.View
import me.devnatan.inventoryframework.ViewConfigBuilder
import me.devnatan.inventoryframework.context.RenderContext
import me.devnatan.inventoryframework.state.State
import me.devnatan.inventoryframework.state.StateValueHost
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.apache.logging.log4j.util.TriConsumer
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.Citadel
import org.trackedout.client.models.Card
import java.util.function.BiConsumer
import java.util.function.Consumer

open class DeckManagementView : View() {
    val plugin: State<Citadel> = initialState(PLUGIN)
    val addCardFunc: State<BiConsumer<String, Card>> = initialState(ADD_CARD_FUNC)
    val deleteCardFunc: State<BiConsumer<String, Card>> = initialState(DELETE_CARD_FUNC)
    val moveCardFunc: State<TriConsumer<String, String, Card>> = initialState(MOVE_CARD_FUNC)
    val joinQueueFunc: State<Consumer<String>> = initialState(JOIN_QUEUE_FUNC)
    val playerName: State<String> = initialState(PLAYER_NAME)
    val deckMap: State<Map<String, List<Card>>> = initialState(DECK_MAP)


    override fun onInit(config: ViewConfigBuilder) {
        config.title("Deck Management Overview")
            .cancelOnClick()
            .layout(
                "123456789",
                "  - # +  ",
                "         ",
                "         "
            )
    }

    override fun onFirstRender(render: RenderContext) {
        val decks = deckMap[render]

        // Init each deck link with an empty deck
        val deckIds = getPrimaryDeckIds(decks)
        val maxDeckId = deckIds.max()

        (1..maxDeckId).toList()
            .filter { !deckIds.contains(it) }
            .forEach { deckId ->
                showDeckLink(
                    render,
                    "View Deck #${deckId} (contains 0 cards)",
                    deckId.toString(),
                    materialForDeckId(deckId.toString())
                )
            }

        decks.forEach { deck ->
            val deckId = deck.key
            val cards = deck.value

            showDeckLink(
                render,
                "View Deck #${deckId} (contains ${cards.size} cards)",
                deckId,
                materialForDeckId(deckId)
            )
        }
    }

    internal fun getPrimaryDeckIds(decks: Map<String, List<Card>>): List<Int> {
        val deckIds = decks.map { it.key.toIntOrNull() }
            .filterNotNull()
            .filter { it in 1..9 }
        return deckIds
    }

    private fun materialForDeckId(deckId: String): Material {
        var material = Material.CYAN_SHULKER_BOX

        // Make every other shulker a different colour
        deckId.toIntOrNull()?.let {
            if (it % 2 == 0) {
                material = Material.BLUE_SHULKER_BOX
            }
        }
        return material
    }

    private fun showDeckLink(
        render: RenderContext,
        title: String,
        deckId: String,
        material: Material,
    ) {

        render.layoutSlot(deckId[0])
            .withItem(namedItem(material, title))
            .onClick { _: StateValueHost? ->
                val newContext = getContext(render)
                    .plus(SELECTED_DECK to deckId)

                render.openForPlayer(DeckInventoryView::class.java, newContext)
            }
    }

    internal fun getContext(render: RenderContext) = render.initialData as MutableMap<String, Any>

    fun namedItem(material: Material, name: String, textColor: NamedTextColor = NamedTextColor.AQUA): ItemStack {
        val itemStack = ItemStack(material)
        val meta = itemStack.itemMeta
        meta.displayName(Component.text(name, textColor))
        itemStack.itemMeta = meta

        return itemStack
    }

    internal fun getCards(render: RenderContext, deckId: String): List<Card> {
        var cards = deckMap[render][deckId]
        if (cards == null) {
            cards = emptyList()
        }
        return cards
    }

    internal fun deleteCardAndShowUpdatedDeck(
        render: RenderContext,
        deckId: String,
        card: Card,
    ) {
        deleteCardFunc[render].accept(deckId, card)
        val deckMap = deckMapWithCardRemoved(render, deckMap[render], deckId, card)
        val context = getContext(render).plus(DECK_MAP to deckMap)
        render.openForPlayer(DeckInventoryView::class.java, context)
    }

    internal fun addCardAndShowUpdatedDeck(
        render: RenderContext,
        deckId: String,
        card: Card,
    ) {
        addCardFunc[render].accept(deckId, card)
        val deckMap = deckMapWithCardAdded(render, deckMap[render], deckId, card)
        val context = getContext(render).plus(DECK_MAP to deckMap)
        render.openForPlayer(DeckInventoryView::class.java, context)
    }

    internal fun moveCardAndShowUpdatedDeck(
        render: RenderContext,
        sourceDeckId: String,
        targetDeckId: String,
        card: Card,
    ) {
        moveCardFunc[render].accept(sourceDeckId, targetDeckId, card)
        var deckMap = deckMapWithCardRemoved(render, deckMap[render], sourceDeckId, card)
        deckMap = deckMapWithCardAdded(render, deckMap, targetDeckId, card)

        val context = getContext(render).plus(DECK_MAP to deckMap)
        render.openForPlayer(DeckInventoryView::class.java, context.plus(SELECTED_DECK to targetDeckId))
    }

    private fun deckMapWithCardRemoved(
        render: RenderContext,
        originalDeckMap: Map<String, List<Card>>,
        deckId: String,
        card: Card,
    ): MutableMap<String, List<Card>> {
        val deckMap = originalDeckMap.toMutableMap()

        val cards = getCards(render, deckId)
        val cardToRemove = cards.findLast { it.name == card.name }

        deckMap[deckId] = cards - cardToRemove!!
        return deckMap
    }

    private fun deckMapWithCardAdded(
        render: RenderContext,
        originalDeckMap: Map<String, List<Card>>,
        deckId: String,
        card: Card,
    ): MutableMap<String, List<Card>> {
        val deckMap = originalDeckMap.toMutableMap()
        val cards = getCards(render, deckId)
        deckMap[deckId] = cards + card
        return deckMap
    }

    internal fun joinQueue(
        render: RenderContext,
        deckId: String,
    ) {
        joinQueueFunc[render].accept(deckId)
        render.closeForPlayer()
    }

    companion object {
        val PLUGIN: String = "plugin"
        val PLAYER_NAME: String = "player-name"
        val DECK_MAP: String = "deck-map"
        val SELECTED_CARD: String = "selected-card"
        val SELECTED_DECK: String = "selected-deck"

        val ADD_CARD_FUNC: String = "add-card-func"
        val DELETE_CARD_FUNC: String = "delete-card-func"
        val MOVE_CARD_FUNC: String = "move-card-func"
        val JOIN_QUEUE_FUNC: String = "join-queue-func"


        fun createContext(
            plugin: Citadel,
            player: Player,
            allCards: List<Card>,
            addCardFunc: BiConsumer<String, Card>,
            deleteCardFunc: BiConsumer<String, Card>,
            moveCardFunc: TriConsumer<String, String, Card>,
            joinQueueFunc: Consumer<String>,
        ): MutableMap<String, Any> {
            val context = mutableMapOf(
                PLUGIN to plugin,
                PLAYER_NAME to player.name,
                DECK_MAP to allCards.groupBy { it.deckId!! },
                ADD_CARD_FUNC to addCardFunc,
                DELETE_CARD_FUNC to deleteCardFunc,
                MOVE_CARD_FUNC to moveCardFunc,
                JOIN_QUEUE_FUNC to joinQueueFunc,
            )

            return context
        }
    }

}
