package org.trackedout.citadel.commands.party

import co.aikar.commands.BaseCommand
import co.aikar.commands.CommandHelp
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.HelpCommand
import co.aikar.commands.annotation.Subcommand
import co.aikar.commands.annotation.Syntax
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendMiniMessage
import org.trackedout.citadel.sendRedMessage

@CommandAlias("party")
class PartyCommand(
    private val plugin: Citadel,
    private val partyApi: PartyApi,
    private val gameRegistry: GameRegistryCache,
) : BaseCommand() {

    @HelpCommand
    fun doHelp(help: CommandHelp) {
        help.showHelp()
    }

    @Subcommand("create")
    @Syntax("<game>")
    @CommandCompletion("@gameNames")
    @Description("Create a party for a game mode")
    fun create(player: Player, game: String) {
        val permission = "tracked-out.game.$game"
        if (!player.hasPermission(permission)) {
            player.sendRedMessage("You don't have permission to play $game")
            return
        }

        plugin.async(player) {
            val gameMode = gameRegistry.getGame(game)
            if (gameMode == null) {
                player.sendRedMessage("Unknown game mode: $game")
                return@async
            }
            if (!gameMode.enabled) {
                player.sendRedMessage("Game mode $game is currently disabled")
                return@async
            }

            val party = partyApi.createParty(leader = player.name, game = game)
            player.sendGreenMessage("Party created for ${gameMode.displayName}! Invite players with /party invite <player>")
        }
    }

    @Subcommand("invite")
    @Syntax("<player>")
    @CommandCompletion("@players")
    @Description("Invite a player to your party")
    fun invite(player: Player, targetName: String) {
        plugin.async(player) {
            val party = getOwnParty(player) ?: return@async
            if (!assertLeader(player, party)) return@async

            val target = Bukkit.getPlayer(targetName)
            if (target == null || !target.isOnline) {
                player.sendRedMessage("Player $targetName is not online")
                return@async
            }

            val updatedParty = partyApi.invitePlayer(party.id, targetName)
            val gameMode = gameRegistry.getGame(party.game)
            val displayName = gameMode?.displayName ?: party.game

            player.sendGreenMessage("Invited $targetName to the party")
            target.sendMiniMessage("<green>${player.name} invited you to play <gold>$displayName</gold>! Type <aqua>/party join ${player.name}</aqua></green>")
        }
    }

    @Subcommand("join")
    @Syntax("<leader>")
    @CommandCompletion("@players")
    @Description("Accept an invite and join a party")
    fun join(player: Player, leaderName: String) {
        plugin.async(player) {
            val leaderParty = try {
                partyApi.getParty(leaderName)
            } catch (e: Exception) {
                player.sendRedMessage("Could not find a party led by $leaderName")
                return@async
            }

            val updatedParty = partyApi.joinParty(leaderParty.id, player.name)
            val memberCount = updatedParty.members.size
            val maxPlayers = updatedParty.maxPlayers ?: 0

            updatedParty.members.forEach { memberName ->
                Bukkit.getPlayer(memberName)?.sendGreenMessage("${player.name} joined the party! ($memberCount/$maxPlayers)")
            }
        }
    }

    @Subcommand("leave")
    @Description("Leave your current party")
    fun leave(player: Player) {
        plugin.async(player) {
            val party = getOwnParty(player) ?: return@async

            val updatedParty = partyApi.leaveParty(party.id, player.name)
            player.sendGreenMessage("You left the party")

            updatedParty.members.forEach { memberName ->
                Bukkit.getPlayer(memberName)?.sendMiniMessage("<yellow>${player.name} left the party</yellow>")
            }
        }
    }

    @Subcommand("kick")
    @Syntax("<player>")
    @CommandCompletion("@players")
    @Description("Kick a player from your party")
    fun kick(player: Player, targetName: String) {
        plugin.async(player) {
            val party = getOwnParty(player) ?: return@async
            if (!assertLeader(player, party)) return@async

            val updatedParty = partyApi.kickPlayer(party.id, targetName)

            Bukkit.getPlayer(targetName)?.sendRedMessage("You were kicked from the party")

            updatedParty.members.forEach { memberName ->
                Bukkit.getPlayer(memberName)?.sendMiniMessage("<yellow>$targetName was kicked from the party</yellow>")
            }
        }
    }

    @Subcommand("queue")
    @Description("Start the queue for your party")
    fun queue(player: Player) {
        plugin.async(player) {
            val party = getOwnParty(player) ?: return@async
            if (!assertLeader(player, party)) return@async

            partyApi.queueParty(party.id, player.name)

            party.members.forEach { memberName ->
                Bukkit.getPlayer(memberName)?.sendMiniMessage("<aqua>Searching for a server...</aqua>")
            }
        }
    }

    @Subcommand("disband")
    @Description("Disband your party")
    fun disband(player: Player) {
        plugin.async(player) {
            val party = getOwnParty(player) ?: return@async
            if (!assertLeader(player, party)) return@async

            val members = party.members.toList()
            partyApi.disbandParty(party.id)

            members.forEach { memberName ->
                Bukkit.getPlayer(memberName)?.sendRedMessage("Party disbanded")
            }
        }
    }

    @Subcommand("info")
    @Description("Show your current party info")
    fun info(player: Player) {
        plugin.async(player) {
            val party = getOwnParty(player) ?: return@async
            val gameMode = gameRegistry.getGame(party.game)
            val displayName = gameMode?.displayName ?: party.game

            player.sendMiniMessage("<green>--- Party Info ---</green>")
            player.sendMiniMessage("<gray>Game:</gray> <gold>$displayName</gold>")
            player.sendMiniMessage("<gray>Leader:</gray> <aqua>${party.leader}</aqua>")
            player.sendMiniMessage("<gray>Members:</gray> <white>${party.members.joinToString(", ")}</white>")
            player.sendMiniMessage("<gray>State:</gray> <white>${party.state ?: "waiting"}</white>")
        }
    }

    private fun getOwnParty(player: Player): Party? {
        return try {
            partyApi.getParty(player.name)
        } catch (e: Exception) {
            player.sendRedMessage("You are not in a party")
            null
        }
    }

    private fun assertLeader(player: Player, party: Party): Boolean {
        if (party.leader != player.name) {
            player.sendRedMessage("Only the party leader can do that")
            return false
        }
        return true
    }
}
