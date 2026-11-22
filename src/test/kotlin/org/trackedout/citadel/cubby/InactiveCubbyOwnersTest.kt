package org.trackedout.citadel.cubby

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InactiveCubbyOwnersTest {
    private val now = Instant.parse("2026-10-20T00:00:00Z")
    private val cutoff = Instant.parse("2025-10-20T00:00:00Z")

    private fun find(
        owners: Map<String, List<String>>,
        lastSeen: Map<String, Instant?>,
        staff: Set<String> = emptySet(),
    ) = InactiveCubbyOwners.find(owners, staff, lastSeen.mapValues { (name, seen) -> KnownPlayer(name, seen) }, cutoff)

    @Test
    fun `owners not seen since the cutoff are inactive, oldest first`() {
        val result = find(
            mapOf("Flare4706" to listOf("cubby-3"), "jonah188" to listOf("cubby-7"), "Active" to listOf("cubby-1")),
            mapOf("flare4706" to Instant.parse("2025-01-04T10:00:00Z"), "jonah188" to Instant.parse("2024-12-28T10:00:00Z"), "active" to Instant.parse("2026-10-01T00:00:00Z")),
        )

        assertEquals(listOf("jonah188", "flare4706"), result.inactive.map { it.name })
    }

    @Test
    fun `owners are shown with the capitalisation of their player document`() {
        val result = InactiveCubbyOwners.find(
            mapOf("jonah188" to listOf("cubby-7")), emptySet(),
            mapOf("jonah188" to KnownPlayer("Jonah188", null)), cutoff,
        )

        assertEquals("Jonah188", result.inactive.single().name)
    }

    @Test
    fun `owners who were never seen are inactive and listed first`() {
        val result = find(
            mapOf("old" to listOf("cubby-2"), "never" to listOf("cubby-1")),
            mapOf("old" to Instant.parse("2025-01-01T00:00:00Z"), "never" to null),
        )

        assertEquals(listOf("never", "old"), result.inactive.map { it.name })
    }

    @Test
    fun `staff are skipped whatever their case`() {
        val result = find(mapOf("SoftShadows" to listOf("cubby-22")), mapOf("softshadows" to null), staff = setOf("softshadows"))

        assertTrue(result.inactive.isEmpty())
        assertEquals(1, result.staffSkipped)
    }

    @Test
    fun `owners without a player document are reported but not inactive`() {
        val result = find(mapOf("Renamed" to listOf("cubby-9")), emptyMap())

        assertTrue(result.inactive.isEmpty())
        assertEquals(mapOf("Renamed" to listOf("cubby-9")), result.unmatched)
    }

    @Test
    fun `table matches the existing report format`() {
        val owners = listOf(
            InactiveOwner("jonah188", Instant.parse("2024-12-28T10:00:00Z"), listOf("cubby-7")),
            InactiveOwner("Koko404mb", Instant.parse("2025-01-13T10:00:00Z"), listOf("cubby-55")),
        )

        assertEquals(
            listOf(
                "jonah188   2024-12-28  1y 9mo ago  cubby-7",
                "Koko404mb  2025-01-13  1y 9mo ago  cubby-55",
            ),
            InactiveCubbyOwners.table(owners, now),
        )
    }
}
