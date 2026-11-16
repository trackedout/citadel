package org.trackedout.citadel.commands

import net.kyori.adventure.inventory.Book
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.DEFAULT_FILTER_MODE_SLUG
import org.trackedout.citadel.FILTER_MODE_SLUG_CONFIG
import org.trackedout.citadel.async
import org.trackedout.client.apis.ConfigApi
import org.trackedout.client.apis.ScoreApi
import org.trackedout.citadel.config.isSelectableRunMode
import org.trackedout.citadel.config.runTypeFromLeague

fun showPlayerBook(plugin: Citadel, player: Player, configApi: ConfigApi, scoreApi: ScoreApi, page: Int = 0) {
    plugin.async(player) {
        val configs = try {
            configApi.configsListGet(entity = player.name).results ?: emptyList()
        } catch (e: Exception) { emptyList() }

        val currentRunMode = try {
            val slug = configs.find { it.key == FILTER_MODE_SLUG_CONFIG }?.value?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_FILTER_MODE_SLUG
            plugin.leagueCache.getLeagueBySlug(slug)?.let { runTypeFromLeague(it) }
        } catch (e: Exception) { null }

        val skipDoor = configs.find { it.key == "skip-door" }?.value == "true"
        val allowSpectating = configs.find { it.key == "allow-spectating" }?.value == "true"
        val shulkerStyle = configs.find { it.key == "shulker-style" }?.value ?: "CYAN"

        val pages = buildPlayerBookPages(
            leagues = plugin.leagueCache.getLeagues().filter { it.isSelectableRunMode() },
            currentRunModeSlug = currentRunMode?.longId,
            skipDoor = skipDoor,
            allowSpectating = allowSpectating,
            shulkerStyle = shulkerStyle,
        )

        // Rotate pages so the target page appears first
        val rotated = if (page > 0 && page < pages.size) {
            pages.subList(page, pages.size) + pages.subList(0, page)
        } else {
            pages
        }

        val components = rotated.map { MiniMessage.miniMessage().deserialize(it) }
        val book = Book.book(Component.text("Player Settings"), Component.text("Tracked Out"), components)
        player.openBook(book)
    }
}

/**
 * Builds the MiniMessage source for every player-settings book page.
 *
 * Pure and Bukkit-free so it can be unit-tested (see PlayerBookLayoutTest) to guard
 * against page overflow — the "Run Mode" pages are packed so each stays within
 * the vanilla book page height regardless of how many leagues exist.
 */
fun buildPlayerBookPages(
    leagues: List<org.trackedout.client.models.League>,
    currentRunModeSlug: String?,
    skipDoor: Boolean,
    allowSpectating: Boolean,
    shulkerStyle: String,
): List<String> {
    val shulkerColourTag = when (shulkerStyle.uppercase()) {
        "WHITE" -> "dark_gray"
        "ORANGE" -> "#FF8800"
        "MAGENTA" -> "light_purple"
        "LIGHT_BLUE" -> "aqua"
        "YELLOW" -> "yellow"
        "LIME" -> "green"
        "PINK" -> "#ff85c2"
        "GRAY" -> "dark_gray"
        "LIGHT_GRAY" -> "gray"
        "CYAN" -> "dark_aqua"
        "PURPLE" -> "dark_purple"
        "BLUE" -> "blue"
        "BROWN" -> "#835432"
        "GREEN" -> "dark_green"
        "RED" -> "red"
        "BLACK" -> "black"
        else -> "aqua"
    }

    fun shulkerBtn(id: String, tag: String, label: String): String {
        val selected = shulkerStyle.equals(id, ignoreCase = true)
        val display = if (selected) ">>$label<<" else label
        return "<click:run_command:'/do shulker-style $id'><$tag>[$display]</$tag></click>"
    }

    fun leagueEntry(league: org.trackedout.client.models.League): String {
        val colour = league.display?.colour?.let { String.format("#%06x", it) } ?: "#ffffff"
        val slug = league.slug ?: "unknown"
        val name = league.displayName ?: slug
        val isSelected = currentRunModeSlug == slug
        val description = league.description ?: ""

        return if (isSelected) {
            "<color:$colour>>> $name <<</color>\n$description"
        } else {
            "<click:run_command:'/do run-mode $slug'><color:$colour>[$name]</color></click>\n$description"
        }
    }

    // Pack league entries onto as many "Run Mode" pages as needed, adding a
    // league only while the page still fits the vanilla book height. This keeps
    // the last description from clipping no matter how many leagues exist.
    val runModePages = mutableListOf<String>()
    var pending = mutableListOf<String>()

    fun renderRunModePage(entries: List<String>, isFirst: Boolean): String {
        val header = if (isFirst) {
            "   <italic><gold>Run Mode</gold> (click!)</italic>"
        } else {
            "   <italic><gold>Run Mode</gold> (cont.)</italic>"
        }
        return "$header\n\n${entries.joinToString("\n\n")}"
    }

    for (league in leagues) {
        val entry = leagueEntry(league)
        val candidate = pending + entry
        val isFirst = runModePages.isEmpty()
        if (pending.isNotEmpty() && !BookLayout.fits(renderRunModePage(candidate, isFirst))) {
            runModePages.add(renderRunModePage(pending, isFirst))
            pending = mutableListOf(entry)
        } else {
            pending = candidate.toMutableList()
        }
    }
    if (pending.isNotEmpty()) {
        runModePages.add(renderRunModePage(pending, runModePages.isEmpty()))
    }

    return runModePages + listOf(

        // Quick actions
        """
      <italic><gold>Quick Actions</gold></italic>

    <click:run_command:'/do spectate'><gold>[Spectate a Run]</gold></click>
 Watch someone play!

      <click:run_command:'/do book cubby'><gold>[Cubby Guide]</gold></click>
 Claim, locate, and visit
 cubbies.

      <click:run_command:'/do unstuck'><gold>[Get Unstuck]</gold></click>
 Teleport to the lobby area.
        """.trimIndent(),

        // Settings toggles
        """
     <italic><gold>Settings</gold></italic>

 <bold>Skip Door:</bold>
 ${if (skipDoor) "<green>ON</green>" else "<red>OFF</red>"} <click:run_command:'/do setting toggle skip-door'><gold>[Toggle]</gold></click>
 Skip the door animation
 when entering.

 <bold>Allow Spectating:</bold>
 ${if (allowSpectating) "<green>ON</green>" else "<red>OFF</red>"} <click:run_command:'/do setting toggle allow-spectating'><gold>[Toggle]</gold></click>
 Let others watch
 your runs.
        """.trimIndent(),

        // Shulker colour
        """
     <italic><gold>Shulker Colour</gold></italic>
 <italic><aqua>(for competitive mode)</aqua></italic>

 Current: <${shulkerColourTag}>${shulkerStyle.replace('_', ' ').uppercase()}</${shulkerColourTag}>

 ${shulkerBtn("white", "dark_gray", "White")} ${shulkerBtn("orange", "#FF8800", "Orange")}
 ${shulkerBtn("light_blue", "aqua", "Light Blue")} ${shulkerBtn("cyan", "dark_aqua", "Cyan")}
 ${shulkerBtn("lime", "green", "Lime")} ${shulkerBtn("pink", "#ff85c2", "Pink")}
 ${shulkerBtn("purple", "dark_purple", "Purple")} ${shulkerBtn("red", "red", "Red")}
 ${shulkerBtn("blue", "blue", "Blue")} ${shulkerBtn("yellow", "dark_gray", "Yellow")}
 ${shulkerBtn("black", "dark_gray", "Black")}
        """.trimIndent(),

    )
}
