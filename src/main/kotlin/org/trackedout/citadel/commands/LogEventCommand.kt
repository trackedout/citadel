package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import org.bukkit.command.CommandSender
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.sendGreyMessage
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.models.EventsPostRequest

@CommandAlias("log-event")
class LogEventCommand(
    private val eventsApi: EventsApi
) : BaseCommand() {
    @Dependency
    private lateinit var plugin: Citadel


    @Default
    @Syntax("[event] [count]")
    @CommandPermission("decked-out.log-event")
    @Description("Add Decked Out 2 card into player's DB inventory")
    fun logEvent(sender: CommandSender, eventName: String, @Default("1") count: Int) {
        plugin.async(sender) {
            eventsApi.eventsPost(
                EventsPostRequest(
                    name = eventName,
                    server = plugin.serverName,
                    player = sender.name,
                    count = count,
                    x = 0.0,
                    y = 0.0,
                    z = 0.0,
                )
            )
            sender.sendGreyMessage("Sent $eventName (count = $count) to Dunga Dunga")
        }
    }
}