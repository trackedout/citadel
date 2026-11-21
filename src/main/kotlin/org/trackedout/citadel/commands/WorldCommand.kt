package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Subcommand
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendGreyMessage
import org.trackedout.citadel.sendRedMessage
import org.trackedout.client.apis.WorldsApi
import org.trackedout.client.infrastructure.ClientException
import org.trackedout.client.models.World

@CommandAlias("decked-out|do")
@CommandPermission("decked-out.league")
class WorldCommand(
    private val plugin: Citadel,
    private val worldsApi: WorldsApi,
    private val worldEditor: WorldEditor,
) : BaseCommand() {

    @Subcommand("world list")
    @Description("List worlds and where they are built")
    fun listWorlds(player: Player) {
        plugin.async(player) {
            val worlds = try {
                worldsApi.worldsGet().results.orEmpty().sortedBy { it.slug }
            } catch (e: ClientException) {
                player.sendRedMessage("Failed to list worlds: ${e.message}")
                return@async
            }

            if (worlds.isEmpty()) {
                player.sendGreyMessage("No worlds")
                return@async
            }

            player.sendGreenMessage("Worlds (${worlds.size}):")
            worlds.forEach { player.sendGreyMessage("- ${it.slug}: ${describeBuildPlace(it)}, ${it.storage.source.value}") }
        }
    }

    @Subcommand("world edit")
    @CommandCompletion("@worldSlugs")
    @Description("Edit a world on its build server or an on-demand sandbox")
    fun editWorld(player: Player, worldSlug: String) {
        plugin.async(player) { worldEditor.edit(player, worldSlug) }
    }

    @Subcommand("world stop")
    @CommandCompletion("@worldSlugs")
    @CommandPermission("decked-out.league.stop")
    @Description("Stop a world's sandbox build server")
    fun stopWorld(player: Player, worldSlug: String) {
        plugin.async(player) { worldEditor.stop(player, worldSlug) }
    }

    private fun describeBuildPlace(world: World): String = world.buildServer?.value ?: "sandbox"
}
