package org.trackedout.citadel.classes;

import java.util.ArrayList;

import org.bukkit.entity.Player;

public class Party {
	public Player leader = null;
	public ArrayList<Player> members = new ArrayList<Player>();

	public Party(Player leader) {
		this.leader = leader;
		this.members.add(leader);
	}

	public void addMember(Player member) {
		this.members.add(member);
	}

	public void removeMember(Player member) {
		this.members.remove(member);
	}

}
