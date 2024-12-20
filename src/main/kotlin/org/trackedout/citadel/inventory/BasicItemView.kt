package org.trackedout.citadel.inventory

import me.devnatan.inventoryframework.View
import me.devnatan.inventoryframework.ViewConfigBuilder
import me.devnatan.inventoryframework.context.OpenContext
import me.devnatan.inventoryframework.context.RenderContext
import me.devnatan.inventoryframework.state.State
import net.kyori.adventure.text.TextComponent
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.Citadel

open class BasicItemView : View() {
    val plugin: State<Citadel> = initialState(PLUGIN)
    val player: State<Player> = initialState(PLAYER)
    val title: State<TextComponent> = initialState(TITLE)
    val items: State<Map<Int, ItemStack>> = initialState(ITEMS)
    val rows: State<Int> = initialState(ROWS)

    companion object {
        val PLUGIN: String = "plugin"
        val PLAYER: String = "player"
        val TITLE: String = "title"
        val ITEMS: String = "items"
        val ROWS: String = "rows"

        fun createContext(
            plugin: Citadel,
            player: Player,
            title: TextComponent,
            items: Map<Int, ItemStack>, // Items to show in the view
            size: Int = 6, // Number of rows in the view
        ): MutableMap<String, Any> {

            val context = mutableMapOf(
                PLUGIN to plugin,
                PLAYER to player,
                TITLE to title,
                ITEMS to items,
                ROWS to size,
            )

            return context
        }
    }

    override fun onInit(config: ViewConfigBuilder) {
        config
            .cancelOnClick()
    }

    override fun onOpen(open: OpenContext) {
        open.modifyConfig().title(title[open])
        open.modifyConfig().size(rows[open])
    }

    override fun onFirstRender(render: RenderContext) {
        val items = items[render]
        items.forEach { (index, item) ->
            render.slot(index, item)
        }
    }
}
