package org.trackedout.citadel.inventory

import me.devnatan.inventoryframework.View
import me.devnatan.inventoryframework.ViewConfigBuilder
import me.devnatan.inventoryframework.context.CloseContext
import me.devnatan.inventoryframework.context.Context
import me.devnatan.inventoryframework.context.OpenContext
import me.devnatan.inventoryframework.context.RenderContext
import me.devnatan.inventoryframework.context.SlotClickContext
import me.devnatan.inventoryframework.state.State
import me.devnatan.inventoryframework.state.StateValueHost
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.inventory.DeckManagementView.Companion.JOIN_QUEUE_FUNC
import org.trackedout.citadel.sendRedMessage
import java.util.function.Consumer

class EnterQueueView : View() {
    private val joinQueueFunc: State<Consumer<String>> = initialState(JOIN_QUEUE_FUNC)
    private val readyForQueue = mutableState(false)


    override fun onInit(config: ViewConfigBuilder) {
        config.title("Enter Queue")
            .cancelOnClick()
            .layout(
                "         ",
                "    #    ",
                "         ",
            )
    }

    override fun onOpen(open: OpenContext) {
        if (giveShard(open).isNotEmpty()) {
            open.player.sendRedMessage("You don't have space in your inventory for your dungeon key!")
            open.closeForPlayer()
        }
    }

    override fun onClose(close: CloseContext) {
        removeShard(close)
        if (readyForQueue[close]) {
            joinQueueFunc[close].accept("1")
        }
    }

    override fun onClick(slotClick: SlotClickContext) {
        if (slotClick.item == shardForPlayerInventory()) {
            // Player inventory (bottom)
            if (slotClick.isOnEntityContainer) {
                readyForQueue.set(true, slotClick)
                removeShard(slotClick)
            }
        }
    }

    override fun onFirstRender(render: RenderContext) {
        render.layoutSlot('#')
            .watch(readyForQueue)
            .renderWith {
                dungeonShard(
                    "Joining queue! Close this inventory to finalize.",
                    NamedTextColor.AQUA,
                    if (readyForQueue[render]) 1 else 0
                )
            }
            .onClick { _: StateValueHost ->
                if (readyForQueue[render]) {
                    readyForQueue.set(false, render)
                    giveShard(render)
                }
            }
    }

    private fun giveShard(open: Context): Map<Int, ItemStack> {
        val inventory = open.player.inventory
        if (inventory.getItem(4) == null) {
            inventory.setItem(4, shardForPlayerInventory())
            return emptyMap()
        } else {
            return inventory.addItem(shardForPlayerInventory())
        }
    }

    private fun removeShard(close: Context) {
        val inventory = close.player.inventory
        while (inventory.containsAtLeast(shardForPlayerInventory(), 1)) {
            inventory.removeItem(shardForPlayerInventory())
        }
    }

    private fun shardForPlayerInventory() = dungeonShard(
        "Click me to join the queue!",
        NamedTextColor.AQUA,
    )
}
