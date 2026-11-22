package org.trackedout.citadel.cubby

import com.sk89q.worldguard.protection.regions.ProtectedRegion
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Directional
import org.trackedout.citadel.regions

const val CUBBY_PREFIX = "cubby-"

private const val FIRST_BLOCK_ABOVE_FLOOR = 1
private const val BLOCKS_ABOVE_FLOOR_SEARCHED = 2

fun ProtectedRegion.toBox() = Box(
    minimumPoint.x, minimumPoint.y, minimumPoint.z,
    maximumPoint.x, maximumPoint.y, maximumPoint.z,
)

fun cubbyNumber(regionId: String): Int? = regionId.removePrefix(CUBBY_PREFIX).toIntOrNull()

/** The cubby regions of one world, with the facing of each derived from the chest outside it. */
class CubbyWorld(val world: World) {
    val cubbies: List<ProtectedRegion> =
        world.regions().orEmpty().filter { it.id.startsWith(CUBBY_PREFIX) }.sortedBy { cubbyNumber(it.id) ?: Int.MAX_VALUE }

    fun find(regionId: String): ProtectedRegion? = cubbies.firstOrNull { it.id.equals(regionId, ignoreCase = true) }

    fun nextNumber(): Int = (cubbies.mapNotNull { cubbyNumber(it.id) }.maxOrNull() ?: 0) + 1

    fun facingOf(cubby: ProtectedRegion): FacingResult {
        val box = cubby.toBox()
        val others = cubbies.filter { it.id != cubby.id }.map { it.toBox() }
        return CubbyFacingResolver.resolve(box, chestsAround(box), others)
    }

    fun facingOf(box: Box): FacingResult {
        val others = cubbies.map { it.toBox() }.filter { it != box }
        return CubbyFacingResolver.resolve(box, chestsAround(box), others)
    }

    /** The shape shared by most cubbies, which a template or new region should match. */
    fun commonShape(): Pair<Shape, Int>? {
        val counts = cubbies.groupingBy { it.toBox().size.shape() }.eachCount()
        return counts.maxByOrNull { it.value }?.toPair()
    }

    private fun chestsAround(box: Box): List<FrontChest> {
        val chests = mutableListOf<FrontChest>()
        for (x in box.minX - 1..box.maxX + 1) {
            for (z in box.minZ - 1..box.maxZ + 1) {
                for (y in box.minY + FIRST_BLOCK_ABOVE_FLOOR..box.minY + BLOCKS_ABOVE_FLOOR_SEARCHED) {
                    if (box.contains(x, y, z)) continue
                    enderChestFacing(x, y, z)?.let { chests += FrontChest(x, y, z, it) }
                }
            }
        }
        return chests
    }

    private fun enderChestFacing(x: Int, y: Int, z: Int): CubbyFacing? {
        val block = world.getBlockAt(x, y, z)
        if (block.type != Material.ENDER_CHEST) return null
        return (block.blockData as Directional).facing.toCubbyFacing()
    }

    private fun BlockFace.toCubbyFacing(): CubbyFacing? = CubbyFacing.entries.firstOrNull { it.name == name }
}

fun ProtectedRegion.ownerNames(): List<String> =
    members.players.toList() + members.uniqueIds.map { Bukkit.getOfflinePlayer(it).name ?: it.toString() }
