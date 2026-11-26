package org.trackedout.citadel.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.trackedout.client.models.League
import org.trackedout.client.models.LeagueDeck

class DeckCopyRulesTest {
    private val practice = League(slug = "practice", deck = LeagueDeck(copyFrom = listOf("comp")))

    @Test
    fun `accepts a listed source`() {
        assertTrue(practice.acceptsDeckCopyFrom("comp"))
    }

    @Test
    fun `refuses an unlisted source`() {
        assertFalse(practice.acceptsDeckCopyFrom("hardcore"))
    }

    @Test
    fun `refuses everything when the spec lists no sources`() {
        assertFalse(League(slug = "comp", deck = LeagueDeck()).acceptsDeckCopyFrom("practice"))
    }

    private val competitive = League(slug = "comp", deck = LeagueDeck())
    private val leagues = listOf(practice, competitive)

    @Test
    fun `only decks that some league accepts are offered as sources`() {
        assertEquals(listOf("comp"), leagues.copySourceSlugs())
    }

    @Test
    fun `sources that are not known leagues are not offered`() {
        assertEquals(emptyList<String>(), listOf(practice).copySourceSlugs())
    }

    @Test
    fun `only leagues accepting the source are offered as targets`() {
        assertEquals(listOf("practice"), leagues.copyTargetSlugs("comp"))
    }

    @Test
    fun `no targets are offered for a deck nothing accepts`() {
        assertEquals(emptyList<String>(), leagues.copyTargetSlugs("practice"))
    }
}
