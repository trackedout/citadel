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
import org.trackedout.citadel.SHARDS_SCOREBOARD
import org.trackedout.citadel.ScoreRef
import org.trackedout.citadel.async
import org.trackedout.citadel.displayNamedText
import org.trackedout.citadel.getInventoryRelatedScores
import org.trackedout.citadel.inventory.Trade
import org.trackedout.citadel.isInventoryRelatedScore
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendMessage
import org.trackedout.citadel.sendRedMessage
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.apis.ScoreApi
import org.trackedout.client.models.Card
import org.trackedout.client.models.Event
import org.trackedout.client.models.Score
import org.trackedout.citadel.config.RunType
import org.trackedout.citadel.config.getRunTypesFromCache

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
    @CommandCompletion("@dbPlayers @nothing")
    fun listScores(source: CommandSender, playerName: String) {
        plugin.async(source) {
            val scores = scoreApi.getInventoryRelatedScores(plugin.leagueCache, plugin.catalogCache, playerName)

            val applicableScores = scores.filter { isInventoryRelatedScore(plugin.catalogCache, it) }
            if (applicableScores.isEmpty()) {
                source.sendRedMessage("No applicable scores found for $playerName")
                return@async
            }

            source.sendMessage("$playerName has the following scores (minus spent):")
            val scoreValues = scores.associate { ScoreRef(it.slug!!, it.key!!) to it.value!!.toInt() }
            for (runType in getRunTypesFromCache(plugin.leagueCache)) {
                applicableScores.filter { it.slug == runType.longId }.sortedBy { it.key }.forEach { score ->
                    listScore(source, runType, scoreValues, score.key!!, score.value!!.toInt())
                }
            }
        }
    }

    private fun listScore(source: CommandSender, runType: RunType, scores: Map<ScoreRef, Int>, key: String, value: Int) {
        fun spent(spentKey: String) = scores.getOrDefault(ScoreRef(runType.longId, spentKey), 0)
        val label = when (key) {
            SHARDS_SCOREBOARD -> "Shards" to value
            // Escaped minus spent
            "do2.lifetime.escaped.shard_fragments" -> "Shard Fragments" to value - spent("do2.lifetime.spent.shard_fragments")
            "do2.lifetime.escaped.crowns" -> "Crowns" to value - spent("do2.lifetime.spent.crowns")
            "do2.lifetime.escaped.tomes" -> "Tomes" to value - spent("do2.lifetime.spent.tomes")
            else -> return
        }

        source.sendMessage("- ${runType.displayName} ${label.first} (${key}) = ${label.second}", runType.displayNamedText())
    }

    @Subcommand("give-item")
    @CommandPermission("decked-out.shop.admin")
    @Description("Update player's score to give them more items")
    @CommandCompletion("@dbPlayers @runTypes @items @range:1-100 @nothing")
    fun giveScore(source: CommandSender, playerName: String, runType: RunType, item: String, count: Int, reason: String) {
        val catalog = plugin.catalogCache.require(runType.longId)
        val trade = Trade(
            runType = runType.longId,
            sourceType = "dummy",
            sourceItemCount = 0,
            targetType = item,
            targetItemCount = count,
            catalog = catalog,
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
                        "slug" to trade.runType,
                        "source-scoreboard" to trade.sourceScoreboardName(),
                        "source-inversion-scoreboard" to trade.sourceInversionScoreboardName(),
                        "source-count" to trade.sourceItemCount.toString(),
                        "target-scoreboard" to trade.targetScoreboardName(),
                        "target-count" to trade.targetItemCount.toString(),
                        "reason" to "Granted by ${source.name} - $reason",
                    )
                )
            )

            if (catalog.isIntoDungeonItem(item)) {
                plugin.logger.info("Target type is an dungeon item: $item")

                val targetItem = item
                val itemsToAdd = count
                plugin.logger.info("Adding ${itemsToAdd}x $targetItem (item) to ${playerName}'s ${runType.displayName} deck")
                source.sendGreenMessage("Added ${itemsToAdd}x $targetItem to ${playerName}'s ${runType.displayName} deck")
                (0.until(itemsToAdd)).forEach { _ ->
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

}
