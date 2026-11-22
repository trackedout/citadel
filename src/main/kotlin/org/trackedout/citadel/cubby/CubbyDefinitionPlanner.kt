package org.trackedout.citadel.cubby

const val DEFAULT_WALL_GAP = 1

data class PlannedCubby(val id: String, val box: Box, val overlaps: List<String>)

/** Lays out new cubby regions in a row next to a reference cubby. */
object CubbyDefinitionPlanner {
    fun plan(
        reference: Box,
        direction: CubbyFacing,
        count: Int,
        gap: Int,
        firstNumber: Int,
        existing: Map<String, Box>,
    ): List<PlannedCubby> {
        val step = reference.extent(direction) + gap

        return (1..count).map { index ->
            val box = reference.shifted(direction.dx * step * index, direction.dz * step * index)
            val overlaps = existing.filterValues { it.intersects(box) }.keys.sorted()
            PlannedCubby("$CUBBY_PREFIX${firstNumber + index - 1}", box, overlaps)
        }
    }
}
