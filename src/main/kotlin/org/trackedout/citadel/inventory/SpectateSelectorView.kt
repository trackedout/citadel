package org.trackedout.citadel.inventory

import com.saicone.rtag.RtagItem
import me.devnatan.inventoryframework.View
import me.devnatan.inventoryframework.ViewConfigBuilder
import me.devnatan.inventoryframework.context.RenderContext
import me.devnatan.inventoryframework.state.State
import me.devnatan.inventoryframework.state.StateValueHost
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.commands.Game
import java.util.function.BiConsumer

open class SpectateSelectorView : View() {
    val plugin: State<Citadel> = initialState(PLUGIN)
    val spectateGameFunc: State<BiConsumer<Player, Game>> = initialState(SPECTATE_GAME_FUNC)
    val player: State<Player> = initialState(PLAYER)
    val games: State<List<Game>> = initialState(GAMES)

    companion object {
        val PLUGIN: String = "plugin"
        val PLAYER: String = "player"
        val GAMES: String = "games"
        val SPECTATE_GAME_FUNC: String = "spectate-game-func"

        fun createContext(
            plugin: Citadel,
            player: Player,
            games: List<Game>,
            spectateGameFunc: BiConsumer<Player, Game>,
        ): MutableMap<String, Any> {

            val context = mutableMapOf(
                PLUGIN to plugin,
                PLAYER to player,
                GAMES to games,
                SPECTATE_GAME_FUNC to spectateGameFunc,
            )

            return context
        }
    }

    override fun onInit(config: ViewConfigBuilder) {
        config.title("Spectate a player")
            .cancelOnClick()
            .size(6)
    }

    override fun onFirstRender(render: RenderContext) {
        val games = games[render]

        games.forEachIndexed { index, game ->
            val item = ItemStack(Material.PLAYER_HEAD, 1)
            val nameColour = NamedTextColor.DARK_PURPLE

            val itemStack = RtagItem.edit(item, fun(tag: RtagItem): ItemStack {
                tag.set(game.playerName, "SkullOwner")

                val itemName = "Spectate ${game.playerName}'s ${game.shortRunType.fullRunType().lowercase()} game"
                val nameJson = "{\"color\":\"${nameColour}\",\"text\":\"$itemName\"}"
                tag.set(nameJson, "display", "Name")
                tag.set("{\"color\":\"${nameColour}\",\"OriginalName\":\"${nameJson}\"}", "display", "NameFormat");

                return tag.loadCopy();
            })

            if (index < render.container.slotsCount) {
                render.slot(index, itemStack)
                    .onClick { _: StateValueHost ->
                        spectateGameFunc[render].accept(player[render], game)
                    }
            } else {
                plugin[render].logger.warning("Unable to render spectate button for ${game.playerName} (#${index}) as all slots are used!")
            }
        }
    }
}
