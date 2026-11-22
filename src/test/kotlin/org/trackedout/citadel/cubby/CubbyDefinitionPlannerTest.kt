package org.trackedout.citadel.cubby

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CubbyDefinitionPlannerTest {
    // cubby-1 and cubby-2 on burn: 9 wide, one wall block between them
    private val cubby1 = Box(-538, 109, 1987, -530, 115, 1996)
    private val cubby2 = Box(-548, 109, 1987, -540, 115, 1996)

    @Test
    fun `new cubbies step by the width plus a wall`() {
        val planned = CubbyDefinitionPlanner.plan(cubby1, CubbyFacing.WEST, 2, DEFAULT_WALL_GAP, 3, emptyMap())

        assertEquals(cubby2, planned.first().box)
        assertEquals(Box(-558, 109, 1987, -550, 115, 1996), planned.last().box)
    }

    @Test
    fun `ids continue from the first free number`() {
        val planned = CubbyDefinitionPlanner.plan(cubby1, CubbyFacing.WEST, 3, DEFAULT_WALL_GAP, 68, emptyMap())

        assertEquals(listOf("cubby-68", "cubby-69", "cubby-70"), planned.map { it.id })
    }

    @Test
    fun `stepping along z uses the depth`() {
        val planned = CubbyDefinitionPlanner.plan(cubby1, CubbyFacing.SOUTH, 1, DEFAULT_WALL_GAP, 1, emptyMap())

        assertEquals(Box(-538, 109, 1998, -530, 115, 2007), planned.single().box)
    }

    @Test
    fun `overlapping existing cubbies are reported`() {
        val planned = CubbyDefinitionPlanner.plan(cubby1, CubbyFacing.WEST, 2, DEFAULT_WALL_GAP, 3, mapOf("cubby-2" to cubby2))

        assertEquals(listOf("cubby-2"), planned.first().overlaps)
        assertTrue(planned.last().overlaps.isEmpty())
    }
}
