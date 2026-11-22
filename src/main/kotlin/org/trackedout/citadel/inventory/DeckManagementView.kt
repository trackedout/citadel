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
import org.trackedout.citadel.config.LeagueCache
import org.trackedout.citadel.config.RunType
import org.trackedout.citadel.config.findRunTypeByIdFromCache
import org.trackedout.citadel.config.getRunTypeByIdFromCache
import java.util.function.BiConsumer
import java.util.function.Consumer

open class DeckManagementView : View() {
    val plugin: State<Citadel> = initialState(PLUGIN)
    val addCardFunc: State<BiConsumer<LeagueSlug, Card>> = initialState(ADD_CARD_FUNC)
    val deleteCardFunc: State<BiConsumer<LeagueSlug, Card>> = initialState(DELETE_CARD_FUNC)
    val moveCardFunc: State<TriConsumer<LeagueSlug, LeagueSlug, Card>> = initialState(MOVE_CARD_FUNC)
    val joinQueueFunc: State<Consumer<LeagueSlug>> = initialState(JOIN_QUEUE_FUNC)
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
        // DeckMap is a map of deck slug (Card.deckType) to Cards
        val decks = deckMap[render]

        decks.forEach { entry ->
            // With one deck per league, the map key (Card.deckType) is the deck's
            // slug, which is the LeagueSlug itself.
            val deckId: LeagueSlug = entry.key
            val allCards = entry.value
            var i = if (deckId.isPractice(plugin[render])) 0 else 18

            i += 4 // center decks (only works if we have 1 deck)

            val cards = allCards.filterNot { it.hiddenInDecks?.contains(deckId) ?: false }

            showDeckLink(
                render,
                i,
                "View ${deckId.displayName(plugin[render].leagueCache)} Deck (contains ${cards.size} cards)",
                deckId,
                deckId.deckMaterial(plugin[render].leagueCache),
            )
        }
    }

    private fun showDeckLink(
        render: RenderContext,
        location: Int,
        title: String,
        deckId: LeagueSlug,
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

    internal fun getCards(render: Context, deckId: LeagueSlug): List<Card> {
        var cards = deckMap[render][deckId]
        if (cards == null) {
            cards = emptyList()
        }
        return cards.filterNot { it.hiddenInDecks?.contains(deckId) ?: false }
    }

    internal fun deleteCardAndShowUpdatedDeck(
        render: RenderContext,
        deckId: LeagueSlug,
        card: Card,
    ) {
        deleteCardFunc[render].accept(deckId, card)
        val deckMap = deckMapWithCardRemoved(render, deckMap[render], deckId, card)
        val context = getContext(render).plus(DECK_MAP to deckMap)
        render.openForPlayer(DeckInventoryViewWithoutBack::class.java, context)
    }

    internal fun addCardAndShowUpdatedDeck(
        render: RenderContext,
        deckId: LeagueSlug,
        card: Card,
    ) {
        addCardFunc[render].accept(deckId, card)
        val deckMap = deckMapWithCardAdded(render, deckMap[render], deckId, card)
        val context = getContext(render).plus(DECK_MAP to deckMap)
        render.openForPlayer(DeckInventoryViewWithoutBack::class.java, context)
    }

    internal fun moveCardAndShowUpdatedDeck(
        render: RenderContext,
        sourceDeckId: LeagueSlug,
        targetDeckId: LeagueSlug,
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
        deckId: LeagueSlug,
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
        deckId: LeagueSlug,
        card: Card,
    ): MutableMap<String, List<Card>> {
        val deckMap = originalDeckMap.toMutableMap()
        val cards = getCards(render, deckId)
        deckMap[deckId] = cards + card
        return deckMap
    }

    internal fun joinQueue(
        render: RenderContext,
        deckId: LeagueSlug,
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
            addCardFunc: BiConsumer<LeagueSlug, Card>,
            deleteCardFunc: BiConsumer<LeagueSlug, Card>,
            moveCardFunc: TriConsumer<LeagueSlug, LeagueSlug, Card>,
            joinQueueFunc: Consumer<LeagueSlug>,
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

typealias LeagueSlug = String

// A league slug identifies a deck (one deck per league). The legacy positional
// encoding ("<shortRunType><deckNumber>", e.g. "c1") is gone; compatibility with
// old in-world items is handled at the NBT parse boundary (ItemStack.getSlug).

fun LeagueSlug.displayName(leagueCache: LeagueCache): String {
    return getRunTypeByIdFromCache(leagueCache, this).displayName
}

fun LeagueSlug.isValidRunType(leagueCache: LeagueCache): Boolean = findRunTypeByIdFromCache(leagueCache, this) != null

fun LeagueSlug.isPractice(plugin: Citadel): Boolean =
    findRunTypeByIdFromCache(plugin.leagueCache, this)?.longId.let { it != null && it == plugin.serverSettings.leagues()?.practice }

fun LeagueSlug.runType(leagueCache: LeagueCache): RunType = getRunTypeByIdFromCache(leagueCache, this)

fun LeagueSlug.deckMaterial(leagueCache: LeagueCache): Material {
    return Material.valueOf(getRunTypeByIdFromCache(leagueCache, this).deckMaterial)
}
