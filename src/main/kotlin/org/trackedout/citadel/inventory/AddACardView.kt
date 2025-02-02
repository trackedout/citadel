package org.trackedout.citadel.inventory

import me.devnatan.inventoryframework.ViewConfigBuilder
import me.devnatan.inventoryframework.context.RenderContext
import me.devnatan.inventoryframework.state.State
import me.devnatan.inventoryframework.state.StateValueHost
import org.bukkit.Material
import org.trackedout.citadel.commands.GiveShulkerCommand.Companion.createCard
import org.trackedout.client.models.Card
import org.trackedout.data.Cards

class AddACardView : DeckManagementView() {
    val deckId: State<DeckId> = initialState(SELECTED_DECK)


    override fun onInit(config: ViewConfigBuilder) {
        config.title("Add a Card")
            .cancelOnClick()
            .size(6)
    }

    override fun onFirstRender(render: RenderContext) {
        val cards = getCards(render, deckId[render])

        render.slot(6, 1)
            .withItem(namedItem(Material.GOLD_INGOT, "Go back"))
            .onClick { _: StateValueHost? ->
                render.openForPlayer(DeckInventoryViewWithoutBack::class.java, getContext(render))
            }

        Cards.Companion.Card.entries.sortedBy { it.colour + it.key }.forEachIndexed { index, card ->
            val itemStack = createCard(null, null, card.key, 1)

            itemStack?.let {
                render.slot(index, it)
                    .onClick { _: StateValueHost? ->
                        val runType = getRunType(render)
                        val newCard = Card(
                            player = playerName[render],
                            name = card.key,
                            deckType = runType,
                            server = plugin[render].serverName,
                        )

                        addCardAndShowUpdatedDeck(render, deckId[render], newCard)
                    }
            }
        }
    }

    private fun getRunType(render: RenderContext) = deckId[render][0].toString()
}
