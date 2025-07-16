package org.trackedout.citadel.listeners

import com.destroystokyo.paper.event.server.ServerTickStartEvent
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.InventoryManager
import org.trackedout.citadel.async
import org.trackedout.citadel.getEnvOrDefault
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.ScoreApi
import org.trackedout.client.models.Event

class PlayedJoinedListener(
    private val plugin: Citadel,
    private val eventsApi: EventsApi,
    private val scoreApi: ScoreApi,
    private val inventoryManager: InventoryManager,
) : Listener {
    val modernResourcePackUrl by lazy {
        getEnvOrDefault(
            "RESOURCE_PACK_MODERN",
            "https://mc.trackedout.org/brilliance-pack-1.21.4.zip"
        )
    }
    val modernResourcePackChecksum by lazy {
        getEnvOrDefault(
            "RESOURCE_PACK_MODERN_SHA1",
            "ba2cb5b5646e9e7f3954173576cbacf4ce54ddae"
        )
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (insideDungeonEntrance(player)) {
            plugin.logger.info("${player.name} is within dungeon entrance at ${player.location}, teleporting them out")
            player.teleport(Location(player.world, -512.0, 114.0, 1980.0, 90f, 0f))
        }

        inventoryManager.updateInventoryBasedOnScore(player)

        val protocolStr = event.player.playerProfile.properties
            .firstOrNull { it.name == "clientProtocol" }
            ?.value

        val proxyProtocol = protocolStr?.toInt()
        plugin.logger.info("${player.name} (proxyProtocol: ${proxyProtocol}) joined at location: ${player.location}")

        // https://minecraft.wiki/w/Minecraft_Wiki:Projects/wiki.vg_merge/Protocol_version_numbers
        // 769 = 1.21.4
        if (proxyProtocol != null && proxyProtocol > 769) {
            plugin.logger.info("Player client version is using 1.21.4 or higher, sending the newer datapack")
            // Override resource pack to use newer version
            plugin.async(player) {
                event.getPlayer().setResourcePack(
                    modernResourcePackUrl,
                    modernResourcePackChecksum
                );
            }
        }
    }

    private fun insideDungeonEntrance(player: Player): Boolean {
        return -553 <= player.x && player.x <= -542
            && 1977 <= player.z && player.z <= 1983
            && 112 <= player.y && player.y <= 119
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerSeen(event: ServerTickStartEvent) {
        if (event.tickNumber % 60 == 0) { // Every 3 seconds
            plugin.server.onlinePlayers.forEach { player ->
                plugin.async(player) {
                    eventsApi.eventsPost(
                        Event(
                            player = player.name,
                            server = plugin.serverName,
                            name = "player-seen",
                            x = player.x,
                            y = player.y,
                            z = player.z,
                            count = 1,
                        )
                    )
                }
            }
        }
    }
}
