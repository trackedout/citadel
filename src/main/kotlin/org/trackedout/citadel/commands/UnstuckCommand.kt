package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Subcommand
import org.bukkit.Location
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.sendGreenMessage

@CommandAlias("decked-out|do")
class UnstuckCommand(
    private val plugin: Citadel,
) : BaseCommand() {

    @Subcommand("unstuck")
    @Description("Unstuck yourself")
    fun unstuck(player: Player) {
        player.sendGreenMessage("Be freeeee!")
        player.teleport(Location(player.world, -512.0, 114.0, 1980.0, 90f, 0f))
    }
}
