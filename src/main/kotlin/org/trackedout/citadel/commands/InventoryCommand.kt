package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import org.bukkit.entity.Player
import org.trackedout.citadel.*
import org.trackedout.citadel.data.Cards
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.models.Card

@CommandAlias("decked-out|do") // Spelling is intentional
class InventoryCommand(
    private val eventsApi: EventsApi,
    private val inventoryApi: InventoryApi
) : BaseCommand() {
    @Dependency
    private lateinit var plugin: Citadel

    @Subcommand("add-card")
    @Syntax("[player] [card-name]")
    @CommandPermission("decked-out.inventory.admin")
    @Description("Add Decked Out 2 card into player's DB inventory")
    fun addCard(player: Player, args: Array<String>) {
        mutateInventory("add", player, args)
    }

    @Subcommand("remove-card")
    @Syntax("[player] [card-name]")
    @CommandPermission("decked-out.inventory.admin")
    @Description("Remove a Decked Out 2 card from player's DB inventory")
    fun removeCard(player: Player, args: Array<String>) {
        mutateInventory("remove", player, args)
    }

    @Subcommand("list-cards")
    @Syntax("[player]")
    @CommandPermission("decked-out.inventory.admin")
    @Description("List the Decked Out 2 cards in a player's DB inventory")
    fun listCards(player: Player, args: Array<String>) {
        if (!player.isOp) {
            player.sendRedMessage("You need to be an operator to use this command")
            return
        }

        if (args.size != 1) {
            player.sendGreyMessage("Usage: /decked-out list-cards <Player>")
            return
        }

        val target = args[0]
        plugin.async {
            val cards = inventoryApi.inventoryCardsGet(
                player = target,
                limit = 200,
                deckId = "1",
            ).results!!

            val cardCount = cards.sortedBy { it.name }.groupingBy { it.name!! }.eachCount()
            player.sendGreyMessage("${player.name}'s shulker contains ${cards.size} cards:")
            cardCount.forEach { (cardName, count) -> player.sendGreyMessage("${count}x $cardName") }
        }
    }

    private fun mutateInventory(action: String, player: Player, args: Array<String>) {
        if (!player.isOp) {
            player.sendRedMessage("You need to be an operator to use this command")
            return
        }
        if (args.size != 2) {
            player.sendGreyMessage("Usage: /decked-out $action-card <Player> <card-name>")
            return
        }

        val target = args[0]
        val cardName = args[1].let {
            try {
                Cards.cardModelData(it)
                it
            } catch (e: Exception) {
                player.sendRedMessage("Unknown card: $it")
                return
            }
        }

        try {
            when (action) {
                "add" -> {
                    inventoryApi.inventoryAddCardPost(
                        Card(
                            player = target,
                            name = cardName,
                            deckId = "1",
                            server = plugin.serverName,
                        )
                    )
                    plugin.logger.info("Added $cardName to $target's deck")
                    player.sendGreenMessage("Added $cardName to $target's deck")
                }

                "remove" -> {
                    inventoryApi.inventoryDeleteCardPost(
                        Card(
                            player = target,
                            name = cardName,
                            deckId = "1",
                            server = plugin.serverName,
                        )
                    )
                    plugin.logger.info("Removed $cardName from $target's deck")
                    player.sendGreenMessage("Removed $cardName from $target's deck")
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            plugin.logger.severe("Failed to $action $cardName to/from $target's deck. Exception: $e")
            player.sendRedMessage("Failed to $action $cardName to $target's deck. Exception: $e")
        }
    }
}