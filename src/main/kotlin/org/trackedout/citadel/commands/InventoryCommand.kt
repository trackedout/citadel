package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Dependency
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Subcommand
import co.aikar.commands.annotation.Syntax
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.InventoryManager
import org.trackedout.citadel.async
import org.trackedout.citadel.config.cardConfig
import org.trackedout.citadel.inventory.fullRunType
import org.trackedout.citadel.inventory.isValidRunType
import org.trackedout.citadel.inventory.shortRunType
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendGreyMessage
import org.trackedout.citadel.sendRedMessage
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.models.Card
import org.trackedout.data.find
import org.trackedout.data.sortedList

@CommandAlias("decked-out|do")
class InventoryCommand(
    private val eventsApi: EventsApi,
    private val inventoryApi: InventoryApi,
    private val inventoryManager: InventoryManager,
) : BaseCommand() {
    @Dependency
    private lateinit var plugin: Citadel

    @Subcommand("update-inventory")
    @Syntax("[player]")
    @CommandPermission("decked-out.inventory.admin")
    @Description("Update a player's inventory using DB state")
    fun updateInventory(source: CommandSender, args: Array<String>) {
        if (args.size != 1) {
            source.sendGreyMessage("Usage: /decked-out update-inventory <Player>")
            return
        }

        plugin.server.onlinePlayers.filter { it.name == args[0] || args[0] == "ALL" }.forEach { player ->
            inventoryManager.updateInventoryBasedOnScore(player)
            source.sendGreenMessage("Updated ${player.name}'s inventory")
        }
    }

    @Subcommand("add-card")
    @Syntax("[player] [card-name]")
    @CommandPermission("decked-out.inventory.admin")
    @Description("Add Decked Out 2 card into player's DB inventory")
    fun addCard(sender: CommandSender, args: Array<String>) {
        mutateInventory("add", sender, args)
    }

    @Subcommand("remove-card")
    @Syntax("[player] [card-name]")
    @CommandPermission("decked-out.inventory.admin")
    @Description("Remove a Decked Out 2 card from player's DB inventory")
    fun removeCard(source: CommandSender, args: Array<String>) {
        mutateInventory("remove", source, args)
    }

    @Subcommand("list-cards")
    @Syntax("[player]")
    @CommandPermission("decked-out.inventory.admin")
    @Description("List the Decked Out 2 cards in a player's DB inventory")
    fun listCards(source: CommandSender, args: Array<String>) {
        if (args.size != 1) {
            source.sendGreyMessage("Usage: /decked-out list-cards <Player>")
            return
        }

        val target = args[0]
        plugin.async(source) {
            val cards = inventoryApi.inventoryCardsGet(
                player = target,
                limit = 200,
                deckId = "1",
            ).results!!

            val knownCards = cardConfig.sortedList()
            cards.map { it.deckType }.distinct().forEach { deckType ->
                val cardsForRunType = cards.filter { it.deckType == deckType }
                source.sendGreenMessage("${target}'s ${deckType?.fullRunType()} deck contains ${cardsForRunType.size} cards:")

                knownCards.forEach {
                    var textColor = it.tag.nameFormat?.color?.let(TextColor::fromHexString)
                    if (textColor == null) {
                        textColor = TextColor.color(NamedTextColor.GRAY)
                    }

                    val count = cardsForRunType.count { card -> card.name == it.shorthand }
                    if (count > 0) {
                        source.sendMessage(Component.text().color(textColor).content("${count}x ${it.name}").build())
                    }
                }
            }
        }
    }

    @Subcommand("remove-all-cards")
    @Syntax("[player]")
    @CommandPermission("decked-out.inventory.admin")
    @Description("Remove all Decked Out 2 cards from a player's DB inventory")
    fun removeAllCards(source: CommandSender, args: Array<String>) {
        if (args.size != 2) {
            source.sendGreyMessage("Usage: /decked-out remove-all-cards <Player> <deckType (p/c)>")
            return
        }

        val target = args[0]
        val deckType = args[1].shortRunType()
        if (!deckType.isValidRunType()) {
            source.sendRedMessage("Invalid deckType: $deckType")
            return
        }

        plugin.async(source) {
            val cards = inventoryApi.inventoryCardsGet(
                player = target,
                limit = 200,
                deckType = deckType,
            ).results!!

            source.sendGreyMessage("Deleting ${cards.size} cards from ${target}'s ${deckType.fullRunType()} deck...")
            cards.forEach {
                inventoryApi.inventoryDeleteCardPost(
                    Card(
                        player = it.player,
                        name = it.name,
                        deckType = deckType,
                    )
                )
            }
            source.sendGreenMessage("Deleted ${cards.size} cards from ${target}'s ${deckType.fullRunType()} deck!")
        }
    }

    @Subcommand("list-known-cards")
    @Syntax("[player]")
    @CommandPermission("decked-out.inventory.list-known")
    @Description("List all known Decked Out 2 cards")
    fun listAllKnownCards(player: Player) {
        val knownCards = cardConfig.sortedList()
        player.sendGreenMessage("Decked Out 2 has the following cards:")
        knownCards.forEach {
            var textColor = it.tag.nameFormat?.color?.let(TextColor::fromHexString)
            if (textColor == null) {
                textColor = TextColor.color(NamedTextColor.GRAY)
            }

//            val content = "- ${it.name}${it.emberValue?.let { cost -> " ($cost embers)" } ?: ""}"
            player.sendMessage(Component.text().color(textColor).content(it.name).build())
        }
    }

    @Subcommand("add-all-known-cards")
    @Syntax("[player]")
    @CommandPermission("decked-out.inventory.admin")
    @Description("Add a copy of every known card to a player's DB inventory")
    fun addAllKnownCards(source: CommandSender, args: Array<String>) {
        if (args.size != 2) {
            source.sendGreyMessage("Usage: /decked-out add-all-known-cards <Player> <deckType (p/c)>")
            return
        }

        val target = args[0]
        val deckType = args[1].shortRunType()
        if (!deckType.isValidRunType()) {
            source.sendRedMessage("Invalid deckType: $deckType")
            return
        }

        plugin.async(source) {
            val knownCards = cardConfig.values.map { it.shorthand }
            source.sendGreyMessage("Adding ${knownCards.size} cards to ${target}'s ${deckType.fullRunType()} deck...")

            knownCards.forEach {
                inventoryApi.inventoryAddCardPost(
                    Card(
                        player = target,
                        name = it,
                        deckType = deckType,
                        server = plugin.serverName,
                    )
                )
            }

            source.sendGreenMessage("Added ${knownCards.size} cards to ${target}'s ${deckType.fullRunType()} deck!")
        }
    }

    private fun mutateInventory(action: String, source: CommandSender, args: Array<String>) {
        if (args.size != 3) {
            source.sendGreyMessage("Usage: /decked-out $action-card <Player> <card-name> <deckType (p/c)>")
            return
        }

        val target = args[0]
        val cardName = args[1].let {
            try {
                cardConfig.find(it)!!.shorthand
            } catch (e: Exception) {
                source.sendRedMessage("Unknown card: $it")
                return
            }
        }

        val deckType = args[2].shortRunType()
        if (!deckType.isValidRunType()) {
            source.sendRedMessage("Invalid deckType: $deckType")
            return
        }

        try {
            when (action) {
                "add" -> plugin.async(source) {
                    inventoryApi.inventoryAddCardPost(
                        Card(
                            player = target,
                            name = cardName,
                            deckType = deckType,
                            server = plugin.serverName,
                        )
                    )
                    plugin.logger.info("Added $cardName to $target's ${deckType.fullRunType()} deck")
                    source.sendGreenMessage("Added $cardName to $target's ${deckType.fullRunType()} deck")
                }

                "remove" -> plugin.async(source) {
                    inventoryApi.inventoryDeleteCardPost(
                        Card(
                            player = target,
                            name = cardName,
                            deckType = deckType,
                        )
                    )
                    plugin.logger.info("Removed $cardName from $target's ${deckType.fullRunType()} deck")
                    source.sendGreenMessage("Removed $cardName from $target's ${deckType.fullRunType()} deck")
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            plugin.logger.severe("Failed to $action $cardName to/from $target's ${deckType.fullRunType()} deck. Exception: $e")
            source.sendRedMessage("Failed to $action $cardName to $target's ${deckType.fullRunType()} deck. Exception: $e")
        }
    }
}
