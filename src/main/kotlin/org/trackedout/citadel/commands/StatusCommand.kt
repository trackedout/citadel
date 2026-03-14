package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Subcommand
import org.bukkit.entity.Player
import org.trackedout.citadel.opsLogsTag
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendGreyMessage

@CommandAlias("decked-out|do")
class StatusCommand : BaseCommand() {
    @Subcommand("status")
    @CommandPermission("decked-out.inventory.admin")
    @Description("Render scoreboard with network status")
    fun renderStatus(player: Player, args: Array<String>) {
        if (!player.scoreboardTags.contains(opsLogsTag)) {
            player.sendGreenMessage("Giving you the debug tag, you should see the status scoreboard shortly")
            player.scoreboardTags.add(opsLogsTag)
        } else {
            player.sendGreyMessage("Removing debug tag, you will no longer see the status scoreboard")
            player.scoreboardTags.remove(opsLogsTag)
        }
    }

    @Subcommand("logs")
    @CommandPermission("decked-out.ops.logs")
    @Description("Show operator logs")
    fun showOpsLogs(player: Player, args: Array<String>) {
        if (!player.scoreboardTags.contains(opsLogsTag)) {
            player.sendGreenMessage("Giving you the debug tag, you will see operator debug logs")
            player.scoreboardTags.add(opsLogsTag)
        } else {
            player.sendGreyMessage("Removing debug tag, you will no longer see operator debug logs")
            player.scoreboardTags.remove(opsLogsTag)
        }
    }
}
