package org.trackedout.citadel.commands.party

import org.trackedout.client.models.Party

private const val FORMING_STATE = "forming"

enum class PartyAction(val usage: String, val description: String, val needsTarget: Boolean) {
    CREATE("create <league>", "Create a party for a league", needsTarget = true),
    JOIN("join <leader>", "Accept a party invite", needsTarget = true),
    INFO("info", "Show your party", needsTarget = false),
    INVITE("invite <player>", "Invite a player to your party", needsTarget = true),
    KICK("kick <player>", "Kick a player from your party", needsTarget = true),
    LEAVE("leave", "Leave your party", needsTarget = false),
    DISBAND("disband", "Disband your party", needsTarget = false);

    val id: String = name.lowercase()

    companion object {
        fun fromId(id: String): PartyAction? = entries.firstOrNull { it.id == id.lowercase() }
    }
}

// The /party actions that make sense for a player, given the party they are in (if any)
fun partyActionsFor(party: Party?, playerName: String): List<PartyAction> {
    if (party == null) {
        return listOf(PartyAction.CREATE, PartyAction.JOIN)
    }

    if (party.leader != playerName) {
        return listOf(PartyAction.INFO, PartyAction.LEAVE)
    }

    val isForming = party.state == FORMING_STATE
    val hasOtherMembers = party.members.orEmpty().any { it != playerName }
    return buildList {
        add(PartyAction.INFO)
        if (isForming) add(PartyAction.INVITE)
        if (hasOtherMembers) add(PartyAction.KICK)
        add(PartyAction.DISBAND)
    }
}
