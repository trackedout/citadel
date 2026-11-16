package org.trackedout.citadel.config

import org.trackedout.client.models.League
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectableLeaguesTest {
    private fun leagueIn(state: String?) = League(slug = "do2-s2-comp", state = state)

    @Test
    fun `active league is selectable`() = assertTrue(leagueIn("active").isSelectableRunMode())

    @Test
    fun `completed league is selectable`() = assertTrue(leagueIn("completed").isSelectableRunMode())

    @Test
    fun `draft league is not selectable`() = assertFalse(leagueIn("draft").isSelectableRunMode())

    @Test
    fun `archived league is not selectable`() = assertFalse(leagueIn("archived").isSelectableRunMode())

    @Test
    fun `league with unknown state is not selectable`() = assertFalse(leagueIn(null).isSelectableRunMode())
}
