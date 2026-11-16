package org.trackedout.citadel.commands.party

import co.aikar.commands.BaseCommand
import co.aikar.commands.PaperCommandManager
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Optional
import co.aikar.commands.annotation.Syntax
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.InventoryManager
import org.trackedout.citadel.async
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendGreyMessage
import org.trackedout.citadel.sendMiniMessage
import org.trackedout.citadel.sendRedMessage
import org.trackedout.client.apis.PartiesApi
import org.trackedout.client.infrastructure.ClientException
import org.trackedout.client.models.League
import org.trackedout.client.models.PartiesInvitePostRequest
import org.trackedout.client.models.PartiesPostRequest
import org.trackedout.client.models.Party

// Index into the party() parameters, where 0 is the player
private const val ACTION_ARGUMENT_INDEX = 1
private const val NOT_FOUND = 404

// A party plays a league together: the leader queues as usual, and when they pass
// through the dungeon door the rest of the party is sent in with them. Creating or
// joining a party switches the player's run mode (inventory) to the party's league.
//
// One command with an action argument rather than ACF subcommands, so completion
// and help only offer the actions that apply to the player's party (see partyActionsFor)
@CommandAlias("party")
class PartyCommand(
    private val plugin: Citadel,
    private val partiesApi: PartiesApi,
    private val inventoryManager: InventoryManager,
) : BaseCommand() {

    fun registerCompletions(manager: PaperCommandManager) {
        manager.commandCompletions.registerAsyncCompletion("partyActions") { context ->
            val player = context.player ?: return@registerAsyncCompletion emptyList()
            partyActionsFor(findParty(player.name), player.name).map { it.id }
        }
        manager.commandCompletions.registerAsyncCompletion("partyActionTarget") { context ->
            val player = context.player ?: return@registerAsyncCompletion emptyList()
            val actionId = context.getContextValue(String::class.java, ACTION_ARGUMENT_INDEX)
            PartyAction.fromId(actionId)?.let { targetsFor(it, player) } ?: emptyList()
        }
    }

    @Default
    @Syntax("<action> [league|player]")
    @CommandCompletion("@partyActions @partyActionTarget @nothing")
    @Description("Play a league run together with other players")
    fun party(player: Player, @Optional actionId: String?, @Optional target: String?) {
        plugin.async(player) {
            val party = findParty(player.name)
            val available = partyActionsFor(party, player.name)
            val action = actionId?.let { PartyAction.fromId(it) }

            if (action == null || action !in available) {
                if (actionId != null) player.sendRedMessage("You can't use /party $actionId right now")
                showHelp(player, available)
                return@async
            }
            if (action.needsTarget && target == null) {
                player.sendRedMessage("Usage: /party ${action.usage}")
                return@async
            }

            when (action) {
                PartyAction.CREATE -> create(player, target!!)
                PartyAction.JOIN -> join(player, target!!)
                PartyAction.INFO -> info(player, party!!)
                PartyAction.INVITE -> invite(player, party!!, target!!)
                PartyAction.KICK -> kick(party!!, target!!)
                PartyAction.LEAVE -> leave(player, party!!)
                PartyAction.DISBAND -> disband(party!!)
            }
        }
    }

    private fun showHelp(player: Player, available: List<PartyAction>) {
        player.sendGreenMessage("--- Party commands ---")
        available.forEach { player.sendGreyMessage("/party ${it.usage} - ${it.description}") }
    }

    private fun targetsFor(action: PartyAction, player: Player): List<String> {
        val otherOnlinePlayers = Bukkit.getOnlinePlayers().map { it.name }.filter { it != player.name }
        return when (action) {
            PartyAction.CREATE -> plugin.runModes.selectableLeagues(player.name).filter { it.supportsParties() }.mapNotNull { it.slug }
            PartyAction.JOIN -> otherOnlinePlayers
            PartyAction.INVITE -> {
                val members = findParty(player.name)?.members.orEmpty()
                otherOnlinePlayers.filter { it !in members }
            }
            PartyAction.KICK -> findParty(player.name)?.members.orEmpty().filter { it != player.name }
            PartyAction.INFO, PartyAction.LEAVE, PartyAction.DISBAND -> emptyList()
        }
    }

    private fun create(player: Player, slug: String) {
        val league = plugin.leagueCache.getLeagueBySlug(slug)
        if (league == null || !plugin.runModes.isSelectable(league, player.name)) {
            player.sendRedMessage("$slug is not available to play")
            return
        }
        if (!league.supportsParties()) {
            player.sendRedMessage("${league.displayName} does not support parties")
            return
        }

        partiesApi.partiesPost(PartiesPostRequest(leader = player.name, slug = slug))
        player.sendGreenMessage("Party created for ${league.displayName}! Invite players with /party invite <player>")
        player.sendMiniMessage("<gray>Queue as usual. When you pass through the dungeon door, your party is sent in with you</gray>")
        switchRunModeTo(player, league)
    }

    private fun invite(player: Player, party: Party, targetName: String) {
        val target = Bukkit.getPlayer(targetName)
        if (target == null || !target.isOnline) {
            player.sendRedMessage("Player $targetName is not online")
            return
        }

        partiesApi.partiesInvitePost(party.id!!, PartiesInvitePostRequest(player = targetName))

        player.sendGreenMessage("Invited $targetName to the party")
        target.sendMiniMessage("<green>${player.name} invited you to play <gold>${leagueName(party)}</gold>! Type <aqua>/party join ${player.name}</aqua></green>")
    }

    private fun join(player: Player, leaderName: String) {
        val leaderParty = findParty(leaderName)
        if (leaderParty == null) {
            player.sendRedMessage("Could not find a party led by $leaderName")
            return
        }

        val updatedParty = partiesApi.partiesJoinPost(leaderParty.id!!, PartiesInvitePostRequest(player = player.name))
        val size = partySize(updatedParty)

        updatedParty.members?.forEach { memberName ->
            Bukkit.getPlayer(memberName)?.sendGreenMessage("${player.name} joined the party! ($size)")
        }
        updatedParty.slug?.let { plugin.leagueCache.getLeagueBySlug(it) }?.let { switchRunModeTo(player, it) }
    }

    private fun leave(player: Player, party: Party) {
        val updatedParty = partiesApi.partiesLeavePost(party.id!!, PartiesInvitePostRequest(player = player.name))
        player.sendGreenMessage("You left the party")

        updatedParty.members?.forEach { memberName ->
            Bukkit.getPlayer(memberName)?.sendMiniMessage("<yellow>${player.name} left the party</yellow>")
        }
    }

    private fun kick(party: Party, targetName: String) {
        val updatedParty = partiesApi.partiesKickPost(party.id!!, PartiesInvitePostRequest(player = targetName))

        Bukkit.getPlayer(targetName)?.sendRedMessage("You were kicked from the party")

        updatedParty.members?.forEach { memberName ->
            Bukkit.getPlayer(memberName)?.sendMiniMessage("<yellow>$targetName was kicked from the party</yellow>")
        }
    }

    private fun disband(party: Party) {
        partiesApi.partiesDelete(party.id!!)

        party.members.orEmpty().forEach { memberName ->
            Bukkit.getPlayer(memberName)?.sendRedMessage("Party disbanded")
        }
    }

    private fun info(player: Player, party: Party) {
        player.sendMiniMessage("<green>--- Party Info ---</green>")
        player.sendMiniMessage("<gray>League:</gray> <gold>${leagueName(party)}</gold>")
        player.sendMiniMessage("<gray>Leader:</gray> <aqua>${party.leader}</aqua>")
        player.sendMiniMessage("<gray>Members (${partySize(party)}):</gray> <white>${party.members?.joinToString(", ")}</white>")
        player.sendMiniMessage("<gray>State:</gray> <white>${party.state ?: "unknown"}</white>")
    }

    private fun switchRunModeTo(player: Player, league: League) {
        val slug = league.slug ?: return
        if (plugin.runModes.current(player.name)?.longId == slug) {
            return
        }
        if (!plugin.runModes.isSelectable(league, player.name)) {
            player.sendRedMessage("${league.displayName} is not available to you as a run mode, your inventory was not switched")
            return
        }

        plugin.runModes.choose(player.name, slug)
        inventoryManager.updateInventoryBasedOnScore(player)
        player.sendGreenMessage("Your run mode is now ${league.displayName}")
    }

    private fun leagueName(party: Party): String =
        party.slug?.let { plugin.leagueCache.getLeagueBySlug(it)?.displayName } ?: party.slug ?: "unknown"

    private fun partySize(party: Party): String {
        val maxPlayers = party.slug?.let { plugin.leagueCache.getLeagueBySlug(it)?.maxPartySize }
        return "${party.members?.size ?: 0}/${maxPlayers ?: "?"}"
    }

    // The backend answers 404 when the player is not in a party
    private fun findParty(playerName: String): Party? =
        try {
            partiesApi.partiesGet(player = playerName)
        } catch (e: ClientException) {
            if (e.statusCode == NOT_FOUND) null else throw e
        }
}
