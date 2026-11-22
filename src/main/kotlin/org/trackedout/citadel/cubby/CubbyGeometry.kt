package org.trackedout.citadel.cubby

/** Compass direction, ordered clockwise when viewed from above. */
enum class CubbyFacing(val dx: Int, val dz: Int) {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    /** Number of clockwise quarter turns that turn this facing into [target]. */
    fun quarterTurnsTo(target: CubbyFacing): Int = (target.ordinal - ordinal + CubbyFacing.entries.size) % CubbyFacing.entries.size
}

data class Size(val x: Int, val y: Int, val z: Int) {
    /** The size after turning clockwise by [quarterTurns]: odd turns swap the horizontal axes. */
    fun rotated(quarterTurns: Int): Size = if (quarterTurns % 2 == 0) this else Size(z, y, x)

    /** Orientation-independent shape, so differently facing cubbies of one design compare equal. */
    fun shape(): Shape = Shape(y, minOf(x, z), maxOf(x, z))

    override fun toString() = "${x}x${y}x$z"
}

/** Height, then the shorter and longer horizontal side. */
data class Shape(val height: Int, val shortSide: Int, val longSide: Int) {
    override fun toString() = "${shortSide}x${height}x$longSide"
}

/** Inclusive block bounds. */
data class Box(
    val minX: Int, val minY: Int, val minZ: Int,
    val maxX: Int, val maxY: Int, val maxZ: Int,
) {
    val size = Size(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1)

    fun contains(x: Int, y: Int, z: Int) = x in minX..maxX && y in minY..maxY && z in minZ..maxZ

    /** Whether the block lies beyond this box's edge in the direction of [facing]. */
    fun isBeyondEdge(x: Int, z: Int, facing: CubbyFacing) = when (facing) {
        CubbyFacing.NORTH -> z < minZ
        CubbyFacing.SOUTH -> z > maxZ
        CubbyFacing.WEST -> x < minX
        CubbyFacing.EAST -> x > maxX
    }
}

data class FrontChest(val x: Int, val y: Int, val z: Int, val facing: CubbyFacing)

sealed interface FacingResult {
    data class Resolved(val facing: CubbyFacing) : FacingResult
    data object NoChest : FacingResult
    data class Conflict(val facings: Set<CubbyFacing>) : FacingResult
}

/**
 * A cubby's front is marked by an ender chest just outside it, facing away from the cubby.
 * Chests inside the cubby or inside a neighbouring cubby belong to players and are ignored.
 */
object CubbyFacingResolver {
    fun resolve(cubby: Box, nearbyChests: List<FrontChest>, otherCubbies: List<Box>): FacingResult {
        val facings = nearbyChests
            .filter { !cubby.contains(it.x, it.y, it.z) }
            .filter { cubby.isBeyondEdge(it.x, it.z, it.facing) }
            .filter { chest -> otherCubbies.none { it.contains(chest.x, chest.y, chest.z) } }
            .map { it.facing }
            .toSet()

        return when (facings.size) {
            0 -> FacingResult.NoChest
            1 -> FacingResult.Resolved(facings.single())
            else -> FacingResult.Conflict(facings)
        }
    }
}
