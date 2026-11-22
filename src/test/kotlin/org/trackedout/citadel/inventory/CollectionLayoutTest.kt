package org.trackedout.citadel.inventory

import org.trackedout.citadel.CatalogFixture
import org.trackedout.client.models.CatalogLobbyAction
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CollectionLayoutTest {
    private val competitive = CatalogFixture.load(CatalogFixture.COMPETITIVE)
    private val artifacts = competitive.collectionEntries(CatalogLobbyAction.Kind.artifact)

    private fun entry(key: String, ember: Int?) = CollectionEntry(key, "do2.artifakes.$key", ember?.let { BigDecimal(it) })

    @Test
    fun `artifacts are counted by their scoreboard`() =
        assertEquals("do2.artifakes.HAY", artifacts.first { it.key == "do2:HAY" }.scoreboard)

    @Test
    fun `cards are counted by the times they were played`() =
        assertEquals("do2.cards.played.ADR", competitive.collectionEntries(CatalogLobbyAction.Kind.card).first { it.key == "do2:adrenaline_rush" }.scoreboard)

    @Test
    fun `items are collected by their only score`() =
        assertEquals(setOf("do2:SHARD", "do2:GAUNTLET_TROPHY"), competitive.collectionEntries(CatalogLobbyAction.Kind.item).map { it.key }.toSet())

    @Test
    fun `entries are sorted by ember value, lowest first`() =
        assertEquals(listOf("do2:HAY", "do2:ASV", "do2:HGT", "do2:GDE", "do2:MKY"), sortedCollection(artifacts, "emberValue").map { it.key })

    @Test
    fun `entries without an ember value come last`() =
        assertEquals(listOf("b", "a", "c"), sortedCollection(listOf(entry("a", null), entry("b", 1), entry("c", null)), "emberValue").map { it.key })

    @Test
    fun `entries with equal ember values keep the catalog order`() =
        assertEquals(listOf("x", "y", "z"), sortedCollection(listOf(entry("x", 5), entry("y", 5), entry("z", 5)), "emberValue").map { it.key })

    @Test
    fun `entries are kept in catalog order without a sort`() =
        assertEquals(listOf("b", "a"), sortedCollection(listOf(entry("b", 2), entry("a", 1)), null).map { it.key })

    @Test
    fun `an unknown sort is rejected`() = assertFailsWith<IllegalArgumentException> { sortedCollection(artifacts, "name") }

    @Test
    fun `the first rows are filled in order`() = assertEquals((0 until 27).toList(), collectionSlots(27, 4))

    @Test
    fun `entries past the full rows start in the middle of the last row`() = assertEquals(31, collectionSlots(28, 4).last())

    @Test
    fun `the last row fits five entries`() = assertEquals(listOf(31, 32, 33, 34, 35), collectionSlots(32, 4).takeLast(5))

    @Test
    fun `more entries than slots are rejected`() = assertFailsWith<IllegalArgumentException> { collectionSlots(33, 4) }

    @Test
    fun `a collection needs a row`() = assertFailsWith<IllegalArgumentException> { collectionSlots(1, 0) }

    @Test
    fun `an entry the owner has none of counts as zero`() = assertEquals(0, entry("HAY", 6).countIn(emptyMap()))

    @Test
    fun `an entry counts its scoreboard`() = assertEquals(3, entry("HAY", 6).countIn(mapOf("do2.artifakes.HAY" to 3)))

    @Test
    fun `the owner sees their own collection as Your`() = assertEquals("Your S2 Competitive Artifakes", collectionTitle("{owner} S2 Competitive Artifakes", "Pawel", "Pawel"))

    @Test
    fun `a viewer sees another player's collection under their name`() =
        assertEquals("4Ply's S2 Competitive Artifakes", collectionTitle("{owner} S2 Competitive Artifakes", "Pawel", "4Ply"))

    @Test
    fun `the scores to fetch share the start of the entries' scoreboards`() = assertEquals("do2.artifakes.", scorePrefixOf(artifacts))

    @Test
    fun `no entries have no prefix`() = assertEquals(null, scorePrefixOf(emptyList()))

    @Test
    fun `an artifact shows as its tag says`() {
        val display = artifactDisplay("do2:GDE", competitive.document.artifacts.getValue("do2:GDE"))

        assertEquals("""{"bold":true,"color":"gold","text":"Golden Eye (34)"}""", display.name)
        assertEquals(listOf("""{"text":"iMazing \"I\" iNscribed iNside"}"""), display.lore)
        assertEquals(19, display.customModelData)
    }
}
