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

fun ProtectedRegion.hasMembers(): Boolean {
    return this.members.players.isNotEmpty() || this.members.uniqueIds.isNotEmpty()
}

fun Player.getCubby(): ProtectedRegion? {
    return this.world.getCubbyForPlayer(this.name, this.uniqueId)
}

fun World.getCubbyByName(name: String): ProtectedRegion? {
    return this.regions()?.firstOrNull { region ->
        region.id.equals(name, ignoreCase = true)
    }
}

fun World.getCubbyForPlayer(playerName: String, uuid: java.util.UUID? = null): ProtectedRegion? {
    return this.regions()?.firstOrNull { region ->
        region.members.players.contains(playerName.lowercase()) ||
            (uuid != null && region.members.uniqueIds.contains(uuid))
    }
}

fun ProtectedRegion.getCenterLocation(world: World): Location {
    val min = this.minimumPoint.toVector3()
    val max = this.maximumPoint.toVector3()

    val center = min.add(max.add(1.0, 1.0, 1.0)).multiply(0.5)

    return Location(world, center.x, center.y, center.z)
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
