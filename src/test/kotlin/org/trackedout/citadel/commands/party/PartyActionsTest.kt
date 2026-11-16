package org.trackedout.citadel.commands.party

import org.trackedout.client.models.Party
import kotlin.test.Test
import kotlin.test.assertEquals

class PartyActionsTest {
    private val leader = "Leader"
    private val member = "Member"

    private fun party(state: String = "forming", members: List<String> = listOf(leader, member)) =
        Party(leader = leader, slug = "do2-s2-practice", members = members, state = state)

    @Test
    fun `player without a party can create or join`() =
        assertEquals(listOf(PartyAction.CREATE, PartyAction.JOIN), partyActionsFor(null, member))

    @Test
    fun `member can see info or leave`() =
        assertEquals(listOf(PartyAction.INFO, PartyAction.LEAVE), partyActionsFor(party(), member))

    @Test
    fun `leader of a forming party can invite, kick and disband`() =
        assertEquals(
            listOf(PartyAction.INFO, PartyAction.INVITE, PartyAction.KICK, PartyAction.DISBAND),
            partyActionsFor(party(), leader),
        )

    @Test
    fun `leader alone has nobody to kick`() =
        assertEquals(
            listOf(PartyAction.INFO, PartyAction.INVITE, PartyAction.DISBAND),
            partyActionsFor(party(members = listOf(leader)), leader),
        )

    @Test
    fun `leader of a queued party cannot invite`() =
        assertEquals(
            listOf(PartyAction.INFO, PartyAction.KICK, PartyAction.DISBAND),
            partyActionsFor(party(state = "queued"), leader),
        )
}
