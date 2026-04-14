package org.trackedout.citadel.listeners

import com.destroystokyo.paper.event.server.ServerTickStartEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.InventoryManager
import org.trackedout.citadel.async
import org.trackedout.citadel.getEnvOrDefault
import org.trackedout.citadel.getInt
import org.trackedout.citadel.getRelativeFutureDate
import org.trackedout.citadel.runLaterOnATick
import org.trackedout.citadel.sendMiniMessage
import org.trackedout.client.apis.ConfigApi
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.models.Event
import java.time.Duration

class PlayedJoinedListener(
    private val plugin: Citadel,
    private val eventsApi: EventsApi,
    private val configApi: ConfigApi,
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
            ""
        )
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        // Log but don't broadcast the player's join message
        plugin.logger.info(event.joinMessage)
        event.joinMessage = ""

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
            plugin.async(player) {
                event.getPlayer().setResourcePack(
                    modernResourcePackUrl,
                    modernResourcePackChecksum
                )
            }
        }

        plugin.async(player) {
            configApi.getInt("comp-season", "current-phase")?.let { currentPhase ->
                val formattedPhase = "Competitive Phase <gold>#${currentPhase}</gold>"
                configApi.getRelativeFutureDate("phase-${currentPhase}", "end-time")?.let { phaseEndRelativeDate ->
                    val message = "<aqua>$formattedPhase ends in <gold>${phaseEndRelativeDate}</gold></aqua>"
                    player.sendMiniMessage(message)

                    plugin.runLaterOnATick(20 * 5) {
                        player.showTitle(
                            Title.title(
                                MiniMessage.miniMessage().deserialize("<aqua>${formattedPhase}</aqua>"),
                                MiniMessage.miniMessage().deserialize("<aqua>Ends in $phaseEndRelativeDate</aqua>"),
                                Title.Times.times(
                                    Duration.ofSeconds(1),
                                    Duration.ofSeconds(3),
                                    Duration.ofSeconds(2),
                                )
                            )
                        )
                    }
                } ?: run {
                    player.sendMiniMessage("<aqua>$formattedPhase has ended</aqua>")
                }
            }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // Log but don't broadcast the player's quit message
        plugin.logger.info(event.quitMessage)
        event.quitMessage = ""
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
