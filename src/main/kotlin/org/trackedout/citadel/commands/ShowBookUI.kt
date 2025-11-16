package org.trackedout.citadel.commands

import net.kyori.adventure.inventory.Book
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async

fun showBookUI(
    plugin: Citadel,
    player: Player,
) {
    plugin.async(player) {
        val page1 = """

       Welcome to

      <gold>Tracked Out</gold>

   --------------

   A free and public
      server for

     <gold>Decked Out 2</gold>

            """

        val page2 = """

           Links:

- <u><blue><click:open_url:'https://trackedout.org'>Tracked Out Website</click></blue></u>

- <u><blue><click:open_url:'https://trackedout.org'>Player Stats</click></blue></u>

- <u><blue><click:open_url:'https://trackedout.org/discord'>Discord</click></blue></u>

- <u><light_purple><click:open_url:'https://hermitcraft.fandom.com/wiki/Decked_Out_2'>Decked Out 2 wiki</click></light_purple></u>

- <u><gold><click:open_url:'https://modrinth.com/modpack/trackedout'>Modpack (optional)</click></gold></u>
        """.trimIndent()

        val page3 = """
       Run modes:

       <aqua>Competitive:</aqua>
Limited shards, standard rules, currently closed.

       <green>Practice:</green>
Infinite runs, open access to all cards.

       <red>Hardcore:</red>
Limited shards, deck is lost on death.
        """.trimIndent()

        val page4 = """
 How to play (page 1)

1. Use Minecraft <gold>1.20.1</gold>

2. [Optional] Install the <u><gold><click:open_url:'https://modrinth.com/modpack/trackedout'>Tracked Out Modpack</click></gold></u>

3. <green>Right-click</green> your deck in mid-air to <green>edit</green>!

4. Place a <aqua>Frozen Shard</aqua> in the barrel on the floor to <aqua>queue</aqua>.
        """.trimIndent()

        val page5 = """
 How to play (page 2)

5. <gold>Move cards</gold> to your inventory if you don't want to take them into the dungeon.

6. Press the button above the door to leave your instance when you're done, or run <gold>/lobby</gold> at any time
        """.trimIndent()

        val page6 = """
 How to play (page 3)

7. You don't need to wait to queue again, we have <gold>multiple dungeon servers</gold> running!

8. <gold>Multiple players</gold> can play at the same time in <gold>different dungeon instances.</gold>
        """.trimIndent()

        val pages = mutableListOf(
            page1,
            page2,
            page3,
            page4,
            page5,
            page6,
        )

        pages.add(
            """
  Common Questions:

1. How do I claim a cubby?
Stand in an available cubby and run: <gold>/do cubby claim</gold>

2. How do I report a bug?
Join our <u><blue><click:open_url:'https://trackedout.org/discord'>Discord</click></blue></u> and head to <gold>#bug-reports</gold>
        """.trimIndent()
        )

        pages.add(
            """
  Common Questions:

3. How do I spectate a run?

Right-click on the <gold>"Spectate" NPC</gold> next to the queue barrel.

To stop spectating run <gold>/lobby</gold>
        """.trimIndent()
        )

        pages.add("""
  Common Questions:

4. How do I switch run modes?

Run <gold>/do run-mode practice</gold> or <gold>/do run-mode competitive</gold> or <gold>/do run-mode hardcore</gold>
Note that competitive mode is <red>currently closed</red> as we build the next season.
        """.trimIndent())

        val components = pages.map {
            MiniMessage.miniMessage().deserialize(it);
        }

        val book = Book.book(Component.text("Welcome to Tracked Out"), Component.text("4Ply"), components)
        player.openBook(book)
    }
}
