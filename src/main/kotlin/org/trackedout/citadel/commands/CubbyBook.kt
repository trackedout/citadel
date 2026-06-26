package org.trackedout.citadel.commands

import net.kyori.adventure.inventory.Book
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

fun showCubbyBook(player: Player) {
    val pages = listOf(
        """
     <gold>Cubbies</gold>

A cubby is your space to express yourself!

Stand inside an empty
cubby to claim it!

<click:run_command:'/do cubby claim'><gold>[Claim a Cubby]</gold></click>

<click:run_command:'/do cubby locate'><gold>[Find My Cubby]</gold></click>

<click:run_command:'/do cubby tp'><gold>[Go to My Cubby]</gold></click>
        """.trimIndent(),

        """
 <gold>Visit Other Cubbies</gold>

To visit someone's cubby, run:
<gold>/do cubby tp <name></gold>

Replace <name> with
their player name.
        """.trimIndent(),
    )

    val components = pages.map { MiniMessage.miniMessage().deserialize(it) }
    val book = Book.book(Component.text("Cubbies"), Component.text("Tracked Out"), components)
    player.openBook(book)
}
