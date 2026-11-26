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
import org.trackedout.citadel.inventory.DeckCopyPlan
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendGreyMessage
import org.trackedout.citadel.sendMiniMessage
import org.trackedout.citadel.sendRedMessage
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.models.Card

private const val CONFIRM_FLAG = "--confirm"

@CommandAlias("decked-out|do")
class CopyDeckCommand(
    private val plugin: Citadel,
    private val inventoryApi: InventoryApi,
) : BaseCommand() {
    @Subcommand("copy-deck")
    @Syntax("<sourceDeck> [--confirm]")
    @CommandPermission("decked-out.inventory.edit")
    @Description("Replace your practice deck with a copy of another deck. Add --confirm to do it")
    @CommandCompletion("@runTypes @nothing")
    fun copyDeck(player: Player, source: RunType, @Optional args: Array<String>?) {
        val practiceSlug = plugin.serverSettings.leagues()?.practice
        if (practiceSlug == null) {
            player.sendRedMessage("Settings are not loaded, try again in a moment")
            return
        }

        if (source.longId == practiceSlug) {
            player.sendRedMessage("Pick a deck other than your practice deck to copy from")
            return
        }

        val confirmed = args.orEmpty().contains(CONFIRM_FLAG)
        plugin.async(player) { copyIntoPractice(player, source, practiceSlug, confirmed) }
    }

    private fun copyIntoPractice(player: Player, source: RunType, practiceSlug: String, confirmed: Boolean) {
        val practice = plugin.leagueCache.getLeagueBySlug(practiceSlug)?.displayName ?: practiceSlug
        val catalog = plugin.catalogCache.require(practiceSlug)
        val validEntries = catalog.cards.values.map { it.shorthand }.toSet() + catalog.tradeItems.keys

        val sourceCards = cardsOf(player, source.longId)
        val practiceCards = cardsOf(player, practiceSlug)
        val plan = DeckCopyPlan.of(sourceCards.map { it.name!! }, practiceCards.map { it.name!! }, validEntries)

        if (plan.notInTargetCatalog.isNotEmpty()) {
            player.sendGreyMessage("Skipping ${plan.notInTargetCatalog.values.sum()} entries that do not exist in $practice: ${plan.notInTargetCatalog.keys.joinToString()}")
        }

        if (plan.isNoop) {
            player.sendGreenMessage("Your $practice deck already matches your ${source.displayName} deck")
            return
        }

        val summary = "${plan.toAdd.values.sum()} to add, ${plan.toRemove.values.sum()} to remove"
        if (!confirmed) {
            player.sendMiniMessage(
                "<yellow>This replaces your $practice deck with your ${source.displayName} deck ($summary). " +
                    "Run <gold><click:suggest_command:'/do copy-deck ${source.longId} $CONFIRM_FLAG'>/do copy-deck ${source.longId} $CONFIRM_FLAG</click></gold> to continue</yellow>",
            )
            return
        }

        plan.toRemove.forEach { (name, count) -> repeat(count) { inventoryApi.inventoryDeleteCardPost(card(player, name, practiceSlug)) } }
        plan.toAdd.forEach { (name, count) -> repeat(count) { inventoryApi.inventoryAddCardPost(card(player, name, practiceSlug)) } }

        plugin.logger.info("${player.name} copied their ${source.longId} deck to $practiceSlug ($summary)")
        player.sendGreenMessage("Copied your ${source.displayName} deck to your $practice deck ($summary). Reopen /do edit to see it")
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
