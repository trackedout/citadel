package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Subcommand
import org.bukkit.entity.Player
import org.trackedout.citadel.InventoryManager
import org.trackedout.citadel.sendMiniMessage
import org.trackedout.citadel.sendRedMessage
import org.trackedout.client.apis.ConfigApi
import org.trackedout.client.models.Config

val shulkerColors = listOf(
    "WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY",
    "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"
)

private val shulkerColorTags = mapOf(
    "WHITE" to "white",
    "ORANGE" to "gold",
    "MAGENTA" to "light_purple",
    "LIGHT_BLUE" to "aqua",
    "YELLOW" to "yellow",
    "LIME" to "green",
    "PINK" to "#ff85c2",
    "GRAY" to "dark_gray",
    "LIGHT_GRAY" to "gray",
    "CYAN" to "dark_aqua",
    "PURPLE" to "dark_purple",
    "BLUE" to "blue",
    "BROWN" to "#835432",
    "GREEN" to "dark_green",
    "RED" to "red",
    "BLACK" to "black",
)

@CommandAlias("decked-out|do")
class ShulkerStyleCommand(
    private val configApi: ConfigApi,
    private val inventoryManager: InventoryManager,
) : BaseCommand() {

    @Subcommand("shulker-style")
    @CommandPermission("decked-out.config.shulker-style")
    @Description("Set the colour of your competitive shulker box")
    @CommandCompletion("@shulkerColors")
    fun setShulkerStyle(player: Player, color: String) {
        val normalized = color.uppercase()
        if (normalized !in shulkerColors) {
            player.sendRedMessage("Invalid colour '$color'. Valid colours: ${shulkerColors.joinToString(", ")}")
            return
        }

        configApi.configsAddConfigPost(
            Config(
                entity = player.name,
                key = "shulker-style",
                value = normalized,
            )
        )

        val colorTag = shulkerColorTags[normalized] ?: "white"
        val displayName = normalized.replace('_', ' ')
        player.sendMiniMessage("<aqua>Your competitive shulker is now <$colorTag>$displayName</$colorTag>!</aqua>")
        inventoryManager.updateInventoryBasedOnScore(player)
    }
}
