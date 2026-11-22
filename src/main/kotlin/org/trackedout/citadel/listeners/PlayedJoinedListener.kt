package org.trackedout.citadel.listeners

import com.destroystokyo.paper.event.server.ServerTickStartEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.InventoryManager
import org.trackedout.citadel.LeaderboardState
import org.trackedout.citadel.async
import org.trackedout.citadel.getBool
import org.trackedout.citadel.config.activePhaseSchedule
import org.trackedout.citadel.config.leaderboardScope
import org.trackedout.citadel.getFutureRelativeTime
import org.trackedout.citadel.regions
import org.trackedout.citadel.runLaterOnATick
import org.trackedout.citadel.sendMiniMessage
import org.trackedout.client.apis.ConfigApi
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.models.Config
import org.trackedout.client.models.Event
import java.time.Duration
import java.util.UUID

class PlayedJoinedListener(
    private val plugin: Citadel,
    private val eventsApi: EventsApi,
    private val configApi: ConfigApi,
    private val inventoryManager: InventoryManager,
) : Listener {
    // The pack for newer clients comes from the world's server spec, which lobby-entrypoint.sh exports.
    // There is no fallback: an unpinned pack could be any version of the pack, or none of it
    private val modernResourcePack: ModernResourcePack? by lazy {
        val url = System.getenv("RESOURCE_PACK_MODERN").orEmpty()
        val sha1 = System.getenv("RESOURCE_PACK_MODERN_SHA1").orEmpty()
        if (url.isEmpty() || sha1.isEmpty()) {
            plugin.logger.warning("RESOURCE_PACK_MODERN or RESOURCE_PACK_MODERN_SHA1 is not set (the world has no pack, or lobby-entrypoint.sh did not run), not sending a modern resource pack")
            null
        } else {
            ModernResourcePack(url, sha1)
        }
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
            modernResourcePack?.let { pack ->
                plugin.logger.info("Player client version is using 1.21.4 or higher, sending the newer resource pack")
                plugin.async(player) {
                    event.getPlayer().setResourcePack(pack.url, pack.sha1)
                }
            }
        }

        plugin.async(player) {
            val phases = plugin.competitivePhases.current() ?: return@async
            val activePhase = phases.activePhaseSchedule()
            if (activePhase == null) {
                phases.leaderboardPhase?.let { player.sendMiniMessage("<aqua>Competitive Phase <gold>#$it</gold> has ended</aqua>") }
                return@async
            }

            val formattedPhase = "Competitive Phase <gold>#${activePhase.phase}</gold>"
            val phaseEndRelativeDate = activePhase.end?.let { getFutureRelativeTime(it) }
            if (phaseEndRelativeDate == null) {
                player.sendMiniMessage("<aqua>$formattedPhase has ended</aqua>")
                return@async
            }

            player.sendMiniMessage("<aqua>$formattedPhase ends in <gold>${phaseEndRelativeDate}</gold></aqua>")
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

    private val leaderboardAnimationPending = mutableSetOf<UUID>()

    @EventHandler(ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!event.hasChangedBlock()) return
        val player = event.player
        if (player.uniqueId in leaderboardAnimationPending) return
        if (LeaderboardState.animating || LeaderboardState.running) return

        val region = player.world.regions()?.find { it.id == "view-leaderboard" } ?: return
        if (!region.contains(event.to.blockX, event.to.blockY, event.to.blockZ)) return
        if (region.contains(event.from.blockX, event.from.blockY, event.from.blockZ)) return

        // Player just entered the view-leaderboard region
        leaderboardAnimationPending.add(player.uniqueId)
        plugin.async(player) {
            try {
                val scope = plugin.competitivePhases.current()?.leaderboardScope() ?: return@async
                if (!plugin.serverSettings.showLeaderboard()) return@async
                if (configApi.getBool(player.name, scope.seenKey, default = false)) return@async

                configApi.configsAddConfigPost(Config(entity = player.name, key = scope.seenKey, value = "true"))
                if (!LeaderboardState.animating && !LeaderboardState.running) {
                    plugin.leaderboardTaskRunner.runWithAnimation(setOf(player.uniqueId))
                }
            } finally {
                leaderboardAnimationPending.remove(player.uniqueId)
            }
        }
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

private data class ModernResourcePack(val url: String, val sha1: String)
