package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import co.aikar.commands.bukkit.contexts.OnlinePlayer
import org.bukkit.command.BlockCommandSender
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.sendGreyMessage
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.models.EventsPostRequest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@CommandAlias("log-event")
class LogEventCommand(
    private val eventsApi: EventsApi
) : BaseCommand() {
    @Dependency
    private lateinit var plugin: Citadel


    @Default
    @Syntax("[event] [count] [player]")
    @CommandPermission("decked-out.log-event")
    @Description("Add Decked Out 2 card into player's DB inventory")
    fun logEvent(sender: CommandSender, eventName: String, @Default("1") count: Int, @Optional target: OnlinePlayer?) {
        var player = target?.player
        if (player == null && sender is OnlinePlayer) {
            player = sender.player
        }
        if (player == null && sender is Player) {
            player = sender
        }
        var x = player?.x ?: 0.0
        var y = player?.y ?: 0.0
        var z = player?.z ?: 0.0
        var world = player?.world

        if (player == null && sender is BlockCommandSender) {
            x = sender.block.x.toDouble()
            y = sender.block.y.toDouble()
            z = sender.block.z.toDouble()
            world = sender.block.world
        }

        plugin.async(sender) {
            val playerName = player?.name ?: sender.name
            eventsApi.eventsPost(
                EventsPostRequest(
                    name = eventName,
                    server = plugin.serverName,
                    player = playerName,
//                    worldAge = world?.gameTime,
                    count = count,
                    x = x,
                    y = y,
                    z = z,
                )
            )

            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:SS")
            val current = LocalDateTime.now().format(formatter)
            val message = "[${current}/${world?.gameTime}] Sent { event=$eventName, count=$count, player=$playerName, location=[$x, $y, $z] } to Dunga Dunga"
            plugin.logger.info(message)
            sender.sendGreyMessage(message)
        }
    }
}