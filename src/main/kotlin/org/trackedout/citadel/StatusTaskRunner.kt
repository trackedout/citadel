package org.trackedout.citadel

import com.mongodb.client.model.Filters
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.sign.Side
import org.bukkit.scheduler.BukkitRunnable
import org.trackedout.citadel.mongo.DungeonState
import org.trackedout.citadel.mongo.MongoDBManager
import org.trackedout.citadel.mongo.MongoDungeon
import org.trackedout.client.apis.StatusApi
import org.trackedout.fs.logger

// WARNING: This task is processed *asynchronously*, and thus most interactions with the main Minecraft thread
// must go through a sync task scheduler. The Scoreboard lib is thread-safe, so it doesn't require a sync task.
class StatusTaskRunner(
    private val plugin: Citadel,
    private val statusApi: StatusApi,
    private val sidebar: Sidebar,
) : BukkitRunnable() {
    override fun run() {
        plugin.debug("[Async task ${this.taskId}] Fetching network status from Dunga Dunga")

        val statusSections = statusApi.getStatus()
        val mm = MiniMessage.miniMessage();

        if (sidebar.closed()) {
            return
        }
        sidebar.clearLines()
        sidebar.title(text("Network Status"))

        var lines = 0
        for (statusSection in statusSections) {
            if (lines > 0) {
                sidebar.line(lines++, Component.empty())
            }
            sidebar.line(lines++, mm.deserialize(statusSection.header!!).asComponent())

            statusSection.lines?.forEach {
                val parsed = mm.deserialize(it.key!!)

                val textComponent: TextComponent = text()
                    .append(parsed)
                    .append(text(": "))
                    .append(
                        text("${it.value!!}").colorIfAbsent(NamedTextColor.AQUA)
                    ).build()
                sidebar.line(lines++, textComponent)
            }
        }

        plugin.server.onlinePlayers.forEach {
            if (it.scoreboardTags.contains(debugTag)) {
                sidebar.addPlayer(it)
            } else {
                sidebar.removePlayer(it)
            }
        }

        updateDungeonStatusSigns()
    }

    private fun updateDungeonStatusSigns() {
        logger.debug("Starting dungeon status sign updater")
        val database = MongoDBManager.getDatabase("dunga-dunga");
        val instanceCollection = database.getCollection("instances", MongoDungeon::class.java)
        val instancesByState = instanceCollection.find(
            Filters.and(
                Filters.regex("name", "^d[0-9]{3}"),
            )
        ).groupBy { it.state }

        val signConfigs = mapOf(
            listOf(DungeonState.AVAILABLE) to Sign(-537, 117, 1975) { instances ->
                listOf(
                    "",
                    "${instances.size} Available",
                    "Inhaleable",
                )
            },

            listOf(
                DungeonState.RESERVED,
                DungeonState.AWAITING_PLAYER,
            ) to Sign(-537, 116, 1975) { instances ->
                listOf(
                    "",
                    "${instances.size} Pending",
                    "Player Sending",
                )
            },

            listOf(DungeonState.IN_USE) to Sign(-537, 115, 1975) { instances ->
                listOf(
                    "",
                    "${instances.size} In Use",
                    "Caboose",
                )
            },

            listOf(
                DungeonState.BUILDING,
                DungeonState.UNREACHABLE
            ) to Sign(-537, 114, 1975) { instances ->
                listOf(
                    "",
                    "${instances.size} Resetti",
                    "Spaghetti",
                )
            }
        )

        signConfigs.entries.forEach { (states, signConfig) ->
            val instances = states.flatMap { state ->
                val dbState = state.toString().lowercase().replace("_", "-")
                instancesByState[dbState] ?: emptyList()
            }

            val lines = signConfig.text(instances)
            plugin.runOnNextTick {
                updateSign(plugin, signConfig.x, signConfig.y, signConfig.z, lines)
            }
        }
    }

    data class Sign(
        val x: Int,
        val y: Int,
        val z: Int,
        // Takes in the list of instances, and returns a list of up to 4 strings to display on the sign
        val text: (instances: List<MongoDungeon>) -> List<String>,
    )
}

fun updateSign(plugin: Citadel, x: Int, y: Int, z: Int, lines: List<String>) {
    logger.debug("Updating sign at {}, {}, {} with lines: {}", x, y, z, lines)

    plugin.server.worlds.find { it.name == "world" }?.let { world ->
        val signBlock: Block = world.getBlockAt(x, y, z)

        if (signBlock.type == Material.WARPED_WALL_SIGN) {
            val sign = signBlock.state as org.bukkit.block.Sign
            val signSide = sign.getSide(Side.FRONT)

            intArrayOf(0, 1, 2, 3).forEach { i ->
                signSide.line(i, text(lines.getOrNull(i) ?: "").color(NamedTextColor.WHITE))
            }

            sign.update()
        }
    }
}
