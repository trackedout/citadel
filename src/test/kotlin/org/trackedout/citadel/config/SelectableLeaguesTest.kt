package org.trackedout.citadel.config

import org.trackedout.client.models.League
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectableLeaguesTest {
    private fun leagueIn(state: String?) = League(slug = "do2-s2-comp", state = state)

    @Test
    fun `active league is selectable without a deck`() = assertTrue(leagueIn("active").isSelectableRunMode(playerHasDeck = false))

    @Test
    fun `completed league is selectable with a deck`() = assertTrue(leagueIn("completed").isSelectableRunMode(playerHasDeck = true))

    @Test
    fun `completed league is not selectable without a deck`() = assertFalse(leagueIn("completed").isSelectableRunMode(playerHasDeck = false))

    @Test
    fun `draft league is not selectable`() = assertFalse(leagueIn("draft").isSelectableRunMode(playerHasDeck = true))

    @Test
    fun `archived league is not selectable`() = assertFalse(leagueIn("archived").isSelectableRunMode(playerHasDeck = true))

    @Test
    fun `league with unknown state is not selectable`() = assertFalse(leagueIn(null).isSelectableRunMode(playerHasDeck = true))
}
