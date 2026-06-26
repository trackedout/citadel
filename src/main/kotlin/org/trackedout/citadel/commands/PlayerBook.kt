package org.trackedout.citadel.commands

import net.kyori.adventure.inventory.Book
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.INVENTORY_FILTER_MODE_SCOREBOARD
import org.trackedout.citadel.async
import org.trackedout.client.apis.ConfigApi
import org.trackedout.client.apis.ScoreApi
import org.trackedout.data.findRunTypeById

fun showPlayerBook(plugin: Citadel, player: Player, configApi: ConfigApi, scoreApi: ScoreApi, page: Int = 0) {
    plugin.async(player) {
        val configs = try {
            configApi.configsListGet(entity = player.name).results ?: emptyList()
        } catch (e: Exception) { emptyList() }

        val currentRunMode = try {
            val score = scoreApi.scoresGet(player.name, prefixFilter = INVENTORY_FILTER_MODE_SCOREBOARD)
                .results?.firstOrNull()
            score?.let { findRunTypeById(it.value.toString()) }
        } catch (e: Exception) { null }

        val skipDoor = configs.find { it.key == "skip-door" }?.value == "true"
        val allowSpectating = configs.find { it.key == "allow-spectating" }?.value == "true"
        val shulkerStyle = configs.find { it.key == "shulker-style" }?.value ?: "CYAN"

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

        val runModeDisplay = currentRunMode?.displayName ?: "Competitive"
        val runModeColour = when (currentRunMode?.shortId) {
            'p' -> "green"
            'h' -> "red"
            else -> "aqua"
        }

        // ~114px per line, ~14 lines per page
        // Approx chars: lowercase ~6px, uppercase ~6px, space ~4px, bold +1px/char
        // Safe: ~19 chars/line
        fun shulkerBtn(id: String, tag: String, label: String): String {
            val selected = shulkerStyle.equals(id, ignoreCase = true)
            val display = if (selected) ">>$label<<" else label
            return "<click:run_command:'/do shulker-style $id'><$tag>[$display]</$tag></click>"
        }

        val pages = listOf(

            // Page 1: Run Mode
            // Centering: 114px line width, space=4px
            // >> Competitive << ~88px -> 3 spaces
            // [Competitive] ~70px -> 5 spaces
            // >> Practice << ~72px -> 5 spaces
            // [Practice] ~54px -> 7 spaces
            // >> Hardcore << ~72px -> 5 spaces
            // [Hardcore] ~54px -> 7 spaces
            """
   <italic><gold>Run Mode</gold> (click!)</italic>

${if (currentRunMode?.shortId == 'c') "   <aqua>>> Competitive <<</aqua>" else "     <click:run_command:'/do run-mode competitive'><aqua>[Competitive]</aqua></click>"}
Limited shards, standard rules, and new content!

${if (currentRunMode?.shortId == 'p') "     <green>>> Practice <<</green>" else "      <click:run_command:'/do run-mode practice'><green>[Practice]</green></click>"}
Infinite runs, open access to all cards.

${if (currentRunMode?.shortId == 'h') "     <red>>> Hardcore <<</red>" else "      <click:run_command:'/do run-mode hardcore'><red>[Hardcore]</red></click>"}
Limited shards, deck lost on death.
            """.trimIndent(),

            // Page 2: Quick actions
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

            // Page 3: Settings toggles
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

            // Page 4: Shulker colour
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
