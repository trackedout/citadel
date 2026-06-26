package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Optional
import co.aikar.commands.annotation.Subcommand
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.sendMiniMessage
import org.trackedout.citadel.sendRedMessage
import org.trackedout.client.apis.ConfigApi
import org.trackedout.client.apis.ScoreApi
import org.trackedout.client.infrastructure.ClientException
import org.trackedout.client.models.Config

@CommandAlias("decked-out|do")
class BookCommand(
    private val plugin: Citadel,
    private val configApi: ConfigApi,
    private val scoreApi: ScoreApi,
) : BaseCommand() {

    @Subcommand("book player")
    @Description("Open the player settings book")
    fun playerBook(player: Player, @Optional page: Int?) {
        showPlayerBook(plugin, player, configApi, scoreApi, page ?: 0)
    }

    @Subcommand("book admin")
    @CommandPermission("decked-out.book.admin")
    @Description("Open the server management book")
    fun adminBook(player: Player) {
        showAdminBook(plugin, player, configApi)
    }

    @Subcommand("book cubby")
    @Description("Open the cubby guide book")
    fun cubbyBook(player: Player) {
        showCubbyBook(player)
    }

    @Subcommand("setting toggle")
    @CommandCompletion("@toggleableConfigs @nothing")
    @Description("Toggle a setting and re-open the settings book")
    fun settingToggle(player: Player, configKey: String) {
        if (configKey !in toggleableConfigs) {
            player.sendRedMessage("Unknown setting: $configKey")
            return
        }

        plugin.async(player) {
            val isEnabled = try {
                configApi.configsGet(player.name, configKey).value == "true"
            } catch (e: ClientException) { false }

            val newValue = (!isEnabled).toString()
            configApi.configsAddConfigPost(
                Config(entity = player.name, key = configKey, value = newValue)
            )

            val colour = if (!isEnabled) "<green>" else "<red>"
            val label = if (!isEnabled) "ON" else "OFF"
            player.sendMiniMessage("<aqua>${configKey}: ${colour}${label}</aqua>")

            // Re-open book at settings page (page index 2)
            showPlayerBook(plugin, player, configApi, scoreApi, page = 2)
        }
    }
}
