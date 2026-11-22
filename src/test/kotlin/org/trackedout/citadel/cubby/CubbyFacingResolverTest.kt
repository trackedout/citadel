package org.trackedout.citadel.cubby

import kotlin.test.Test
import kotlin.test.assertEquals

// Coordinates are taken from the burn lobby world
class CubbyFacingResolverTest {
    private val cubby1 = Box(-538, 109, 1987, -530, 115, 1996)
    private val cubby33 = Box(-579, 95, 2008, -570, 101, 2016)
    private val cubby32 = Box(-579, 95, 2018, -571, 101, 2026)

    @Test
    fun `chest in front of a south facing cubby resolves south`() {
        val chests = listOf(FrontChest(-539, 110, 1997, CubbyFacing.SOUTH), FrontChest(-529, 110, 1997, CubbyFacing.SOUTH))

        assertEquals(FacingResult.Resolved(CubbyFacing.SOUTH), CubbyFacingResolver.resolve(cubby1, chests, emptyList()))
    }

    @Test
    fun `chest inside the cubby is ignored`() {
        val chests = listOf(FrontChest(-535, 109, 1988, CubbyFacing.SOUTH))

        assertEquals(FacingResult.NoChest, CubbyFacingResolver.resolve(cubby1, chests, emptyList()))
    }

    @Test
    fun `chest inside a neighbouring cubby is ignored`() {
        val chests = listOf(FrontChest(-578, 96, 2012, CubbyFacing.EAST))

        assertEquals(FacingResult.NoChest, CubbyFacingResolver.resolve(cubby32, chests, listOf(cubby33)))
    }

    @Test
    fun `chest facing back towards the cubby is ignored`() {
        val chests = listOf(FrontChest(-539, 110, 1997, CubbyFacing.NORTH))

        assertEquals(FacingResult.NoChest, CubbyFacingResolver.resolve(cubby1, chests, emptyList()))
    }

    @Test
    fun `chests facing different ways conflict`() {
        val chests = listOf(FrontChest(-539, 110, 1997, CubbyFacing.SOUTH), FrontChest(-540, 110, 1990, CubbyFacing.WEST))

        assertEquals(
            FacingResult.Conflict(setOf(CubbyFacing.SOUTH, CubbyFacing.WEST)),
            CubbyFacingResolver.resolve(cubby1, chests, emptyList()),
        )
    }

    @Test
    fun `quarter turns are clockwise`() {
        assertEquals(1, CubbyFacing.NORTH.quarterTurnsTo(CubbyFacing.EAST))
        assertEquals(2, CubbyFacing.SOUTH.quarterTurnsTo(CubbyFacing.NORTH))
        assertEquals(3, CubbyFacing.NORTH.quarterTurnsTo(CubbyFacing.WEST))
        assertEquals(0, CubbyFacing.EAST.quarterTurnsTo(CubbyFacing.EAST))
    }

    @Test
    fun `facing is read from minecraft yaw`() {
        assertEquals(CubbyFacing.SOUTH, CubbyFacing.fromYaw(0f))
        assertEquals(CubbyFacing.WEST, CubbyFacing.fromYaw(90f))
        assertEquals(CubbyFacing.NORTH, CubbyFacing.fromYaw(180f))
        assertEquals(CubbyFacing.NORTH, CubbyFacing.fromYaw(-180f))
        assertEquals(CubbyFacing.EAST, CubbyFacing.fromYaw(-90f))
        assertEquals(CubbyFacing.EAST, CubbyFacing.fromYaw(-100f))
    }

    @Test
    fun `odd quarter turns swap the horizontal size`() {
        assertEquals(Size(10, 7, 9), Size(9, 7, 10).rotated(1))
        assertEquals(Size(9, 7, 10), Size(9, 7, 10).rotated(2))
    }

    @Test
    fun `differently facing cubbies share a shape`() {
        assertEquals(Size(9, 7, 10).shape(), Size(10, 7, 9).shape())
    }
}
