package org.trackedout.citadel.commands;

import org.bukkit.entity.Player;
import org.trackedout.citadel.Citadel;
import org.trackedout.citadel.classes.Party;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Dependency;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;

public class PartyCommand extends BaseCommand {
    @Dependency
    private Citadel plugin;

    @Default
    @Subcommand("info")
    @Description("Get info about your party")
    public void onPartyInfo(Player player) {

        Party party = this.plugin.parties.stream().filter(p -> p.members.contains(player)).findFirst().orElse(null);
        if (party == null) {
            player.sendMessage("You are not in a party.");
        } else if (party.leader == player) {
            player.sendMessage("You are the leader of a party with " + party.members.size() + " members.");
        } else {
            player.sendMessage("You are a member of a party with " + party.members.size() + " members.");
        }
    }

}