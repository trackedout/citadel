package org.trackedout.citadel.commands

import net.kyori.adventure.inventory.Book
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

fun showAdminBook(player: Player) {
    val pages = listOf(
        """
     <red>Admin Book</red>

   <gold>Dungeon Management</gold>

<click:run_command:'/do shutdown-all-empty-dungeons'><red>[Shutdown All Empty]</red></click>

   Shutdown individual:
<click:run_command:'/do config set d800 shutdown true --force'><gold>[d800]</gold></click> <click:run_command:'/do config set d801 shutdown true --force'><gold>[d801]</gold></click> <click:run_command:'/do config set d802 shutdown true --force'><gold>[d802]</gold></click>
<click:run_command:'/do config set d803 shutdown true --force'><gold>[d803]</gold></click> <click:run_command:'/do config set d804 shutdown true --force'><gold>[d804]</gold></click>
        """.trimIndent(),

        """
      <gold>Backups</gold>

<click:run_command:'/k8s create-snapshot builders'><aqua>[Snapshot Builders]</aqua></click>

<click:run_command:'/k8s create-snapshot builders2'><aqua>[Snapshot Builders2]</aqua></click>

<click:run_command:'/k8s backup-database'><aqua>[Backup Database]</aqua></click>
        """.trimIndent(),

        """
   <gold>Instance Config</gold>

Set dungeon-type to default:
<click:run_command:'/do config set d800 dungeon-type default'><gold>[d800]</gold></click> <click:run_command:'/do config set d801 dungeon-type default'><gold>[d801]</gold></click> <click:run_command:'/do config set d802 dungeon-type default'><gold>[d802]</gold></click>
<click:run_command:'/do config set d803 dungeon-type default'><gold>[d803]</gold></click> <click:run_command:'/do config set d804 dungeon-type default'><gold>[d804]</gold></click>

Set dungeon-type to season-2:
<click:run_command:'/do config set d800 dungeon-type season-2'><aqua>[d800]</aqua></click> <click:run_command:'/do config set d801 dungeon-type season-2'><aqua>[d801]</aqua></click> <click:run_command:'/do config set d802 dungeon-type season-2'><aqua>[d802]</aqua></click>
<click:run_command:'/do config set d803 dungeon-type season-2'><aqua>[d803]</aqua></click> <click:run_command:'/do config set d804 dungeon-type season-2'><aqua>[d804]</aqua></click>
        """.trimIndent(),

        """
      <gold>Utilities</gold>

<click:run_command:'/do status'><gold>[Toggle Status Board]</gold></click>
<click:run_command:'/do debug'><gold>[Toggle Debug]</gold></click>
<click:run_command:'/do config show'><gold>[Show My Config]</gold></click>
        """.trimIndent(),
    )

    val components = pages.map { MiniMessage.miniMessage().deserialize(it) }
    val book = Book.book(Component.text("Admin Panel"), Component.text("Tracked Out"), components)
    player.openBook(book)
}
