package org.trackedout.citadel.commands

import com.mongodb.client.model.Filters
import net.kyori.adventure.inventory.Book
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.mongo.MongoDBManager
import org.trackedout.citadel.mongo.MongoDungeon
import org.trackedout.client.apis.ConfigApi
import org.trackedout.client.infrastructure.ClientException

fun showAdminBook(plugin: Citadel, player: Player, configApi: ConfigApi) {
    plugin.async(player) {
        val database = MongoDBManager.getDatabase("dunga-dunga")
        val dungeons = database.getCollection("instances", MongoDungeon::class.java)
            .find(Filters.regex("name", "^d8[0-9]{2}"))
            .sortedBy { it.name }
            .map { it.name }

        val dungeonTypes = dungeons.associateWith { d ->
            try {
                configApi.configsGet(d, "dungeon-type").value ?: "default"
            } catch (e: ClientException) { "default" }
        }

        fun dungeonLine(d: String): String {
            val type = dungeonTypes[d] ?: "default"
            val s1Btn = if (type == "default") "<gold>[<u>S1</u>]</gold>" else "<click:run_command:'/do config set $d dungeon-type default'><gold>[S1]</gold></click>"
            val s2Btn = if (type == "season-2") "<aqua>[<u>S2</u>]</aqua>" else "<click:run_command:'/do config set $d dungeon-type season-2'><aqua>[S2]</aqua></click>"
            val shutdownBtn = "<click:run_command:'/do shutdown-dungeon $d'><red>[X]</red></click>"
            return "  $d $s1Btn $s2Btn $shutdownBtn"
        }

        fun toggleLabel(label: String, on: Boolean) = if (on) "<green>[$label: ON]</green>" else "<red>[$label: OFF]</red>"

        val pages = listOf(
            """
  <italic><red>Dungeon Management</red></italic>

${dungeons.joinToString("\n") { dungeonLine(it) }}

 <click:run_command:'/do shutdown-all-empty-dungeons'><red>[Shutdown All Empty]</red></click>
            """.trimIndent(),

            """
      <italic><gold>Backups</gold></italic>

<click:run_command:'/k8s create-snapshot builders'><aqua>[Snapshot Builders]</aqua></click>

<click:run_command:'/k8s create-snapshot builders2'><aqua>[Snapshot Builders2]</aqua></click>

<click:run_command:'/k8s backup-database'><aqua>[Backup Database]</aqua></click>
            """.trimIndent(),

            """
    <italic><gold>Debug Configs</gold></italic>

<click:run_command:'/do details'>${toggleLabel("Dungeon Details", player.scoreboardTags.contains("details"))}</click>
<click:run_command:'/do status'>${toggleLabel("Network Status", player.scoreboardTags.contains("debug"))}</click>
<click:run_command:'/do debug'>${toggleLabel("Click Debug", plugin.config.getBoolean("debug"))}</click>

<click:run_command:'/do config show'><gold>[List Configs]</gold></click>
            """.trimIndent(),
        )

        val components = pages.map { MiniMessage.miniMessage().deserialize(it) }
        val book = Book.book(Component.text("Dungeon Ops"), Component.text("Tracked Out"), components)
        player.openBook(book)
    }
}
