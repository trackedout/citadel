package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Subcommand
import org.bukkit.command.CommandSender
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.FirstRun
import org.trackedout.citadel.PageWatcher
import org.trackedout.citadel.sendGreenMessage

@CommandAlias("decked-out|do")
class LeaderboardCommand(
    private val plugin: Citadel,
) : BaseCommand() {
    @Subcommand("leaderboard clear")
    @CommandPermission("decked-out.leaderboard.admin")
    @Description("Clear and hide the leaderboard")
    fun clearAndHideLeaderboard(source: CommandSender) {
        FirstRun.showLeaderboard = false
        FirstRun.isFirstRun = true
        FirstRun.skip = 0
        source.sendGreenMessage("Leaderboard cleared and hidden")
    }

    @Subcommand("leaderboard show")
    @CommandPermission("decked-out.leaderboard.admin")
    @Description("Show the leaderboard and optionally animate")
    fun showLeaderboard(source: CommandSender, animate: Boolean) {
        FirstRun.showLeaderboard = true
        FirstRun.isFirstRun = animate
        PageWatcher.page = 99
        source.sendGreenMessage("Showing leaderboard ${if (animate) "with" else "without"} animation")
    }
}
