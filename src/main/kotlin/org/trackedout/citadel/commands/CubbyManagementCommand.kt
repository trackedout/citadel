package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Subcommand
import me.devnatan.inventoryframework.ViewFrame
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.getApplicableRegions
import org.trackedout.citadel.getCubby
import org.trackedout.citadel.getCubbyByName
import org.trackedout.citadel.getCubbyForPlayer
import org.trackedout.citadel.regions
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendRedMessage
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.ScoreApi

private const val CUBBY_PREFIX = "cubby-"

@CommandAlias("decked-out|do")
class CubbyManagementCommand(
    private val plugin: Citadel,
    private val eventsApi: EventsApi,
    private val scoreApi: ScoreApi,
    private val viewFrame: ViewFrame,
) : BaseCommand() {

    @Subcommand("cubby claim")
    @Description("Claim a cubby")
    fun claimCubby(player: Player) {
        val playerCubby = player.getCubby()
        if (playerCubby != null) {
            player.sendRedMessage("You already have a cubby. Your cubby is located at ${playerCubby.minimumPoint}, ${playerCubby.maximumPoint}")
        } else {
            val cubby = player.world.getApplicableRegions(player.location)?.firstOrNull { it.id.startsWith(CUBBY_PREFIX) }
            if (cubby != null) {
                if (cubby.members.players.isNotEmpty()) {
                    player.sendRedMessage("Cubby already claimed by ${cubby.members.players.first()}")
                    return
                }
                cubby.parent = player.world.getCubbyByName("cubby")
                cubby.priority = 15
                cubby.members.addPlayer(player.name)
                player.sendGreenMessage("This cubby is now yours!")
            } else {
                player.sendRedMessage("You need to be in a cubby to claim it")
            }
        }
    }

    @Subcommand("cubby locate")
    @Description("Locate your cubby")
    fun locateCubby(source: CommandSender) {
        if (source is Player) {
            val playerCubby = source.getCubby()
            if (playerCubby != null) {
                source.sendGreenMessage("Your cubby is located at ${playerCubby.minimumPoint}, ${playerCubby.maximumPoint}")
            } else {
                source.sendRedMessage("You do not have a cubby")
            }
        } else {
            source.sendRedMessage("Cannot locate your cubby as you are not a player")
        }
    }

    @Subcommand("cubby locate")
    @Description("Locate a cubby owned by another player")
    @CommandCompletion("@dbPlayers")
    fun locateCubbyOtherPlayer(source: CommandSender, targetPlayer: String) {
        if (source is Player) {
            val playerCubby = source.world.getCubbyForPlayer(targetPlayer)
            if (playerCubby != null) {
                source.sendGreenMessage("${targetPlayer}'s cubby is located at ${playerCubby.minimumPoint}, ${playerCubby.maximumPoint}")
            } else {
                source.sendRedMessage("$targetPlayer does not have a cubby")
            }
        } else {
            source.sendRedMessage("Cannot locate your cubby as you are not a player")
        }
    }

    @Subcommand("cubby count")
    @Description("Show counts of owned / available cubbies")
    fun listCubbyCounts(source: CommandSender) {
        val world = if (source is Player) source.world else plugin.server.worlds.find { it.name == "world" }
        val allCubbies = world?.regions()?.filter { it.id.startsWith(CUBBY_PREFIX) }

        if (allCubbies != null) {
            val ownedCubbies = allCubbies.filter { it.members.players.isNotEmpty() }
            val availableCubbies = allCubbies.filter { it.members.players.isEmpty() }

            source.sendGreenMessage("Owned Cubbies: ${ownedCubbies.size}")
            source.sendGreenMessage("Available Cubbies: ${availableCubbies.size}")
        } else {
            source.sendRedMessage("No cubbies found")
        }
    }

    @Subcommand("cubby list")
    @Description("Show all owned cubbies by all players")
    fun listCubbies(source: CommandSender) {
        val world = if (source is Player) source.world else plugin.server.worlds.find { it.name == "world" }
        val allCubbies = world?.regions()?.filter { it.id.startsWith(CUBBY_PREFIX) }

        if (allCubbies != null) {
            val ownedCubbies = allCubbies.filter { it.members.players.isNotEmpty() }
            val availableCubbies = allCubbies.filter { it.members.players.isEmpty() }

            source.sendGreenMessage("Cubbies (${ownedCubbies.size} total):")
            ownedCubbies.forEach { cubby ->
                source.sendGreenMessage("- ${cubby.id} -> ${cubby.minimumPoint}, ${cubby.maximumPoint} (owned by ${cubby.members.players})")
            }

            source.sendGreenMessage("Available Cubbies: ${availableCubbies.size}")
        } else {
            source.sendRedMessage("No cubbies found")
        }
    }
}
