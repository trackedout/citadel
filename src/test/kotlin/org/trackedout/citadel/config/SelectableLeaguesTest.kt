package org.trackedout.citadel.config

import org.trackedout.client.models.League
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectableLeaguesTest {
    private fun leagueIn(state: String?) = League(slug = "do2-s2-comp", state = state)

    @Test
    fun `active league is selectable without a deck`() = assertTrue(leagueIn("active").isSelectableRunMode(playerHasDeck = false, isWorldBuilder = false))

    @Test
    fun `completed league is selectable with a deck`() = assertTrue(leagueIn("completed").isSelectableRunMode(playerHasDeck = true, isWorldBuilder = false))

    @Test
    fun `completed league is not selectable without a deck`() = assertFalse(leagueIn("completed").isSelectableRunMode(playerHasDeck = false, isWorldBuilder = false))

    @Test
    fun `draft league is not selectable for a player who is not a builder of its world`() =
        assertFalse(leagueIn("draft").isSelectableRunMode(playerHasDeck = true, isWorldBuilder = false))

    @Test
    fun `draft league is selectable for a builder of its world and no deck`() =
        assertTrue(leagueIn("draft").isSelectableRunMode(playerHasDeck = false, isWorldBuilder = true))

    @Test
    fun `being a world builder does not make an archived league selectable`() =
        assertFalse(leagueIn("archived").isSelectableRunMode(playerHasDeck = true, isWorldBuilder = true))

    @Test
    fun `being a world builder does not change an active league`() =
        assertTrue(leagueIn("active").isSelectableRunMode(playerHasDeck = false, isWorldBuilder = true))

    @Test
    fun `archived league is not selectable`() = assertFalse(leagueIn("archived").isSelectableRunMode(playerHasDeck = true, isWorldBuilder = false))

    @Test
    fun `league with unknown state is not selectable`() = assertFalse(leagueIn(null).isSelectableRunMode(playerHasDeck = true, isWorldBuilder = false))

    @Test
    fun `active league is queueable`() = assertTrue(leagueIn("active").isQueueable(isWorldBuilder = false))

    @Test
    fun `draft league is not queueable for a player who is not a builder of its world`() = assertFalse(leagueIn("draft").isQueueable(isWorldBuilder = false))

    @Test
    fun `draft league is queueable for a builder of its world`() = assertTrue(leagueIn("draft").isQueueable(isWorldBuilder = true))

    @Test
    fun `completed league is not queueable`() = assertFalse(leagueIn("completed").isQueueable(isWorldBuilder = true))

    @Test
    fun `player listed in the world builders is a world builder`() = assertTrue(isWorldBuilder(listOf("Alice", "Bob"), "Bob"))

    @Test
    fun `world builder match ignores case`() = assertTrue(isWorldBuilder(listOf("4Ply"), "4ply"))

    @Test
    fun `player missing from the world builders is not a world builder`() = assertFalse(isWorldBuilder(listOf("Alice"), "Bob"))

    @Test
    fun `nobody is a builder of a world without builders`() = assertFalse(isWorldBuilder(null, "Bob"))
}
