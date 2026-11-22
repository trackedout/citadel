package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Optional
import co.aikar.commands.annotation.Subcommand
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.sendMiniMessage
import org.trackedout.citadel.sendRedMessage
import org.trackedout.client.apis.ConfigApi
import org.trackedout.client.infrastructure.ClientException
import org.trackedout.client.models.Config

val toggleableConfigs = listOf(
    "skip-door",
    "allow-spectating"
)

// Requires 'decked-out.config.edit' permission
val editableConfigs = listOf(
    "dungeon-type",
)

private val configDescriptions = mapOf(
    "skip-door" to "Enable this to skip the door animation",
    "dungeon-type" to "The world a warm dungeon serves (a world slug, see /do world list)",
    "allow-spectating" to "Allow players to spectate your runs",
    "shulker-style" to "Shulker box colour (in competitive mode)",
)

private fun configDescription(key: String?): String = configDescriptions[key]?.let { "<gray>$it</gray>" } ?: ""

@CommandAlias("decked-out|do")
class ConfigCommand(
    private val plugin: Citadel,
    private val configApi: ConfigApi,
) : BaseCommand() {

    @Subcommand("config show")
    @CommandPermission("decked-out.config.view")
    @Description("Show your current config")
    fun showConfig(source: CommandSender) {
        if (source is Player) {
            source.sendConfigList(source.name)
        } else {
            source.sendRedMessage("Cannot show your configs as you are not a player")
        }
    }

    @Subcommand("config show")
    @CommandPermission("decked-out.config.view.all")
    @Description("List config values for target entity")
    @CommandCompletion("@dbPlayers @nothing")
    fun showConfigForPlayer(source: CommandSender, target: String) {
        source.sendConfigList(target)
    }

    private fun CommandSender.sendConfigList(entity: String) {
        val results = configApi.configsListGet(entity).results

        this.sendMiniMessage("<green>Configs for <gold>${entity}</gold>:")
        results?.forEach { result ->
            this.sendMiniMessage(getConfigAsPrintableString(result.key, result.value))
        }

        toggleableConfigs.filter { configKey -> results?.any { result -> result.key == configKey } == false }.forEach { toggleableConfig ->
            this.sendMiniMessage(getConfigAsPrintableString(toggleableConfig, "false"))
        }
    }

    /* Command to set a config. Looks like this in the database:
      {
        entity: 'd800', // Could be player name or dungeon ID
        key: 'dungeon-type',
        value: 'do2-s3'
      }
     */

    @Subcommand("config set")
    @CommandPermission("decked-out.config.edit")
    @Description("Set config for target entity")
    @CommandCompletion("@dbPlayers @editableConfigs @nothing @nothing")
    fun setConfigForEntity(source: CommandSender, targetName: String, configKey: String, configValue: String, @Optional arg: String?) {
        if (configKey !in editableConfigs && arg != "--force") {
            source.sendRedMessage("Config key $configKey is not editable")
            return
        }

        configApi.configsAddConfigPost(
            Config(
                entity = targetName,
                key = configKey,
                value = configValue,
                metadata = mapOf("set-by" to source.name)
            )
        )

        source.sendMiniMessage("Config <gold>$configKey</gold> for <gold>$targetName</gold> is now set to $configValue")
    }

    @Subcommand("config toggle")
    @CommandPermission("decked-out.config.toggle")
    @Description("Toggle a config off / on")
    @CommandCompletion("@toggleableConfigs @nothing")
    fun setConfigForPlayer(source: CommandSender, configKey: String) {
        if (source !is Player) {
            source.sendRedMessage("Cannot modify your config as you are not a player. Use /do toggle <configKey> <playerName>")
            return
        }

        val playerName = source.name
        if (configKey !in toggleableConfigs) {
            source.sendRedMessage("Config key $configKey is not toggleable")
            return
        }

        // Find existing value, if present
        val isEnabled = try {
            configApi.configsGet(playerName, configKey).let { it.value == "true" }
        } catch (e: ClientException) {
            e.printStackTrace()
            false
        }
        val newValue = (!isEnabled).toString()

        configApi.configsAddConfigPost(
            Config(
                entity = playerName,
                key = configKey,
                value = newValue,
            )
        )

        source.sendMiniMessage("<aqua>Config <gold>$configKey</gold> is now set to ${colouredValue(newValue)}</aqua>")
    }
}

private fun getConfigAsPrintableString(key: String?, value: String?): String = "- <gold>$key</gold> = ${colouredValue(value)} ".padEnd(50) + configDescription(key).trim()

private fun colouredValue(value: String?): String {
    val value = when (value?.lowercase()) {
        "true" -> "<green>$value</green>"
        "false" -> "<red>$value</red>"
        else -> "<aqua>$value</aqua>"
    }
    return value
}
