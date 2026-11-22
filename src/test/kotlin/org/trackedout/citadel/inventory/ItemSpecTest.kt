package org.trackedout.citadel.inventory

import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.trackedout.citadel.CatalogFixture
import org.trackedout.citadel.config.CatalogData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ItemSpecTest {
    private val competitive = CatalogFixture.load(CatalogFixture.COMPETITIVE)
    private val practice = CatalogFixture.load(CatalogFixture.PRACTICE)
    private val metro = CatalogFixture.load(CatalogFixture.METRO_MAYHEM)

    private fun spec(catalog: CatalogData, index: Int, count: Int, override: String? = null): ItemSpec {
        val entry = catalog.lobby[index]
        return lobbyEntrySpec(catalog.slug, index, entry, catalog.tradeItemOf(entry), count, override)
    }

    @Test
    fun `the shard entry is the shard item`() {
        val shards = spec(competitive, 0, 22)

        assertEquals("IRON_NUGGET", shards.material)
        assertEquals("S2 Competitive runs", shards.name)
        assertEquals(TextColor.fromHexString("#55ffff"), shards.colour)
        assertEquals(7, shards.customModelData)
        assertEquals(22, shards.amount)
    }

    @Test
    fun `the shard entry is tagged with its league, item and position`() =
        assertEquals(
            mapOf(SLUG_TAG to "do2-s2-comp", TRADE_ID_TAG to "SHARD", LOBBY_ENTRY_TAG to "0"),
            spec(competitive, 0, 22).tags,
        )

    @Test
    fun `an entry that names no item is tagged with its league and position`() =
        assertEquals(mapOf(SLUG_TAG to "do2-s2-comp", LOBBY_ENTRY_TAG to "1"), spec(competitive, 1, 1).tags)

    @Test
    fun `the deck entry takes the material the player chose`() = assertEquals("PINK_SHULKER_BOX", spec(competitive, 1, 1, "PINK_SHULKER_BOX").material)

    @Test
    fun `the deck entry keeps its own material without a choice`() = assertEquals("CYAN_SHULKER_BOX", spec(competitive, 1, 1).material)

    @Test
    fun `the artifact entry has no lore of its own`() = assertEquals(emptyList(), spec(competitive, 2, 1).lore)

    @Test
    fun `the practice deck entry is the one that has no player colour`() = assertNull(practice.lobby[1].action.playerColour)

    @Test
    fun `the minigame queue entry is a stick without a model`() {
        val queue = spec(metro, 0, 1)

        assertEquals("STICK", queue.material)
        assertEquals("Metro Mayhem Queue", queue.name)
        assertEquals(TextColor.fromHexString("#ff5555"), queue.colour)
        assertNull(queue.customModelData)
        assertEquals(mapOf(SLUG_TAG to "metro-mayhem", LOBBY_ENTRY_TAG to "0"), queue.tags)
    }

    @Test
    fun `none of an item is a stack of the absent amount`() = assertEquals(ABSENT_ITEM_AMOUNT, spec(competitive, 0, 0).amount)

    @Test
    fun `an invalid colour is rejected`() = assertFailsWith<IllegalArgumentException> { colourOf("cyan") }

    @Test
    fun `an entry counts its scoreboard from the player's scores`() =
        assertEquals(22, lobbyEntryCount(competitive.lobby[0]) { if (it == "do2.inventory.shards") 22 else error("Unexpected $it") })

    @Test
    fun `an entry with a fixed count ignores the scores`() = assertEquals(1, lobbyEntryCount(competitive.lobby[1]) { error("No score is read") })

    @Test
    fun `an item taken into the dungeon is tagged so`() {
        val key = tradeItemSpec("do2-s2-comp", competitive.tradeItem("CAVES_OF_CARNAGE_KEY"), 2)

        assertEquals(
            mapOf(SLUG_TAG to "do2-s2-comp", TRADE_ID_TAG to "CAVES_OF_CARNAGE_KEY", CAN_TAKE_INTO_DUNGEON_TAG to "1"),
            key.tags,
        )
        assertEquals(201, key.customModelData)
        assertEquals(2, key.amount)
    }

    @Test
    fun `an item consumed on entry says so`() =
        assertEquals(listOf(LoreLine("Consumed on dungeon entry", NamedTextColor.RED)), tradeItemSpec("do2-s2-comp", competitive.tradeItem("COIN"), 1).lore)

    @Test
    fun `an item returned on a win says so`() =
        assertEquals(listOf(LoreLine("Returned to deck on win", NamedTextColor.GREEN)), tradeItemSpec("do2-s2-comp", competitive.tradeItem("ICE"), 1).lore)

    @Test
    fun `an item kept in the lobby has no lore`() = assertEquals(emptyList(), tradeItemSpec("do2-s2-comp", competitive.tradeItem("CROWN"), 1).lore)

    @Test
    fun `a potion carries its potion type`() {
        val potion = tradeItemSpec("do2-s2-comp", competitive.tradeItem("SWIFTNESS_POTION"), 1)

        assertEquals("SPLASH_POTION", potion.material)
        assertEquals("SPEED", potion.potion?.type)
        assertEquals(true, potion.potion?.upgraded)
        assertEquals(-1, potion.customModelData)
    }

    @Test
    fun `an item earned and spent is held as the difference`() {
        val scores = mapOf("do2.lifetime.escaped.crowns" to 7, "do2.lifetime.spent.crowns" to 3)

        assertEquals(4, competitive.tradeItem("CROWN").heldCount { scores[it] })
    }

    @Test
    fun `an item never earned is not held`() = assertEquals(0, competitive.tradeItem("CROWN").heldCount { null })

    @Test
    fun `an item with one score is held as that score`() = assertEquals(5, competitive.tradeItem("TOME").heldCount { if (it.contains("escaped")) 5 else null })

    @Test
    fun `the gauntlet trophy is held once however often it was earned`() = assertEquals(1, competitive.tradeItem("GAUNTLET_TROPHY").heldCount { 4 })
}
