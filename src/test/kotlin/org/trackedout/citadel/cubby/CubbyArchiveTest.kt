package org.trackedout.citadel.cubby

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CubbyArchiveTest {
    private val dataFolder: File = createTempDirectory("cubby-archive-test").toFile()
    private val archive = CubbyArchive(dataFolder)

    private fun archived(owner: String, vararg names: String) = names.forEach {
        File(dataFolder, "cubby-archive/$owner/$it").apply { parentFile.mkdirs() }.writeText("")
    }

    @Test
    fun `entries are listed newest first`() {
        archived("jonah", "20261126T022757Z_cubby-7_EAST.schem", "20261126T022852Z_cubby-7_EAST.schem", "20261122T222219Z_cubby-6_WEST.schem")

        assertEquals(listOf("20261126T022852Z", "20261126T022757Z", "20261122T222219Z"), archive.entriesFor("jonah").map { it.timestamp })
    }

    @Test
    fun `an older archive can be found by its timestamp`() {
        archived("jonah", "20261126T022757Z_cubby-7_EAST.schem", "20261126T022852Z_cubby-8_NORTH.schem")

        assertEquals("cubby-7", archive.find("jonah", "20261126t022757z")?.cubbyId)
    }

    @Test
    fun `an unknown timestamp finds nothing`() {
        archived("jonah", "20261126T022757Z_cubby-7_EAST.schem")

        assertNull(archive.find("jonah", "20200101T000000Z"))
    }

    @Test
    fun `the latest archive is the newest entry`() {
        archived("jonah", "20261126T022757Z_cubby-7_EAST.schem", "20261126T022852Z_cubby-8_NORTH.schem")

        assertEquals("cubby-8", archive.latestFor("jonah")?.cubbyId)
    }

    @Test
    fun `an unsafe player name has no entries`() {
        assertEquals(emptyList(), archive.entriesFor("../etc"))
    }
}
