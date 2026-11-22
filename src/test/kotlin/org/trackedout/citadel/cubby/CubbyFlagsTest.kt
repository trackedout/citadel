package org.trackedout.citadel.cubby

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CubbyFlagsTest {
    @Test
    fun `commands are unconfirmed by default`() {
        assertFalse(CubbyFlags.of(null).confirmed)
        assertFalse(CubbyFlags.of(arrayOf("--gap", "2")).confirmed)
    }

    @Test
    fun `confirm flag is recognised anywhere`() {
        assertTrue(CubbyFlags.of(arrayOf("--gap", "2", "--confirm")).confirmed)
    }

    @Test
    fun `option values are read`() {
        assertEquals(2, CubbyFlags.of(arrayOf("--gap", "2")).intValue("gap"))
        assertNull(CubbyFlags.of(arrayOf("--gap")).intValue("gap"))
        assertNull(CubbyFlags.of(arrayOf("--gap", "x")).intValue("gap"))
    }
}
