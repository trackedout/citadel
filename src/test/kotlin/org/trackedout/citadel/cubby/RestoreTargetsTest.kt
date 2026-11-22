package org.trackedout.citadel.cubby

import kotlin.test.Test
import kotlin.test.assertEquals

class RestoreTargetsTest {
    @Test
    fun `the original cubby is tried first when it is free`() {
        assertEquals(listOf("cubby-31", "cubby-9", "cubby-23"), RestoreTargets.order(listOf("cubby-23", "cubby-9", "cubby-31"), "cubby-31"))
    }

    @Test
    fun `other cubbies are tried by number, not by text`() {
        assertEquals(listOf("cubby-9", "cubby-23", "cubby-100"), RestoreTargets.order(listOf("cubby-100", "cubby-23", "cubby-9"), "cubby-7"))
    }
}
