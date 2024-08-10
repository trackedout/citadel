package org.trackedout.citadel.inventory

import me.devnatan.inventoryframework.ViewConfigBuilder
import me.devnatan.inventoryframework.context.OpenContext
import me.devnatan.inventoryframework.context.RenderContext
import me.devnatan.inventoryframework.state.State
import me.devnatan.inventoryframework.state.StateValueHost
import org.bukkit.Material
import org.trackedout.citadel.commands.GiveShulkerCommand.Companion.createCard
import org.trackedout.client.models.Card
import org.trackedout.data.Cards

class DeckInventoryView : DeckManagementView() {
    val deckIdState: State<DeckId> = initialState(SELECTED_DECK)


    override fun onInit(config: ViewConfigBuilder) {
        config.title("❄☠ Frozen Assets ☠❄")
            .cancelOnClick()
            .size(6)
    }

    override fun onOpen(context: OpenContext) {
        val player = context.player
        context
            .modifyConfig()
            .title("Hi, ${player.name}! Editing ${playerName[context]}'s Deck")
    }

    override fun onFirstRender(render: RenderContext) {
        val deckId = deckIdState[render]
        val cards = getCards(render, deckId)

        render.slot(6, 1)
            .withItem(namedItem(Material.GOLD_INGOT, "Go back"))
            .onClick { _: StateValueHost? ->
                render.openForPlayer(DeckManagementView::class.java, getContext(render))
            }

        if (deckId.shortRunType() == "p") {
            render.slot(6, 9)
                .withItem(namedItem(Material.SLIME_BLOCK, "Add a card"))
                .onClick { _: StateValueHost? -> render.openForPlayer(AddACardView::class.java, getContext(render)) }
        }

//        if (cards.isNotEmpty()) {
//            render.slot(6, 5)
//                .withItem(namedItem(Material.ECHO_SHARD, "QUEUE"))
//                .onClick { _: StateValueHost? -> joinQueue(render, deckId) }
//        }

        Cards.Companion.Card.entries.sortedBy { it.colour + it.key }.forEach { cardDefinition ->
            val count = cards.count { it.name == cardDefinition.key }
            if (count == 0) {
                return@forEach
            }

            val itemStack = createCard(null, null, cardDefinition.key, count)

            itemStack?.let {
                val slot = render.availableSlot(it)

                if (deckId.shortRunType() == "p") {
                    slot.onClick { _: StateValueHost? ->
                        val newCard = Card(
                            player = playerName[render],
                            name = cardDefinition.key,
                            deckType = deckId.shortRunType(),
                            server = plugin[render].serverName,
                        )

                        render.openForPlayer(CardActionView::class.java, getContext(render).plus(SELECTED_CARD to newCard))
                    }
                }
            }
        }
    }
}
