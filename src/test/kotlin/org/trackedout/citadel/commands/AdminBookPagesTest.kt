package org.trackedout.citadel.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdminBookPagesTest {
    private val s1 = AdminWorld("do2-s1", "builders")
    private val s2 = AdminWorld("do2-s2", null)
    private val s3 = AdminWorld("do2-s3", "builders2")
    private val toggles = AdminToggles(details = false, networkStatus = true, clickDebug = false)

    private fun pages(
        dungeons: List<AdminDungeon>,
        assignable: List<AdminWorld> = listOf(s2, s3),
        known: List<AdminWorld> = listOf(s1, s2, s3),
    ) = buildAdminBookPages(dungeons, assignable, known, toggles)

    private fun dungeonText(pages: List<String>) = pages.filter { it.contains("Dungeon Management") }.joinToString("\n")

    @Test
    fun `run mode defaults page is gone`() {
        assertFalse(pages(emptyList()).joinToString("\n").contains("default-dungeon-type"))
    }

    @Test
    fun `world buttons write the world slug to the dungeon-type config`() {
        val text = dungeonText(pages(listOf(AdminDungeon("d800", "do2-s2"))))
        assertTrue(text.contains("/do config set d800 dungeon-type do2-s3"))
    }

    @Test
    fun `the current world is highlighted and not clickable`() {
        val text = dungeonText(pages(listOf(AdminDungeon("d800", "do2-s2"))))
        assertFalse(text.contains("dungeon-type do2-s2'"))
    }

    @Test
    fun `worlds without an active league get no button`() {
        val text = dungeonText(pages(listOf(AdminDungeon("d800", "do2-s2"))))
        assertFalse(text.contains("dungeon-type do2-s1"))
    }

    @Test
    fun `unknown dungeon-type is shown raw in red`() {
        val text = dungeonText(pages(listOf(AdminDungeon("d800", "default"))))
        assertTrue(text.contains("<red>default</red>"))
    }

    @Test
    fun `missing dungeon-type is shown as unset and not defaulted`() {
        val text = dungeonText(pages(listOf(AdminDungeon("d800", null))))
        assertTrue(text.contains("<red>unset</red>"))
    }

    @Test
    fun `snapshot buttons come from world build servers`() {
        val backups = pages(emptyList()).single { it.contains("Backups") }
        assertTrue(backups.contains("/k8s create-snapshot builders'"))
        assertTrue(backups.contains("[Snapshot builders2 (do2-s3)]"))
        assertFalse(backups.contains("do2-s2"))
    }

    @Test
    fun `many dungeons paginate and every page fits`() {
        val dungeons = (800..819).map { AdminDungeon("d$it", "do2-s3") }
        val result = pages(dungeons)
        assertTrue(dungeonText(result).contains("d819"))
        assertEquals(emptyList<String>(), BookLayout.validate(result))
    }

    @Test
    fun `the shutdown all button appears once`() {
        val result = pages((800..819).map { AdminDungeon("d$it", null) })
        assertEquals(1, result.joinToString("\n").split("shutdown-all-empty-dungeons").size - 1)
    }
}
