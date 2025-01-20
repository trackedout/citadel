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
import org.trackedout.citadel.InventoryManager
import org.trackedout.citadel.async
import org.trackedout.citadel.getInventoryRelatedScores
import org.trackedout.citadel.inventory.Trade
import org.trackedout.citadel.inventory.intoDungeonItems
import org.trackedout.citadel.inventory.shortRunType
import org.trackedout.citadel.isInventoryRelatedScore
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendGreyMessage
import org.trackedout.citadel.sendMessage
import org.trackedout.citadel.sendRedMessage
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.apis.ScoreApi
import org.trackedout.client.models.Card
import org.trackedout.client.models.Event

@CommandAlias("decked-out|do")
class ScoreManagementCommand(
    private val plugin: Citadel,
    private val scoreApi: ScoreApi,
    private val eventsApi: EventsApi,
    private val inventoryManager: InventoryManager,
    private val inventoryApi: InventoryApi,
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
            val scores = scoreApi.getInventoryRelatedScores(playerName)

            val applicableScores = scores.filter(::isInventoryRelatedScore)
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

    enum class RunTypes(val runType: String) {
        PRACTICE("practice"),
        COMPETITIVE("competitive"),
    }

    @Subcommand("give-item")
    @CommandPermission("decked-out.shop.admin")
    @Description("Update player's score to give them more items")
    fun giveScore(source: CommandSender, playerName: String, runType: RunTypes, item: String, count: Int, reason: String) {
        val trade = Trade(
            runType = runType.runType,
            sourceType = "dummy",
            sourceItemCount = 0,
            targetType = item,
            targetItemCount = count,
        )

        val sourceScoreboardName = trade.sourceScoreboardName()
        plugin.logger.info("Source scoreboard name: $sourceScoreboardName")

        plugin.async(source) {
            eventsApi.eventsPost(
                Event(
                    name = "trade-requested",
                    server = plugin.serverName,
                    player = playerName,
                    count = 1,
                    x = 0.0,
                    y = 0.0,
                    z = 0.0,
                    metadata = mapOf(
                        "run-type" to trade.runType,
                        "source-scoreboard" to trade.sourceScoreboardName(),
                        "source-inversion-scoreboard" to trade.sourceInversionScoreboardName(),
                        "source-count" to trade.sourceItemCount.toString(),
                        "target-scoreboard" to trade.targetScoreboardName(),
                        "target-count" to trade.targetItemCount.toString(),
                        "reason" to "Granted by ${source.name} - $reason",
                    )
                )
            )

            if (intoDungeonItems.keys.contains(item)) {
                plugin.logger.info("Target type is an dungeon item: $item")

                val targetItem = item
                val itemsToAdd = count
                plugin.logger.info("Adding ${itemsToAdd}x $targetItem (item) to ${playerName}'s deck")
                source.sendGreenMessage("Added ${itemsToAdd}x $targetItem to ${playerName}'s ${runType.runType} deck")
                (0 until itemsToAdd).map {
                    inventoryApi.inventoryAddCardPost(
                        Card(
                            player = playerName,
                            name = targetItem,
                            deckType = runType.runType.shortRunType(),
                            server = plugin.serverName,
                        )
                    )
                }
            }

            plugin.server.onlinePlayers.find { it.name == playerName }?.let { player ->
                inventoryManager.updateInventoryBasedOnScore(player)
            }
            source.sendGreenMessage("Successfully added ${count}x${item} (${runType.runType}) to ${playerName}'s inventory")
        }
    }
}
