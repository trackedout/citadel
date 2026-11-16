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
import org.trackedout.client.apis.PartiesApi
import org.trackedout.client.models.PartiesInvitePostRequest
import org.trackedout.client.models.PartiesPostRequest
import org.trackedout.client.models.Party

// A party plays a league together: the leader queues as usual, and when they pass
// through the dungeon door the rest of the party is sent in with them
@CommandAlias("party")
class PartyCommand(
    private val plugin: Citadel,
    private val partiesApi: PartiesApi,
) : BaseCommand() {

    @HelpCommand
    fun doHelp(help: CommandHelp) {
        help.showHelp()
    }

    @Subcommand("create")
    @Syntax("<league>")
    @CommandCompletion("@partyLeagues @nothing")
    @Description("Create a party for a league")
    fun create(player: Player, slug: String) {
        plugin.async(player) {
            val league = plugin.leagueCache.getLeagueBySlug(slug)
            if (league == null || !plugin.runModes.isSelectable(league, player.name)) {
                player.sendRedMessage("$slug is not available to play")
                return@async
            }
            if (!league.supportsParties()) {
                player.sendRedMessage("${league.displayName} does not support parties")
                return@async
            }

            partiesApi.partiesPost(PartiesPostRequest(leader = player.name, slug = slug))
            player.sendGreenMessage("Party created for ${league.displayName}! Invite players with /party invite <player>")
            player.sendMiniMessage("<gray>Queue as usual. When you pass through the dungeon door, your party is sent in with you</gray>")
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

            partiesApi.partiesInvitePost(party.id!!, PartiesInvitePostRequest(player = targetName))

            player.sendGreenMessage("Invited $targetName to the party")
            target.sendMiniMessage("<green>${player.name} invited you to play <gold>${leagueName(party)}</gold>! Type <aqua>/party join ${player.name}</aqua></green>")
        }
    }

    @Subcommand("join")
    @Syntax("<leader>")
    @CommandCompletion("@players")
    @Description("Accept an invite and join a party")
    fun join(player: Player, leaderName: String) {
        plugin.async(player) {
            val leaderParty = try {
                partiesApi.partiesGet(player = leaderName)
            } catch (e: Exception) {
                player.sendRedMessage("Could not find a party led by $leaderName")
                return@async
            }

            val updatedParty = partiesApi.partiesJoinPost(leaderParty.id!!, PartiesInvitePostRequest(player = player.name))
            val size = partySize(updatedParty)

            updatedParty.members?.forEach { memberName ->
                Bukkit.getPlayer(memberName)?.sendGreenMessage("${player.name} joined the party! ($size)")
            }
        }
    }

    @Subcommand("leave")
    @Description("Leave your current party")
    fun leave(player: Player) {
        plugin.async(player) {
            val party = getOwnParty(player) ?: return@async

            val updatedParty = partiesApi.partiesLeavePost(party.id!!, PartiesInvitePostRequest(player = player.name))
            player.sendGreenMessage("You left the party")

            updatedParty.members?.forEach { memberName ->
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

            val updatedParty = partiesApi.partiesKickPost(party.id!!, PartiesInvitePostRequest(player = targetName))

            Bukkit.getPlayer(targetName)?.sendRedMessage("You were kicked from the party")

            updatedParty.members?.forEach { memberName ->
                Bukkit.getPlayer(memberName)?.sendMiniMessage("<yellow>$targetName was kicked from the party</yellow>")
            }
        }
    }

    @Subcommand("disband")
    @Description("Disband your party")
    fun disband(player: Player) {
        plugin.async(player) {
            val party = getOwnParty(player) ?: return@async
            if (!assertLeader(player, party)) return@async

            val members = party.members?.toList() ?: emptyList()
            partiesApi.partiesDelete(party.id!!)

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

            player.sendMiniMessage("<green>--- Party Info ---</green>")
            player.sendMiniMessage("<gray>League:</gray> <gold>${leagueName(party)}</gold>")
            player.sendMiniMessage("<gray>Leader:</gray> <aqua>${party.leader}</aqua>")
            player.sendMiniMessage("<gray>Members (${partySize(party)}):</gray> <white>${party.members?.joinToString(", ")}</white>")
            player.sendMiniMessage("<gray>State:</gray> <white>${party.state ?: "unknown"}</white>")
        }
    }

    private fun leagueName(party: Party): String =
        party.slug?.let { plugin.leagueCache.getLeagueBySlug(it)?.displayName } ?: party.slug ?: "unknown"

    private fun partySize(party: Party): String {
        val maxPlayers = party.slug?.let { plugin.leagueCache.getLeagueBySlug(it)?.maxPartySize }
        return "${party.members?.size ?: 0}/${maxPlayers ?: "?"}"
    }

    private fun getOwnParty(player: Player): Party? {
        return try {
            partiesApi.partiesGet(player = player.name)
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
