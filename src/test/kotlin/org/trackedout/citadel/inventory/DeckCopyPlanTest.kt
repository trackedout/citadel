package org.trackedout.citadel.inventory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeckCopyPlanTest {
    private val valid = setOf("a", "b", "c")

    @Test
    fun `adds what the target lacks`() {
        val plan = DeckCopyPlan.of(listOf("a", "a", "b"), listOf("a"), valid)

        assertEquals(mapOf("a" to 1, "b" to 1), plan.toAdd)
    }

    @Test
    fun `removes what the source lacks`() {
        val plan = DeckCopyPlan.of(listOf("a"), listOf("a", "a", "c"), valid)

        assertEquals(mapOf("a" to 1, "c" to 1), plan.toRemove)
    }

    @Test
    fun `skips entries missing from the target catalog`() {
        val plan = DeckCopyPlan.of(listOf("a", "x", "x"), emptyList(), valid)

        assertEquals(mapOf("x" to 2), plan.notInTargetCatalog)
    }

    @Test
    fun `identical decks need no change`() {
        assertTrue(DeckCopyPlan.of(listOf("a", "b"), listOf("b", "a"), valid).isNoop)
    }
}
