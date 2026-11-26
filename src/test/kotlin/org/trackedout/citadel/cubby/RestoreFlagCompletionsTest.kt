package org.trackedout.citadel.cubby

import kotlin.test.Test
import kotlin.test.assertEquals

class RestoreFlagCompletionsTest {
    private val free = listOf("cubby-3", "cubby-9")
    private val timestampsOf = { player: String -> if (player == "jonah") listOf("20261126T022852Z", "20261126T022757Z") else emptyList() }

    private fun suggest(vararg typed: String) = RestoreFlagCompletions.suggest(typed.toList(), free, timestampsOf)

    @Test
    fun `all flags are offered after the player`() {
        assertEquals(listOf("--cubby", "--archive", "--confirm"), suggest("jonah", ""))
    }

    @Test
    fun `free cubbies are offered after --cubby`() {
        assertEquals(free, suggest("jonah", "--cubby", ""))
    }

    @Test
    fun `the player's archive timestamps are offered after --archive`() {
        assertEquals(listOf("20261126T022852Z", "20261126T022757Z"), suggest("jonah", "--archive", ""))
    }

    @Test
    fun `flags already typed are not offered again`() {
        assertEquals(listOf("--confirm"), suggest("jonah", "--cubby", "cubby-3", "--archive", "20261126T022757Z", ""))
    }
}
