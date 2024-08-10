package org.trackedout.citadel.inventory

import me.devnatan.inventoryframework.ViewConfigBuilder
import me.devnatan.inventoryframework.context.RenderContext
import me.devnatan.inventoryframework.state.State
import me.devnatan.inventoryframework.state.StateValueHost
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.trackedout.client.models.Card

open class MoveCardView : DeckManagementView() {
    val sourceDeckId: State<DeckId> = initialState(SELECTED_DECK)
    val selectedCard: State<Card> = initialState(SELECTED_CARD)


    override fun onInit(config: ViewConfigBuilder) {
        config.title("Select a Deck")
            .cancelOnClick()
            .layout(
                "123456789",
                "         ",
                "X        ",
            )
    }

    override fun onFirstRender(render: RenderContext) {
        val decks = deckMap[render]

        val deckIds = getPrimaryDeckIds(decks)
        var maxDeckId = deckIds.max()
        // If there's space for an extra deck, and the list is not missing a deck, allow us to add one
        if (maxDeckId < 9 && deckIds.size == maxDeckId) {
            maxDeckId++
        }

        // TODO: Support different deck types
        (1..maxDeckId).toList().forEach { deckId -> renderDeckLink(decks, render)("p${deckId}") }

        render.layoutSlot('X')
            .withItem(namedItem(Material.GOLD_INGOT, "Go back"))
            .onClick { _: StateValueHost? ->
                render.openForPlayer(DeckInventoryView::class.java, getContext(render))
            }
    }

    private fun renderDeckLink(
        decks: Map<String, List<Card>>,
        render: RenderContext,
    ) = { deckId: String ->
        val knownDeck = decks[deckId]
        var cardCount = 0
        knownDeck?.let { cards ->
            cardCount = cards.size
        }

        if (deckId == sourceDeckId[render]) {
            showDisabledDeckLink(
                render,
                "You're moving this card from Deck #${deckId}! Choose another",
                deckId,
                Material.GRAY_SHULKER_BOX
            )
        } else {
            showDeckLink(
                render,
                "Move to Deck #${deckId} (contains $cardCount cards)",
                deckId,
                Material.CYAN_SHULKER_BOX
            )
        }
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
                moveCardAndShowUpdatedDeck(render, sourceDeckId[render], deckId, selectedCard[render])
            }
    }

    private fun showDisabledDeckLink(
        render: RenderContext,
        title: String,
        deckId: String,
        material: Material,
    ) {
        render.layoutSlot(deckId[0])
            .withItem(namedItem(material, title, NamedTextColor.GRAY))
            .onClick { _: StateValueHost? -> }
    }
}
