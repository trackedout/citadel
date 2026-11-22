package org.trackedout.citadel.commands

import com.mongodb.client.model.Filters
import net.kyori.adventure.inventory.Book
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.config.isQueueable
import org.trackedout.citadel.config.isWorldBuilder
import org.trackedout.citadel.mongo.MongoDBManager
import org.trackedout.citadel.mongo.MongoDungeon
import org.trackedout.client.apis.ConfigApi
import org.trackedout.client.infrastructure.ClientException

fun showAdminBook(plugin: Citadel, player: Player, configApi: ConfigApi) {
    plugin.async(player) {
        val worlds = plugin.worldsApi.worldsGet().results.orEmpty()
        val knownWorlds = worlds
            .map { AdminWorld(it.slug, it.buildServer?.value) }
            .sortedBy { it.slug }

        // Worlds of leagues the viewer can queue, so builders can warm a dungeon for a draft they test
        val buildersByWorldSlug = worlds.associate { it.slug to it.builders }
        val queueableWorldSlugs = plugin.leagueCache.getLeagues()
            .filter { it.isQueueable(isWorldBuilder(buildersByWorldSlug[it.world], player.name)) }
            .mapNotNull { it.world }
            .toSet()

        val database = MongoDBManager.getDatabase("dunga-dunga")
        val dungeons = database.getCollection("instances", MongoDungeon::class.java)
            .find(Filters.regex("name", "^d8[0-9]{2}"))
            .sortedBy { it.name }
            .map { AdminDungeon(it.name, dungeonType(configApi, it.name)) }

        val pages = buildAdminBookPages(
            dungeons = dungeons,
            assignableWorlds = knownWorlds.filter { it.slug in queueableWorldSlugs },
            knownWorlds = knownWorlds,
            toggles = AdminToggles(
                details = player.scoreboardTags.contains("details"),
                networkStatus = player.scoreboardTags.contains("debug"),
                clickDebug = plugin.config.getBoolean("debug"),
            ),
        )

        val components = pages.map { MiniMessage.miniMessage().deserialize(it) }
        val book = Book.book(Component.text("Dungeon Ops"), Component.text("Tracked Out"), components)
        player.openBook(book)
    }
}

private fun dungeonType(configApi: ConfigApi, dungeon: String): String? =
    try {
        configApi.configsGet(dungeon, "dungeon-type").value
    } catch (e: ClientException) {
        null
    }
