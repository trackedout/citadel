package org.trackedout.citadel

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.ApplicableRegionSet
import com.sk89q.worldguard.protection.managers.RegionManager
import com.sk89q.worldguard.protection.regions.ProtectedRegion
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player

fun Player.getCubby(): ProtectedRegion? {
    return this.world.getCubbyForPlayer(this.name)
}

fun World.getCubbyByName(name: String): ProtectedRegion? {
    return this.regions()?.firstOrNull { region ->
        region.id.equals(name, ignoreCase = true)
    }
}

fun World.getCubbyForPlayer(playerName: String): ProtectedRegion? {
    return this.regions()?.firstOrNull { region ->
        region.members.players.contains(playerName.lowercase())
    }
}

fun World.getApplicableRegions(location: Location): ApplicableRegionSet? {
    val playerLocation = BlockVector3.at(location.x, location.y, location.z)
    return this.regionManager()?.getApplicableRegions(playerLocation)
}

fun World.regions(): MutableCollection<ProtectedRegion>? {
    return regionManager()?.regions?.values
}

fun World.regionManager(): RegionManager? {
    val container = WorldGuard.getInstance().platform.regionContainer
    return container.get(BukkitAdapter.adapt(this))
}
