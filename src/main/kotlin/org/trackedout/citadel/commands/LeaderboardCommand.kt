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
import org.trackedout.citadel.runOnNextTick

import org.trackedout.citadel.async
import org.trackedout.citadel.config.leaderboardScope
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
            plugin.serverSettings.updateLobby { it.copy(showLeaderboard = false) }
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
            val scope = plugin.competitivePhases.current()?.leaderboardScope()
            if (scope == null) {
                source.sendRedMessage("Cannot show leaderboard: no competitive league has a completed phase")
                return@async
            }
            plugin.serverSettings.updateLobby { it.copy(showLeaderboard = true) }
            FirstRun.showLeaderboard = true
            source.sendGreenMessage("Showing leaderboard ${if (animate) "with" else "without"} animation")
            if (animate) {
                plugin.server.onlinePlayers.forEach { p ->
                    configApi.configsAddConfigPost(Config(entity = p.name, key = scope.seenKey, value = "true"))
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
            val hardcoreSlug = plugin.serverSettings.leagues()?.hardcore ?: run {
                source.sendRedMessage("Settings are not loaded, cannot tell which league is hardcore")
                return@async
            }

            val database = MongoDBManager.getDatabase("dunga-dunga")
            val scoreCollection = database.getCollection("scores", MongoScore::class.java)

            val scores = scoreCollection.find(
                Filters.and(
                    Filters.eq("slug", hardcoreSlug),
                    Filters.eq("key", "leaderboard-do2.lifetime.escaped.tomes")
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
