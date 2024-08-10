package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Subcommand
import org.bukkit.command.CommandSender
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.models.Event

@CommandAlias("decked-out|do")
class ShutdownDungeonsCommand(
    private val plugin: Citadel,
    private val eventsApi: EventsApi,
) : BaseCommand() {
    @Subcommand("shutdown-all-empty-dungeons")
    @CommandPermission("decked-out.inventory.admin")
    @Description("Shutdown all empty dungeons")
    fun shutdownEmptyDungeons(source: CommandSender) {
        plugin.async(source) {
            eventsApi.eventsPost(
                Event(
                    name = "shutdown-all-empty-dungeons",
                    server = plugin.serverName,
                    count = 1,
                    x = 0.0,
                    y = 0.0,
                    z = 0.0,
                )
            )
        }
    }
}
