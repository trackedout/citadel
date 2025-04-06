package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Subcommand
import co.aikar.commands.annotation.Syntax
import org.bukkit.command.CommandSender
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.InventoryManager
import org.trackedout.citadel.async
import org.trackedout.citadel.displayNamedText
import org.trackedout.citadel.getInventoryRelatedScores
import org.trackedout.citadel.inventory.Trade
import org.trackedout.citadel.inventory.intoDungeonItems
import org.trackedout.citadel.isInventoryRelatedScore
import org.trackedout.citadel.listeners.getCostForCrownTrade
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendMessage
import org.trackedout.citadel.sendRedMessage
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.apis.ScoreApi
import org.trackedout.client.models.Card
import org.trackedout.client.models.Event
import org.trackedout.data.RunType
import org.trackedout.data.runTypes

@CommandAlias("decked-out|do")
class ScoreManagementCommand(
    private val plugin: Citadel,
    private val scoreApi: ScoreApi,
    private val eventsApi: EventsApi,
    private val inventoryManager: InventoryManager,
    private val inventoryApi: InventoryApi,
) : BaseCommand() {

    @Subcommand("list-scores")
    @Syntax("<player>")
    @CommandPermission("decked-out.inventory.admin")
    @Description("List scoreboard values for a player")
    @CommandCompletion("@dbPlayers")
    fun listScores(source: CommandSender, playerName: String) {
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
        for (runType in runTypes) {
            when (key) {
                // Shards
                "do2.inventory.shards.${runType.longId}" -> {
                    source.sendMessage("- ${runType.displayName} Shards (${key}) = $value", runType.displayNamedText())
                }

                // Crowns
                "${runType.longId}-do2.lifetime.escaped.crowns" -> {
                    val itemCount = value - scores.getOrDefault("${runType.longId}-do2.lifetime.spent.crowns", 0)
                    source.sendMessage("- ${runType.displayName} Crowns (${key}) = $itemCount", runType.displayNamedText())
                }

                // Tomes
                "${runType.longId}-do2.lifetime.escaped.tomes" -> {
                    val itemCount = value - scores.getOrDefault("${runType.longId}-do2.lifetime.spent.tomes", 0)
                    source.sendMessage("- ${runType.displayName} Tomes (${key}) = $itemCount", runType.displayNamedText())
                }
            }
        }
    }

    @Subcommand("give-item")
    @CommandPermission("decked-out.shop.admin")
    @Description("Update player's score to give them more items")
    @CommandCompletion("@dbPlayers @runTypes @items @range:1-100 @nothing")
    fun giveScore(source: CommandSender, playerName: String, runType: RunType, item: String, count: Int, reason: String) {
        val trade = Trade(
            runType = runType.longId,
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
                plugin.logger.info("Adding ${itemsToAdd}x $targetItem (item) to ${playerName}'s ${runType.displayName} deck")
                source.sendGreenMessage("Added ${itemsToAdd}x $targetItem to ${playerName}'s ${runType.displayName} deck")
                (0 until itemsToAdd).map {
                    inventoryApi.inventoryAddCardPost(
                        Card(
                            player = playerName,
                            name = targetItem,
                            deckType = runType.deckType(),
                            server = plugin.serverName,
                        )
                    )
                }
            }

            plugin.server.onlinePlayers.find { it.name == playerName }?.let { player ->
                inventoryManager.updateInventoryBasedOnScore(player)
            }
            source.sendGreenMessage("Successfully added ${count}x${item} (${runType.longId}) to ${playerName}'s inventory")
        }
    }

    @Subcommand("list-shard-costs")
    @Syntax("<player>")
    @CommandPermission("decked-out.inventory.admin")
    @Description("Calculate and list shard cost for a player")
    @CommandCompletion("@dbPlayers")
    fun listShardCosts(source: CommandSender, playerName: String) {
        plugin.async(source) {
            source.sendMessage("$playerName has the following shard costs:")
            for (runType in runTypes) {
                getCostForCrownTrade(plugin, playerName, runType).let { cost ->
                    source.sendMessage("- ${runType.displayName} = ${cost ?: 10} crowns", runType.displayNamedText())
                }
            }
        }
    }
}
