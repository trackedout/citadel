package org.trackedout.citadel.config

import org.trackedout.client.models.League
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectableLeaguesTest {
    private fun leagueIn(state: String?) = League(slug = "do2-s2-comp", state = state)

    @Test
    fun `active league is selectable without a deck`() = assertTrue(leagueIn("active").isSelectableRunMode(playerHasDeck = false, canQueueDrafts = false))

    @Test
    fun `completed league is selectable with a deck`() = assertTrue(leagueIn("completed").isSelectableRunMode(playerHasDeck = true, canQueueDrafts = false))

    @Test
    fun `completed league is not selectable without a deck`() = assertFalse(leagueIn("completed").isSelectableRunMode(playerHasDeck = false, canQueueDrafts = false))

    @Test
    fun `draft league is not selectable without the draft permission`() =
        assertFalse(leagueIn("draft").isSelectableRunMode(playerHasDeck = true, canQueueDrafts = false))

    @Test
    fun `draft league is selectable with the draft permission and no deck`() =
        assertTrue(leagueIn("draft").isSelectableRunMode(playerHasDeck = false, canQueueDrafts = true))

    @Test
    fun `draft permission does not make an archived league selectable`() =
        assertFalse(leagueIn("archived").isSelectableRunMode(playerHasDeck = true, canQueueDrafts = true))

    @Test
    fun `draft permission does not change an active league`() =
        assertTrue(leagueIn("active").isSelectableRunMode(playerHasDeck = false, canQueueDrafts = true))

    @Test
    fun `archived league is not selectable`() = assertFalse(leagueIn("archived").isSelectableRunMode(playerHasDeck = true, canQueueDrafts = false))

    @Test
    fun `league with unknown state is not selectable`() = assertFalse(leagueIn(null).isSelectableRunMode(playerHasDeck = true, canQueueDrafts = false))

    @Test
    fun `active league is queueable`() = assertTrue(leagueIn("active").isQueueable(canQueueDrafts = false))

    @Test
    fun `draft league is not queueable without the draft permission`() = assertFalse(leagueIn("draft").isQueueable(canQueueDrafts = false))

    @Test
    fun `draft league is queueable with the draft permission`() = assertTrue(leagueIn("draft").isQueueable(canQueueDrafts = true))

    @Test
    fun `completed league is not queueable`() = assertFalse(leagueIn("completed").isQueueable(canQueueDrafts = true))
}
