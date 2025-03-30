package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
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
import org.trackedout.citadel.inventory.displayName
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendGreyMessage
import org.trackedout.citadel.sendRedMessage
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.models.Card
import org.trackedout.data.RunType
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
    @Syntax("<player>")
    @CommandPermission("decked-out.inventory.admin")
    @Description("Update a player's inventory using DB state")
    fun updateInventory(source: CommandSender, target: String) {
        plugin.server.onlinePlayers.filter { it.name == target || target == "ALL" }.forEach { player ->
            inventoryManager.updateInventoryBasedOnScore(player)
            source.sendGreenMessage("Updated ${player.name}'s inventory")
        }
    }

    @Subcommand("add-card")
    @CommandPermission("decked-out.inventory.admin")
    @Description("Add Decked Out 2 card into player's DB inventory")
    @CommandCompletion("@dbPlayers @cards @runTypes")
    fun addCard(source: CommandSender, target: String, cardName: String, runType: RunType) {
        mutateInventory("add", source, target, cardName, runType)
    }

    @Subcommand("remove-card")
    @CommandPermission("decked-out.inventory.admin")
    @Description("Remove a Decked Out 2 card from player's DB inventory")
    @CommandCompletion("@dbPlayers @cards @runTypes")
    fun removeCard(source: CommandSender, target: String, cardName: String, runType: RunType) {
        mutateInventory("remove", source, target, cardName, runType)
    }

    @Subcommand("list-cards")
    @Syntax("<player>")
    @CommandPermission("decked-out.inventory.admin")
    @Description("List the Decked Out 2 cards in a player's DB inventory")
    @CommandCompletion("@dbPlayers")
    fun listCards(source: CommandSender, target: String) {
        plugin.async(source) {
            val cards = inventoryApi.inventoryCardsGet(
                player = target,
                limit = 200,
                deckId = "1",
            ).results!!

            val knownCards = cardConfig.sortedList()
            cards.map { it.deckType }.distinct().forEach { deckType ->
                val cardsForRunType = cards.filter { it.deckType == deckType }
                source.sendGreenMessage("${target}'s ${deckType?.displayName()} deck contains ${cardsForRunType.size} cards:")

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
    @Syntax("<player> <runType>")
    @CommandPermission("decked-out.inventory.admin")
    @Description("Remove all Decked Out 2 cards from a player's DB inventory")
    @CommandCompletion("@dbPlayers @runTypes")
    fun removeAllCards(source: CommandSender, target: String, runType: RunType) {
        plugin.async(source) {
            val cards = inventoryApi.inventoryCardsGet(
                player = target,
                limit = 200,
                deckType = runType.deckType(),
            ).results!!

            source.sendGreyMessage("Deleting ${cards.size} cards from ${target}'s ${runType.displayName} deck...")
            cards.forEach {
                inventoryApi.inventoryDeleteCardPost(
                    Card(
                        player = it.player,
                        name = it.name,
                        deckType = runType.deckType(),
                    )
                )
            }
            source.sendGreenMessage("Deleted ${cards.size} cards from ${target}'s ${runType.displayName} deck!")
        }
    }

    @Subcommand("list-known-cards")
    @Syntax("<player>")
    @CommandPermission("decked-out.inventory.list-known")
    @Description("List all known Decked Out 2 cards")
    @CommandCompletion("@dbPlayers")
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
    @Syntax("<player> <runType>")
    @CommandPermission("decked-out.inventory.admin")
    @Description("Add a copy of every known card to a player's DB inventory")
    @CommandCompletion("@dbPlayers @runTypes")
    fun addAllKnownCards(source: CommandSender, target: String, runType: RunType) {
        plugin.async(source) {
            val knownCards = cardConfig.values.map { it.shorthand }
            source.sendGreyMessage("Adding ${knownCards.size} cards to ${target}'s ${runType.displayName} deck...")

            knownCards.forEach {
                inventoryApi.inventoryAddCardPost(
                    Card(
                        player = target,
                        name = it,
                        deckType = runType.deckType(),
                        server = plugin.serverName,
                    )
                )
            }

            source.sendGreenMessage("Added ${knownCards.size} cards to ${target}'s ${runType.displayName} deck!")
        }
    }

    private fun mutateInventory(action: String, source: CommandSender, target: String, cardName: String, runType: RunType) {
        val cardName = cardName.let {
            try {
                cardConfig.find(it)!!.shorthand
            } catch (e: Exception) {
                source.sendRedMessage("Unknown card: $it")
                return
            }
        }

        try {
            when (action) {
                "add" -> plugin.async(source) {
                    inventoryApi.inventoryAddCardPost(
                        Card(
                            player = target,
                            name = cardName,
                            deckType = runType.deckType(),
                            server = plugin.serverName,
                        )
                    )
                    plugin.logger.info("Added $cardName to $target's ${runType.displayName} deck")
                    source.sendGreenMessage("Added $cardName to $target's ${runType.displayName} deck")
                }

                "remove" -> plugin.async(source) {
                    inventoryApi.inventoryDeleteCardPost(
                        Card(
                            player = target,
                            name = cardName,
                            deckType = runType.deckType(),
                        )
                    )
                    plugin.logger.info("Removed $cardName from $target's ${runType.displayName} deck")
                    source.sendGreenMessage("Removed $cardName from $target's ${runType.displayName} deck")
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            plugin.logger.severe("Failed to $action $cardName to/from $target's ${runType.displayName} deck. Exception: $e")
            source.sendRedMessage("Failed to $action $cardName to $target's ${runType.displayName} deck. Exception: $e")
        }
    }
}
