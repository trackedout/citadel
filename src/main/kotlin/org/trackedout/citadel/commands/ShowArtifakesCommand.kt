package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Subcommand
import me.devnatan.inventoryframework.ViewFrame
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.config.itemStack
import org.trackedout.citadel.config.itemStackNotYetAcquired
import org.trackedout.citadel.config.loadArtifactConfig
import org.trackedout.citadel.getInventoryFilterForPlayer
import org.trackedout.citadel.inventory.BasicItemView
import org.trackedout.citadel.runOnNextTick
import org.trackedout.citadel.sendRedMessage
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.ScoreApi
import org.trackedout.data.RunType
import org.trackedout.data.getRunTypeById

@CommandAlias("decked-out|do")
class ShowArtifakesCommand(
    private val plugin: Citadel,
    private val eventsApi: EventsApi,
    private val scoreApi: ScoreApi,
    private val viewFrame: ViewFrame,
) : BaseCommand() {

    @Subcommand("artifakes")
    @Description("Show artifakes")
    @CommandCompletion("@runTypes")
    fun showArtifakes(source: CommandSender, runType: RunType?) {
        if (source is Player) {
            showArtifakeUIForPlayer(this.plugin, this.viewFrame, source, source.name, this.scoreApi, runType)
        } else {
            source.sendRedMessage("Cannot show your artifakes as you are not a player. Use /do artifakes <playerName>")
        }
    }

    @Subcommand("artifakes")
    @CommandPermission("decked-out.artifakes.view.all")
    @Description("Show artifakes for target player")
    @CommandCompletion("@runTypes @dbPlayers")
    fun showArtifakesForPlayer(source: CommandSender, runType: RunType?, targetPlayer: String) {
        showArtifakeUIForPlayer(this.plugin, this.viewFrame, source, targetPlayer, this.scoreApi, runType)
    }
}

fun showArtifakeUIForPlayer(
    plugin: Citadel,
    viewFrame: ViewFrame,
    source: CommandSender,
    playerName: String,
    scoreApi: ScoreApi,
    runType: RunType? = null,
) {
    plugin.async(source) {
        val runType = runType ?: getInventoryFilterForPlayer(plugin, scoreApi, playerName) ?: getRunTypeById("c")
        val runTypeLongId = runType.longId
        val scores = scoreApi.scoresGet(playerName, prefixFilter = "${runTypeLongId}-do2.artifakes.").results!!

        val artifactConfig = loadArtifactConfig()

        if (source !is Player) {
            source.sendMessage("Artifakes for $playerName (${runType.displayName}):")
        }

        val items = mutableMapOf<Int, ItemStack>()
        var artifactIndex = 0
        artifactConfig?.entries?.sortedBy { it.value.emberValue }?.forEach { (shortKey, props) ->
            val artifakeScoreKey = "${runTypeLongId}-do2.artifakes.${shortKey}"
            items[artifactIndex] = props.itemStackNotYetAcquired(1)
//            items[artifactIndex] = props.itemStack(props.id, 1)
            scores.find { it.key == artifakeScoreKey }?.let { score ->
                val itemCount = score.value?.toInt() ?: 0
                if (itemCount > 0) {
                    items[artifactIndex] = props.itemStack(props.id, itemCount)

                    if (source !is Player) {
                        source.sendMessage("$artifakeScoreKey -> ${props.tag.display.name} = ${score.value}")
                    }
                }
            }
            artifactIndex++
            if (artifactIndex == 27) { // use 45 for 6 rows
                artifactIndex += 4
            }
        } ?: source.sendRedMessage("Failed to load artifact config from json")

        if (source is Player) {
            plugin.runOnNextTick {
                val name = if (source.name == playerName) "Your" else "$playerName's"
                val title = Component.text("$name ${runType.displayName} Artifakes")
                    .color(NamedTextColor.BLUE)
                    .decorate(TextDecoration.BOLD)
                val context = BasicItemView.createContext(plugin, source, title, items, 4)
                viewFrame.open(BasicItemView::class.java, source, context)
            }
        }
    }
}
