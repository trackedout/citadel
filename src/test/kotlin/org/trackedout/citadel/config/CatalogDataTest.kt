package org.trackedout.citadel.config

import org.trackedout.citadel.CatalogFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CatalogDataTest {
    private val competitive = CatalogFixture.load(CatalogFixture.COMPETITIVE)

    @Test
    fun `trade items are keyed by their trade id`() =
        assertEquals("do2:level_2_key", competitive.tradeItem("CAVES_OF_CARNAGE_KEY").catalogKey)

    @Test
    fun `an item without a lobby facet is not a trade item`() = assertFailsWith<UnknownTradeItemException> { competitive.tradeItem("FROST_EMBER") }

    @Test
    fun `the seventeen items that go into the dungeon are those taken into the dungeon`() {
        val ids = competitive.intoDungeonItems.map { it.tradeId }

        assertEquals(17, ids.size)
        assertEquals(true, "COIN" in ids)
    }

    @Test
    fun `items shown by their score are the ones earned in the dungeon`() =
        assertEquals(
            setOf("CROWN", "TOME", "SHARD", "SHARD_FRAGMENT", "GAUNTLET_TROPHY"),
            competitive.scoredTradeItems.map { it.tradeId }.toSet(),
        )

    @Test
    fun `an item earned and spent has a different score for each`() {
        val crown = competitive.tradeItem("CROWN")

        assertEquals("do2.lifetime.escaped.crowns", crown.sourceScoreboard)
        assertEquals("do2.lifetime.spent.crowns", crown.inversionScoreboard)
    }

    @Test
    fun `an item with one score is drawn down from that score`() {
        val shard = competitive.tradeItem("SHARD")

        assertEquals("do2.inventory.shards", shard.sourceScoreboard)
        assertEquals("do2.inventory.shards", shard.inversionScoreboard)
    }

    @Test
    fun `an item with no score has no scoreboard`() = assertEquals(null, competitive.tradeItem("COIN").sourceScoreboard)

    @Test
    fun `the lobby of a deck league counts the shards`() = assertEquals(setOf("do2.inventory.shards"), competitive.lobbyCountScoreboards())

    @Test
    fun `the lobby of a minigame counts no score`() = assertEquals(emptySet(), CatalogFixture.load(CatalogFixture.METRO_MAYHEM).lobbyCountScoreboards())

    @Test
    fun `the inventory scores of a league are those of its scored items and lobby counts`() =
        assertEquals(
            setOf(
                "do2.lifetime.escaped.crowns", "do2.lifetime.spent.crowns",
                "do2.lifetime.escaped.tomes", "do2.lifetime.spent.tomes",
                "do2.lifetime.escaped.shard_fragments", "do2.lifetime.spent.shard_fragments",
                "do2.lifetime.gauntlet_survival", "do2.inventory.shards",
            ),
            competitive.inventoryScoreboards(),
        )

    @Test
    fun `the lobby entry that names an item is found from the item`() = assertEquals(0, competitive.lobbyEntryFor(competitive.tradeItem("SHARD"))?.index)

    @Test
    fun `an item no lobby entry names has no entry`() = assertEquals(null, competitive.lobbyEntryFor(competitive.tradeItem("CROWN")))

    @Test
    fun `an entry names its item by the catalog key`() = assertEquals("SHARD", competitive.tradeItemOf(competitive.lobby[0])?.tradeId)

    @Test
    fun `an entry that names no item has none`() = assertEquals(null, competitive.tradeItemOf(competitive.lobby[1]))

    @Test
    fun `a lobby entry past the end of the list has no action`() = assertFailsWith<UnknownLobbyEntryException> { competitive.lobbyAction(3) }

    @Test
    fun `cards are keyed by catalog key`() = assertEquals("ADR", competitive.cards.getValue("do2:adrenaline_rush").shorthand)

    @Test
    fun `a count that is a number is a fixed amount`() = assertEquals(LobbyCount.Fixed(1), LobbyCount.of(competitive.lobby[1].count))

    @Test
    fun `a JSON number the client reads as a double is a fixed amount`() = assertEquals(LobbyCount.Fixed(3), LobbyCount.of(3.0))

    @Test
    fun `a count that is a scoreboard object is that scoreboard`() =
        assertEquals(LobbyCount.Scoreboard("do2.inventory.shards"), LobbyCount.of(competitive.lobby[0].count))

    @Test
    fun `a fractional count is rejected`() = assertFailsWith<IllegalArgumentException> { LobbyCount.of(1.5) }

    @Test
    fun `a missing count is rejected`() = assertFailsWith<IllegalArgumentException> { LobbyCount.of(null) }

    @Test
    fun `a count object without a scoreboard is rejected`() = assertFailsWith<IllegalArgumentException> { LobbyCount.of(mapOf("other" to "x")) }
}
