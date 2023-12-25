package org.trackedout.citadel.data

import org.bukkit.entity.Player
import java.util.*

class Party(var leader: Player, var members: ArrayList<Player> = ArrayList<Player>()) {
    init {
        members.add(leader)
    }

    fun addMember(member: Player) {
        members.add(member)
    }

    fun removeMember(member: Player) {
        members.remove(member)
    }
}
