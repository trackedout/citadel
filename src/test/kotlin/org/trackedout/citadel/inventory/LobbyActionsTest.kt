package org.trackedout.citadel.inventory

import org.trackedout.citadel.CatalogFixture
import org.trackedout.client.models.CatalogLobbyAction
import kotlin.test.Test
import kotlin.test.assertEquals

class LobbyActionsTest {
    private val competitive = CatalogFixture.load(CatalogFixture.COMPETITIVE)
    private val metro = CatalogFixture.load(CatalogFixture.METRO_MAYHEM)

    private class RecordingHandler : LobbyActionHandler {
        val calls = mutableListOf<String>()

        override fun openDeck() {
            calls += "open-deck"
        }

        override fun queue() {
            calls += "queue"
        }

        override fun showCollection(action: CatalogLobbyAction) {
            calls += "show-collection ${action.kind?.value}"
        }
    }

    private fun dispatched(action: CatalogLobbyAction) = RecordingHandler().also { action.dispatchTo(it) }.calls

    @Test
    fun `the shard item queues`() = assertEquals(listOf("queue"), dispatched(competitive.lobbyAction(0)))

    @Test
    fun `the deck opens the deck`() = assertEquals(listOf("open-deck"), dispatched(competitive.lobbyAction(1)))

    @Test
    fun `the artifacts show their collection`() = assertEquals(listOf("show-collection artifact"), dispatched(competitive.lobbyAction(2)))

    @Test
    fun `the minigame stick queues`() = assertEquals(listOf("queue"), dispatched(metro.lobbyAction(0)))
}
