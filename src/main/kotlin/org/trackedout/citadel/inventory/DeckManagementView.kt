package org.trackedout.citadel.inventory

import me.devnatan.inventoryframework.View
import me.devnatan.inventoryframework.ViewConfigBuilder
import me.devnatan.inventoryframework.context.RenderContext
import me.devnatan.inventoryframework.state.State
import me.devnatan.inventoryframework.state.StateValueHost
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.Citadel
import org.trackedout.client.models.Card
import java.util.function.Consumer

open class DeckManagementView : View() {
    val plugin: State<Citadel> = initialState(PLUGIN)
    val addCardFunc: State<Consumer<Card>> = initialState(ADD_CARD_FUNC)
    val deleteCardFunc: State<Consumer<Card>> = initialState(DELETE_CARD_FUNC)
    val playerName: State<String> = initialState(PLAYER_NAME)
    val playerCards: State<List<Card>> = initialState(PLAYER_CARDS)


    override fun onInit(config: ViewConfigBuilder) {
        config.title("Deck Management Overview")
            .cancelOnClick()
            .layout(
                "         ",
                "  - # +  ",
                "         ",
                "         "
            )
    }

    override fun onFirstRender(render: RenderContext) {
        val cards = playerCards[render]

        render.layoutSlot('#')
            .withItem(namedItem(Material.CYAN_SHULKER_BOX, "View ${playerName[render]}'s Deck (contains ${cards.size} cards)"))
            .onClick { _: StateValueHost? -> render.openForPlayer(DeckInventoryView::class.java, getContext(render)) }

        render.layoutSlot('+')
            .withItem(namedItem(Material.SLIME_BLOCK, "Add a card"))
            .onClick { _: StateValueHost? -> render.openForPlayer(AddACardView::class.java, getContext(render)) }
    }

    internal fun getContext(render: RenderContext) = render.initialData as MutableMap<String, Any>

    fun namedItem(material: Material, name: String, textColor: NamedTextColor = NamedTextColor.AQUA): ItemStack {
        val itemStack = ItemStack(material)
        val meta = itemStack.itemMeta
        meta.displayName(Component.text(name, textColor))
        itemStack.itemMeta = meta

        return itemStack
    }

    internal fun deleteCardAndShowUpdatedDeck(
        render: RenderContext,
        card: Card,
        cards: List<Card>,
    ) {
        deleteCardFunc[render].accept(card)
        val context = getContext(render).plus(PLAYER_CARDS to cards - cards.findLast { it.name == card.name })
        render.openForPlayer(DeckInventoryView::class.java, context)
    }

    internal fun addCardAndShowUpdatedDeck(
        render: RenderContext,
        card: Card,
        cards: List<Card>,
    ) {
        addCardFunc[render].accept(card)
        val context = getContext(render).plus(PLAYER_CARDS to cards.plus(card))
        render.openForPlayer(DeckInventoryView::class.java, context)
    }

    companion object {
        val PLUGIN: String = "plugin"
        val PLAYER_NAME: String = "player-name"
        val PLAYER_CARDS: String = "player-cards"
        val SELECTED_CARD: String = "selected-card"

        val ADD_CARD_FUNC: String = "add-card-func"
        val DELETE_CARD_FUNC: String = "delete-card-func"


        fun createContext(
            plugin: Citadel,
            player: Player,
            playerCards: List<Card>,
            addCardFunc: Consumer<Card>,
            deleteCardFunc: Consumer<Card>,
        ): MutableMap<String, Any> {
            val context = mutableMapOf(
                PLUGIN to plugin,
                PLAYER_NAME to player.name,
                PLAYER_CARDS to playerCards,
                ADD_CARD_FUNC to addCardFunc,
                DELETE_CARD_FUNC to deleteCardFunc,
            )

            return context
        }
    }

}
