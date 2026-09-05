package org.trackedout.citadel.commands

import org.trackedout.client.models.League
import org.trackedout.client.models.LeagueDisplay
import kotlin.test.Test
import kotlin.test.assertTrue

class PlayerBookLayoutTest {

    private fun league(slug: String, name: String, description: String, colour: Int = 0x55FF55) =
        League(
            slug = slug,
            displayName = name,
            description = description,
            display = LeagueDisplay(runTypeId = 2, colour = colour, material = "GRAY_SHULKER_BOX"),
        )

    // The three competitive seasons from the screenshot that originally clipped.
    private val threeSeasons = listOf(
        league("season-1", "Season 1", "The first competitive season of Decked Out 2"),
        league("season-2", "Season 2", "The second competitive season of Decked Out 2"),
        league("season-3", "Season 3", "The third competitive season of Decked Out 2"),
    )

    @Test
    fun `all player book pages fit within a book page height`() {
        val pages = buildPlayerBookPages(
            leagues = threeSeasons,
            currentRunModeSlug = "season-3",
            skipDoor = false,
            allowSpectating = true,
            shulkerStyle = "CYAN",
        )

        val problems = BookLayout.validate(pages)
        assertTrue(problems.isEmpty(), "Book pages should all fit, but found:\n${problems.joinToString("\n")}")
    }

    @Test
    fun `run mode pages stay within height even with many long leagues`() {
        val many = (1..12).map {
            league(
                "season-$it",
                "Season $it",
                "A very long competitive season description number $it for Decked Out 2 that wraps a lot",
            )
        }

        val pages = buildPlayerBookPages(
            leagues = many,
            currentRunModeSlug = null,
            skipDoor = true,
            allowSpectating = false,
            shulkerStyle = "RED",
        )

        val problems = BookLayout.validate(pages)
        assertTrue(problems.isEmpty(), "Book pages should all fit with many leagues, but found:\n${problems.joinToString("\n")}")
    }

    @Test
    fun `no leagues still produces valid non-overflowing pages`() {
        val pages = buildPlayerBookPages(
            leagues = emptyList(),
            currentRunModeSlug = null,
            skipDoor = false,
            allowSpectating = false,
            shulkerStyle = "CYAN",
        )

        val problems = BookLayout.validate(pages)
        assertTrue(problems.isEmpty(), "Book pages should be valid with no leagues, but found:\n${problems.joinToString("\n")}")
    }

    @Test
    fun `line count detects an obviously overflowing page`() {
        val overflowing = (1..30).joinToString("\n") { "Line number $it of many" }
        assertTrue(!BookLayout.fits(overflowing), "A 30-line page must be reported as overflowing")
    }
}
