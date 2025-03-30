package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Subcommand
import com.mongodb.client.model.Filters
import org.bukkit.command.CommandSender
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.FirstRun
import org.trackedout.citadel.PageWatcher
import org.trackedout.citadel.async
import org.trackedout.citadel.mongo.MongoDBManager
import org.trackedout.citadel.mongo.MongoScore
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendRedMessage

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

    @Subcommand("hardcore-leaderboard")
    @Description("Show hardcore leaderboard")
    fun showHardcoreLeaderboard(source: CommandSender) {
        plugin.async(source) {

            val database = MongoDBManager.getDatabase("dunga-dunga")
            val scoreCollection = database.getCollection("scores", MongoScore::class.java)

            val scores = scoreCollection.find(
                Filters.and(
                    Filters.eq("key", "leaderboard-hardcore-do2.lifetime.escaped.tomes")
                )
            ).toList()

            if (scores.isEmpty()) {
                source.sendRedMessage("No applicable scores found for this leaderboard")
                return@async
            }

            source.sendRedMessage("Hardcore leaderboard:")
            var rank = 0
            var previousScore: Long? = null

            scores.sortedByDescending { it.value }.forEach { score ->
                if (previousScore != score.value) {
                    rank++
                }

                source.sendRedMessage("- ${rank}. ${score.player} with ${score.value} ${if (score.value == 1L) "tome" else "tomes"}")
                previousScore = score.value
            }
        }
    }
}
