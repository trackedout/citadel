package org.trackedout.citadel.inventory

import me.devnatan.inventoryframework.ViewConfigBuilder
import me.devnatan.inventoryframework.context.RenderContext
import me.devnatan.inventoryframework.state.State
import me.devnatan.inventoryframework.state.StateValueHost
import org.bukkit.Material
import org.trackedout.client.models.Card

class AddAnItemView : DeckManagementView() {
    val deckId: State<LeagueSlug> = initialState(SELECTED_DECK)

    override fun onInit(config: ViewConfigBuilder) {
        config.title("Add an Item")
            .cancelOnClick()
            .size(3)
    }

    override fun onFirstRender(render: RenderContext) {
        render.slot(3, 1)
            .withItem(namedItem(Material.GOLD_INGOT, "Go back"))
            .onClick { _: StateValueHost? ->
                render.openForPlayer(DeckInventoryViewWithoutBack::class.java, getContext(render))
            }

        plugin[render].catalogCache.require(deckId[render]).intoDungeonItems.forEachIndexed { index, item ->
            render.slot(index, tradeItemSpec(deckId[render], item, 1).toItemStack())
                .onClick { _: StateValueHost? ->
                    val newCard = Card(
                        player = playerName[render],
                        name = item.tradeId,
                        deckType = getRunType(render),
                        server = plugin[render].serverName,
                    )

                    addCardAndShowUpdatedDeck(render, deckId[render], newCard)
                }
        }
    }

    private fun getRunType(render: RenderContext) = deckId[render]
}
