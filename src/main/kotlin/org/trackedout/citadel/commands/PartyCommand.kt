package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel

@CommandAlias("info|party")
class PartyCommand : BaseCommand() {
    @Dependency
    private lateinit var plugin: Citadel

    @Default
    @Subcommand("info")
    @Description("Get info about your party")
    fun onPartyInfo(player: Player) {
        plugin.parties.stream().filter { p -> p.members.contains(player) }.findFirst().ifPresent { party ->
            if (party.leader === player) {
                player.sendMessage("You are the leader of a party with ${party.members.size} members.")
            } else {
                player.sendMessage("You are a member of a party with ${party.members.size} members.")
            }
        }
    }
}