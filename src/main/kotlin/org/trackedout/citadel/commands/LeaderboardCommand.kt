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
import org.trackedout.citadel.getInt
import org.trackedout.citadel.runOnNextTick

import org.trackedout.citadel.async
import org.trackedout.citadel.mongo.MongoDBManager
import org.trackedout.citadel.mongo.MongoScore
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendRedMessage
import org.trackedout.client.apis.ConfigApi
import org.trackedout.client.models.Config

@CommandAlias("decked-out|do")
class LeaderboardCommand(
    private val plugin: Citadel,
    private val configApi: ConfigApi,
) : BaseCommand() {
    @Subcommand("leaderboard hide")
    @CommandPermission("decked-out.leaderboard.admin")
    @Description("Clear and hide the leaderboard")
    fun clearAndHideLeaderboard(source: CommandSender) {
        FirstRun.animating = false
        FirstRun.running = false
        plugin.async(source) {
            configApi.configsAddConfigPost(Config(entity = "lobby", key = "show-leaderboard", value = "false"))
            FirstRun.showLeaderboard = false
            plugin.runOnNextTick { plugin.leaderboardTaskRunner.hideAll() }
            source.sendGreenMessage("Leaderboard hidden")
        }
    }

    @Subcommand("leaderboard show")
    @CommandPermission("decked-out.leaderboard.admin")
    @Description("Show the leaderboard and optionally animate")
    fun showLeaderboard(source: CommandSender, animate: Boolean) {
        plugin.async(source) {
            val currentPhase = configApi.getInt("comp-season", "current-phase")
            if (currentPhase == null) {
                source.sendRedMessage("Cannot show leaderboard: current-phase is not set")
                return@async
            }
            configApi.configsAddConfigPost(Config(entity = "lobby", key = "show-leaderboard", value = "true"))
            FirstRun.showLeaderboard = true
            source.sendGreenMessage("Showing leaderboard ${if (animate) "with" else "without"} animation")
            if (animate) {
                val seenKey = "leaderboard-seen-phase-$currentPhase"
                plugin.server.onlinePlayers.forEach { p ->
                    configApi.configsAddConfigPost(Config(entity = p.name, key = seenKey, value = "true"))
                }
                plugin.leaderboardTaskRunner.runWithAnimation()
            } else {
                plugin.leaderboardTaskRunner.run()
            }
        }
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

    @Subcommand("leaderboard list")
    @Description("List current leaderboard standings")
    fun listLeaderboard(source: CommandSender) {
        plugin.leaderboardTaskRunner.sendLeaderboardTo(source)
    }
}
