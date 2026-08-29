package org.trackedout.citadel.inventory

import me.devnatan.inventoryframework.ViewConfigBuilder
import me.devnatan.inventoryframework.context.RenderContext
import me.devnatan.inventoryframework.state.State
import me.devnatan.inventoryframework.state.StateValueHost
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.trackedout.client.models.Card

open class MoveCardView : DeckManagementView() {
    val sourceDeckId: State<LeagueSlug> = initialState(SELECTED_DECK)
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

        // One deck per league: offer each known league deck as a move target,
        // laid out left-to-right. deck ids are league slugs.
        val layoutChars = "123456789"
        decks.keys.take(layoutChars.length).forEachIndexed { index, deckId ->
            renderDeckLink(decks, render, layoutChars[index])(deckId)
        }

        render.layoutSlot('X')
            .withItem(namedItem(Material.GOLD_INGOT, "Go back"))
            .onClick { _: StateValueHost? ->
                render.openForPlayer(DeckInventoryViewWithoutBack::class.java, getContext(render))
            }
    }

    private fun renderDeckLink(
        decks: Map<String, List<Card>>,
        render: RenderContext,
        slot: Char,
    ) = { deckId: String ->
        val knownDeck = decks[deckId]
        val cardCount = knownDeck?.size ?: 0

        if (deckId == sourceDeckId[render]) {
            showDisabledDeckLink(
                render,
                slot,
                "You're moving this card from the ${deckId.displayName(plugin[render].leagueCache)} deck! Choose another",
                Material.GRAY_SHULKER_BOX
            )
        } else {
            showDeckLink(
                render,
                slot,
                deckId,
                "Move to ${deckId.displayName(plugin[render].leagueCache)} deck (contains $cardCount cards)",
                deckId.deckMaterial(plugin[render].leagueCache),
            )
        }
    }

    private fun showDeckLink(
        render: RenderContext,
        slot: Char,
        deckId: String,
        title: String,
        material: Material,
    ) {
        render.layoutSlot(slot)
            .withItem(namedItem(material, title))
            .onClick { _: StateValueHost? ->
                moveCardAndShowUpdatedDeck(render, sourceDeckId[render], deckId, selectedCard[render])
            }
    }

    private fun showDisabledDeckLink(
        render: RenderContext,
        slot: Char,
        title: String,
        material: Material,
    ) {
        render.layoutSlot(slot)
            .withItem(namedItem(material, title, NamedTextColor.GRAY))
            .onClick { _: StateValueHost? -> }
    }
}
