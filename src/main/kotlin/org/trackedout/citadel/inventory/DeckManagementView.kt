package org.trackedout.citadel.inventory

import me.devnatan.inventoryframework.View
import me.devnatan.inventoryframework.ViewConfigBuilder
import me.devnatan.inventoryframework.context.Context
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
import org.trackedout.citadel.withTags
import org.trackedout.client.models.Card
import org.trackedout.data.RunType
import org.trackedout.data.findRunTypeById
import org.trackedout.data.getRunTypeById
import java.util.function.BiConsumer
import java.util.function.Consumer

open class DeckManagementView : View() {
    val plugin: State<Citadel> = initialState(PLUGIN)
    val addCardFunc: State<BiConsumer<DeckId, Card>> = initialState(ADD_CARD_FUNC)
    val deleteCardFunc: State<BiConsumer<DeckId, Card>> = initialState(DELETE_CARD_FUNC)
    val moveCardFunc: State<TriConsumer<DeckId, DeckId, Card>> = initialState(MOVE_CARD_FUNC)
    val joinQueueFunc: State<Consumer<DeckId>> = initialState(JOIN_QUEUE_FUNC)
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
        // DeckMap is a map of runType to Cards
        val decks = deckMap[render]

        // Init each deck link with an empty deck
        val deckIds = getPrimaryDeckIds(decks)
        val maxDeckId = deckIds.max()

//        (1..maxDeckId).toList()
//            .filter { !deckIds.contains(it) }
//            .forEach { deckId ->
//                showDeckLink(
//                    render,
//                    "View Deck #${deckId} (contains 0 cards)",
//                    deckId.toString(),
//                    materialForDeckId(deckId.toString())
//                )
//            }

        decks.forEach { entry ->
            val runType = entry.key
            val allCards = entry.value
            var i = if (runType.isPractice()) 0 else 18

            i += 4 // center decks (only works if we have 1 deck)

            (1..maxDeckId).toList()
                .map { "${runType}$it" }
                .forEach { deckId: DeckId ->
                    val cards = allCards.filterNot { it.hiddenInDecks?.contains(deckId) ?: false }

                    showDeckLink(
                        render,
                        i,
                        "View ${deckId.displayName()} Deck #${deckId.id()} (contains ${cards.size} cards)",
                        deckId,
                        deckId.deckMaterial(),
                    )
                    i++
                }
        }
    }

    internal fun getPrimaryDeckIds(decks: Map<String, List<Card>>): List<Int> {
//        val deckIds = decks.map { it.key.toIntOrNull() }
//            .filterNotNull()
//            .filter { it in 1..9 }
        // TODO: Do this better
        return listOf(1)
    }

    private fun showDeckLink(
        render: RenderContext,
        location: Int,
        title: String,
        deckId: DeckId,
        material: Material,
    ) {
        render.slot(location)
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

        return itemStack.withTags(mapOf("prevent-removal" to "1"))
    }

    internal fun getCards(render: Context, deckId: DeckId): List<Card> {
        var cards = deckMap[render][deckId.shortRunType()]
        if (cards == null) {
            cards = emptyList()
        }
        return cards.filterNot { it.hiddenInDecks?.contains(deckId) ?: false }
    }

    internal fun deleteCardAndShowUpdatedDeck(
        render: RenderContext,
        deckId: DeckId,
        card: Card,
    ) {
        deleteCardFunc[render].accept(deckId, card)
        val deckMap = deckMapWithCardRemoved(render, deckMap[render], deckId, card)
        val context = getContext(render).plus(DECK_MAP to deckMap)
        render.openForPlayer(DeckInventoryViewWithoutBack::class.java, context)
    }

    internal fun addCardAndShowUpdatedDeck(
        render: RenderContext,
        deckId: DeckId,
        card: Card,
    ) {
        addCardFunc[render].accept(deckId, card)
        val deckMap = deckMapWithCardAdded(render, deckMap[render], deckId, card)
        val context = getContext(render).plus(DECK_MAP to deckMap)
        render.openForPlayer(DeckInventoryViewWithoutBack::class.java, context)
    }

    internal fun moveCardAndShowUpdatedDeck(
        render: RenderContext,
        sourceDeckId: DeckId,
        targetDeckId: DeckId,
        card: Card,
    ) {
        moveCardFunc[render].accept(sourceDeckId, targetDeckId, card)
        var deckMap = deckMapWithCardRemoved(render, deckMap[render], sourceDeckId, card)
        deckMap = deckMapWithCardAdded(render, deckMap, targetDeckId, card)

        val context = getContext(render).plus(DECK_MAP to deckMap)
        render.openForPlayer(DeckInventoryViewWithoutBack::class.java, context.plus(SELECTED_DECK to targetDeckId))
    }

    private fun deckMapWithCardRemoved(
        render: RenderContext,
        originalDeckMap: Map<String, List<Card>>,
        deckId: DeckId,
        card: Card,
    ): MutableMap<String, List<Card>> {
        val deckMap = originalDeckMap.toMutableMap()

        val cards = getCards(render, deckId)
        val cardToRemove = cards.findLast { it.name == card.name }

        deckMap[deckId.shortRunType()] = cards - cardToRemove!!
        return deckMap
    }

    private fun deckMapWithCardAdded(
        render: RenderContext,
        originalDeckMap: Map<String, List<Card>>,
        deckId: DeckId,
        card: Card,
    ): MutableMap<String, List<Card>> {
        val deckMap = originalDeckMap.toMutableMap()
        val cards = getCards(render, deckId)
        deckMap[deckId.shortRunType()] = cards + card
        return deckMap
    }

    internal fun joinQueue(
        render: RenderContext,
        deckId: DeckId,
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

        val UPDATE_CARD_VISIBILITY_FUNC: String = "update-card-visibility-func"


        fun createContext(
            plugin: Citadel,
            player: Player,
            allCards: List<Card>,
            addCardFunc: BiConsumer<DeckId, Card>,
            deleteCardFunc: BiConsumer<DeckId, Card>,
            moveCardFunc: TriConsumer<DeckId, DeckId, Card>,
            joinQueueFunc: Consumer<DeckId>,
        ): MutableMap<String, Any> {

            val context = mutableMapOf(
                PLUGIN to plugin,
                PLAYER_NAME to player.name,
                DECK_MAP to allCards.groupBy { it.deckType!! },
                ADD_CARD_FUNC to addCardFunc,
                DELETE_CARD_FUNC to deleteCardFunc,
                MOVE_CARD_FUNC to moveCardFunc,
                JOIN_QUEUE_FUNC to joinQueueFunc,
            )

            return context
        }
    }
}

typealias DeckId = String

fun DeckId.id(): String = this.substring(1)

fun DeckId.shortRunType(): String = this.lowercase()[0].toString()

fun DeckId.displayName(): String {
    return getRunTypeById(this).displayName
}

fun DeckId.isValidRunType(): Boolean = findRunTypeById(this) != null

fun DeckId.isPractice(): Boolean = this.shortRunType() == "p"

fun DeckId.runType(): RunType = getRunTypeById(this)

fun DeckId.deckMaterial(): Material {
    return Material.valueOf(getRunTypeById(this).deckMaterial)
}
