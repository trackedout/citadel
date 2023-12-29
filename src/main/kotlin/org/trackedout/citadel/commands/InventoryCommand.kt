package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.bukkit.entity.Player
import org.trackedout.citadel.*
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.models.Card
import org.trackedout.data.Cards

@CommandAlias("decked-out|do")
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
            player.sendGreyMessage("${target}'s shulker contains ${cards.size} cards:")
            cardCount.forEach { (cardName, count) -> player.sendGreyMessage("${count}x $cardName") }
        }
    }

    @Subcommand("remove-all-cards")
    @Syntax("[player]")
    @CommandPermission("decked-out.inventory.admin")
    @Description("Remove all Decked Out 2 cards from a player's DB inventory")
    fun removeAllCards(player: Player, args: Array<String>) {
        if (args.size != 1) {
            player.sendGreyMessage("Usage: /decked-out remove-all-cards <Player>")
            return
        }

        val target = args[0]
        plugin.async {
            val cards = inventoryApi.inventoryCardsGet(
                player = target,
                limit = 200,
                deckId = "1",
            ).results!!

            player.sendGreyMessage("Deleting ${cards.size} cards from ${target}'s deck...")
            cards.forEach(inventoryApi::inventoryDeleteCardPost)
            player.sendGreenMessage("Deleted ${cards.size} cards from ${target}'s deck!")
        }
    }

    @Subcommand("list-known-cards")
    @Syntax("[player]")
    @CommandPermission("decked-out.inventory.list-known")
    @Description("Add a copy of every known card to a player's DB inventory")
    fun listAllKnownCards(player: Player) {
        val knownCards = Cards.Companion.Card.entries.sortedBy { it.colour }.reversed()
        player.sendGreenMessage("Decked Out 2 has the following cards:")
        knownCards.forEach {
            var textColor = TextColor.fromHexString(it.colour)
            if (textColor == null) {
                textColor = NamedTextColor.NAMES.value(it.colour)
            }
            if (textColor == null) {
                textColor = TextColor.color(NamedTextColor.GRAY)
            }
            player.sendMessage(Component.text().color(textColor).content("- ${it.displayName}").build())
        }
    }

    @Subcommand("add-all-known-cards")
    @Syntax("[player]")
    @CommandPermission("decked-out.inventory.admin")
    @Description("Add a copy of every known card to a player's DB inventory")
    fun addAllKnownCards(player: Player, args: Array<String>) {
        if (args.size != 1) {
            player.sendGreyMessage("Usage: /decked-out add-all-known-cards <Player>")
            return
        }

        val target = args[0]
        plugin.async {
            val knownCards = Cards.Companion.Card.entries
            player.sendGreyMessage("Adding ${knownCards.size} cards to ${target}'s deck...")

            knownCards.forEach {
                inventoryApi.inventoryAddCardPost(
                    Card(
                        player = target,
                        name = it.key,
                        deckId = "1",
                        server = plugin.serverName,
                    )
                )
            }

            player.sendGreenMessage("Added ${knownCards.size} cards to ${target}'s deck!")
        }
    }

    private fun mutateInventory(action: String, player: Player, args: Array<String>) {
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
                "add" -> plugin.async {
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

                "remove" -> plugin.async {
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