package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Subcommand
import me.devnatan.inventoryframework.ViewFrame
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.config.RunType
import org.trackedout.citadel.inventory.showCollection
import org.trackedout.citadel.sendRedMessage
import org.trackedout.client.apis.ScoreApi
import org.trackedout.client.models.CatalogLobbyAction

// Opens the artifact collection that the league's lobby has an item for
@CommandAlias("decked-out|do")
class ShowArtifakesCommand(
    private val plugin: Citadel,
    private val scoreApi: ScoreApi,
    private val viewFrame: ViewFrame,
) : BaseCommand() {

    @Subcommand("artifakes")
    @Description("Show artifakes")
    @CommandCompletion("@runTypes @nothing")
    fun showArtifakes(source: CommandSender, runType: RunType?) {
        if (source is Player) {
            showArtifakes(source, source.name, runType)
        } else {
            source.sendRedMessage("Cannot show your artifakes as you are not a player. Use /do artifakes <playerName>")
        }
    }

    @Subcommand("artifakes")
    @CommandPermission("decked-out.artifakes.view.all")
    @Description("Show artifakes for target player")
    @CommandCompletion("@runTypes @dbPlayers @nothing")
    fun showArtifakesForPlayer(source: CommandSender, runType: RunType?, targetPlayer: String) {
        showArtifakes(source, targetPlayer, runType)
    }

    private fun showArtifakes(source: CommandSender, playerName: String, runType: RunType?) {
        plugin.async(source) {
            val league = runType ?: plugin.runModes.current(playerName)
                ?: throw IllegalStateException("$playerName has no run mode, name a league")
            val action = plugin.catalogCache.require(league.longId).lobby.map { it.action }.firstOrNull {
                it.type == CatalogLobbyAction.Type.showMinusCollection && it.kind == CatalogLobbyAction.Kind.artifact
            } ?: throw IllegalStateException("The ${league.displayName} lobby has no artifact collection")

            showCollection(plugin, viewFrame, scoreApi, source, playerName, league.longId, action)
        }
    }
}
