package org.trackedout.citadel.inventory

import me.devnatan.inventoryframework.ViewConfigBuilder
import me.devnatan.inventoryframework.context.RenderContext
import me.devnatan.inventoryframework.state.State
import me.devnatan.inventoryframework.state.StateValueHost
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.trackedout.citadel.commands.GiveShulkerCommand
import org.trackedout.client.models.Card
import kotlin.math.max

class CardActionView : DeckManagementView() {
    val deckId: State<String> = initialState(SELECTED_DECK)
    val selectedCard: State<Card> = initialState(SELECTED_CARD)


    override fun onInit(config: ViewConfigBuilder) {
        config.title("Viewing Card")
            .cancelOnClick()
            .layout(
                "    #    ",
                "  -   +  ",
                "    M    ",
                "        X"
            )
    }

    override fun onFirstRender(render: RenderContext) {
        val cards = getCards(render, deckId[render])
        val card = selectedCard[render]

        val count = max(cards.count { it.name == card.name }, 1)

        // Current count item
        render.layoutSlot('#')
            .withItem(GiveShulkerCommand.createCard(null, null, card.name!!, count))

        // Decrement button
        render.layoutSlot('-')
            .withItem(namedItem(Material.RED_WOOL, "Click to DELETE one copy", NamedTextColor.RED))
            .onClick { _: StateValueHost? -> deleteCardAndShowUpdatedDeck(render, deckId[render], card) }

        // Increment button
        render.layoutSlot('+')
            .withItem(namedItem(Material.GREEN_WOOL, "Click to add a copy", NamedTextColor.GREEN))
            .onClick { _: StateValueHost? -> addCardAndShowUpdatedDeck(render, deckId[render], card) }

        // Move button
        render.layoutSlot('M')
            .withItem(namedItem(Material.SEA_LANTERN, "Move to another deck", NamedTextColor.GRAY))
            .onClick { _: StateValueHost? ->
                render.openForPlayer(MoveCardView::class.java, getContext(render))
            }

        render.layoutSlot('X')
            .withItem(namedItem(Material.GOLD_INGOT, "Go back"))
            .onClick { _: StateValueHost? ->
                render.openForPlayer(DeckInventoryView::class.java, getContext(render))
            }
    }
}
