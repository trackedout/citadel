package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Optional
import co.aikar.commands.annotation.Subcommand
import co.aikar.commands.annotation.Syntax
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.config.RunType
import org.trackedout.citadel.config.acceptsDeckCopyFrom
import org.trackedout.citadel.config.copySources
import org.trackedout.citadel.config.getRunTypeByIdFromCache
import org.trackedout.citadel.inventory.DeckCopyPlan
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendGreyMessage
import org.trackedout.citadel.sendMiniMessage
import org.trackedout.citadel.sendRedMessage
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.models.Card
import org.trackedout.client.models.League

private const val CONFIRM_FLAG = "--confirm"

@CommandAlias("decked-out|do")
class CopyDeckCommand(
    private val plugin: Citadel,
    private val inventoryApi: InventoryApi,
) : BaseCommand() {
    @Subcommand("copy-deck")
    @Syntax("<sourceDeck> <targetDeck> [--confirm]")
    @CommandPermission("decked-out.inventory.edit")
    @Description("Replace one of your decks with a copy of another, where the league allows it. Add --confirm to do it")
    @CommandCompletion("@copySources @copyTargets @nothing")
    fun copyDeck(player: Player, source: RunType, target: RunType, @Optional args: Array<String>?) {
        val targetLeague = plugin.leagueCache.getLeagueBySlug(target.longId)
        if (targetLeague == null || !targetLeague.acceptsDeckCopyFrom(source.longId)) {
            player.sendRedMessage(refusal(source, target, targetLeague))
            return
        }

        val confirmed = args.orEmpty().contains(CONFIRM_FLAG)
        plugin.async(player) { copyDeck(player, source, target, confirmed) }
    }

    private fun refusal(source: RunType, target: RunType, targetLeague: League?): String {
        val sources = targetLeague?.copySources().orEmpty()
        if (sources.isEmpty()) {
            return "You cannot copy a deck into ${target.displayName}"
        }
        val names = sources.joinToString { getRunTypeByIdFromCache(plugin.leagueCache, it).displayName }
        return "You cannot copy ${source.displayName} into ${target.displayName}. It accepts copies of: $names"
    }

    private fun copyDeck(player: Player, source: RunType, target: RunType, confirmed: Boolean) {
        val catalog = plugin.catalogCache.require(target.longId)
        val validEntries = catalog.cards.values.map { it.shorthand }.toSet() + catalog.tradeItems.keys

        val sourceCards = cardsOf(player, source.longId)
        val targetCards = cardsOf(player, target.longId)
        val plan = DeckCopyPlan.of(sourceCards.map { it.name!! }, targetCards.map { it.name!! }, validEntries)

        if (plan.notInTargetCatalog.isNotEmpty()) {
            player.sendGreyMessage("Skipping ${plan.notInTargetCatalog.values.sum()} entries that do not exist in ${target.displayName}: ${plan.notInTargetCatalog.keys.joinToString()}")
        }

        if (plan.isNoop) {
            player.sendGreenMessage("Your ${target.displayName} deck already matches your ${source.displayName} deck")
            return
        }

        val summary = "${plan.toAdd.values.sum()} to add, ${plan.toRemove.values.sum()} to remove"
        if (!confirmed) {
            val command = "/do copy-deck ${source.longId} ${target.longId} $CONFIRM_FLAG"
            player.sendMiniMessage(
                "<yellow>This replaces your ${target.displayName} deck with your ${source.displayName} deck ($summary). " +
                    "Run <gold><click:suggest_command:'$command'>$command</click></gold> to continue</yellow>",
            )
            return
        }

        plan.toRemove.forEach { (name, count) -> repeat(count) { inventoryApi.inventoryDeleteCardPost(card(player, name, target.longId)) } }
        plan.toAdd.forEach { (name, count) -> repeat(count) { inventoryApi.inventoryAddCardPost(card(player, name, target.longId)) } }

        plugin.logger.info("${player.name} copied their ${source.longId} deck to ${target.longId} ($summary)")
        player.sendGreenMessage("Copied your ${source.displayName} deck to your ${target.displayName} deck ($summary)")
    }

    private fun cardsOf(player: Player, deckType: String): List<Card> =
        inventoryApi.inventoryCardsGet(player = player.name, limit = 1000, deckType = deckType).results.orEmpty()

    private fun card(player: Player, name: String, deckType: String) = Card(
        player = player.name,
        name = name,
        deckType = deckType,
        server = plugin.serverName,
    )
}
