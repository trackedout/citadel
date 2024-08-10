package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Subcommand
import co.aikar.commands.annotation.Syntax
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendGreyMessage
import org.trackedout.citadel.sendMessage
import org.trackedout.citadel.sendRedMessage
import org.trackedout.client.apis.ScoreApi

@CommandAlias("decked-out|do")
class ListScoresCommand(
    private val plugin: Citadel,
    private val scoreApi: ScoreApi,
) : BaseCommand() {
    @Subcommand("list-scores")
    @Syntax("[player]")
    @CommandPermission("decked-out.inventory.admin")
    @Description("List scoreboard values for a player")
    fun listScores(source: CommandSender, args: Array<String>) {
        if (args.size != 1) {
            source.sendGreyMessage("Usage: /decked-out list-scores <Player>")
            return
        }

        val playerName = args[0]
        plugin.async(source) {
            val scores = scoreApi.scoresGet(player = playerName).results!!

            val applicableScores = scores.filter { it.key!!.startsWith("do2.inventory") || it.key!!.contains("do2.lifetime.escaped.crowns") }
            if (applicableScores.isEmpty()) {
                source.sendRedMessage("No applicable scores found for $playerName")
                return@async
            }
            source.sendMessage("$playerName has the following scores:")
            applicableScores.sortedBy { it.key }.forEach { score ->
                listScores(source, scores.associate { it.key!! to it.value!!.toInt() }, score.key!!, score.value!!.toInt())
            }
        }
    }

    private fun listScores(source: CommandSender, scores: Map<String, Int>, key: String, value: Int) {
        when (key) {
            // Shards
            "do2.inventory.shards.practice" -> {
                source.sendGreenMessage("- Practice Shards (${key}) = $value")
            }

            "do2.inventory.shards.competitive" -> {
                source.sendMessage("- Competitive Shards (${key}) = $value", NamedTextColor.AQUA)
            }

            // Crowns
            "practice-do2.lifetime.escaped.crowns" -> {
                val itemCount = value - scores.getOrDefault("practice-do2.lifetime.spent.crowns", 0)
                source.sendGreenMessage("- Practice Crowns (${key}) = $itemCount")
            }

            "competitive-do2.lifetime.escaped.crowns" -> {
                val itemCount = value - scores.getOrDefault("competitive-do2.lifetime.spent.crowns", 0)
                source.sendMessage("- Competitive Crowns (${key}) = $itemCount", NamedTextColor.AQUA)
            }

            // Tomes
            "practice-do2.lifetime.escaped.tomes" -> {
                val itemCount = value - scores.getOrDefault("practice-do2.lifetime.spent.tomes", 0)
                source.sendGreenMessage("- Practice Tomes (${key}) = $itemCount")
            }

            "competitive-do2.lifetime.escaped.tomes" -> {
                val itemCount = value - scores.getOrDefault("competitive-do2.lifetime.spent.tomes", 0)
                source.sendMessage("- Competitive Tomes (${key}) = $itemCount", NamedTextColor.AQUA)
            }
        }
    }
}
