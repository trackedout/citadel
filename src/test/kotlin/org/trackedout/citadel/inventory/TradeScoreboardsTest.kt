package org.trackedout.citadel.inventory

import org.trackedout.citadel.CatalogFixture
import org.trackedout.citadel.config.UnknownTradeItemException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TradeScoreboardsTest {
    private val competitive = CatalogFixture.load(CatalogFixture.COMPETITIVE)

    private fun trade(source: String, target: String) = Trade("do2-s2-comp", source, 1, target, 1, competitive)

    @Test
    fun `shards trade on the shard scoreboard`() = assertEquals("do2.inventory.shards", trade("SHARD", "dummy").sourceScoreboardName())

    @Test
    fun `crowns are earned on one scoreboard and spent on another`() {
        val trade = trade("CROWN", "dummy")

        assertEquals("do2.lifetime.escaped.crowns", trade.sourceScoreboardName())
        assertEquals("do2.lifetime.spent.crowns", trade.sourceInversionScoreboardName())
    }

    @Test
    fun `a dummy trade has no scoreboard`() = assertEquals("", trade("dummy", "dummy").sourceScoreboardName())

    @Test
    fun `an item without a score has no scoreboard`() = assertEquals("", trade("dummy", "COIN").targetScoreboardName())

    @Test
    fun `a queue target is the queue scoreboard`() = assertEquals("queue", trade("TOME", "QUEUE").targetScoreboardName())

    @Test
    fun `the item of a trade is found whatever its case`() = assertEquals("do2.lifetime.escaped.tomes", trade("tome", "dummy").sourceScoreboardName())

    @Test
    fun `an unknown item is rejected`() = assertFailsWith<UnknownTradeItemException> { trade("SHARDS", "dummy").sourceScoreboardName() }
}
