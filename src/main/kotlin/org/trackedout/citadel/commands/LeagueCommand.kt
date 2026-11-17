package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Subcommand
import com.google.common.io.ByteStreams
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.config.LeagueCache
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendGreyMessage
import org.trackedout.citadel.sendMiniMessage
import org.trackedout.citadel.userMessage
import org.trackedout.citadel.sendRedMessage
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.LeaguesApi
import org.trackedout.client.infrastructure.ClientException
import org.trackedout.client.models.Event
import org.trackedout.client.models.League
import org.trackedout.client.models.LeagueSandboxInstance

@CommandAlias("decked-out|do")
@CommandPermission("decked-out.league")
class LeagueCommand(
    private val plugin: Citadel,
    private val leagueCache: LeagueCache,
    private val leaguesApi: LeaguesApi,
    private val eventsApi: EventsApi,
) : BaseCommand() {

    // Track active polling loops per player to cancel stale ones
    private val activePolling = ConcurrentHashMap<String, Long>()

    @Subcommand("league list")
    @Description("List all active leagues")
    fun listLeagues(player: Player) {
        plugin.async(player) {
            val leagues = leagueCache.getLeagues().sortedBy { it.slug ?: "" }
            if (leagues.isEmpty()) {
                player.sendGreyMessage("No active leagues")
                return@async
            }

            player.sendGreenMessage("Active leagues (${leagues.size}):")
            leagues.forEach { league ->
                val slug = league.slug ?: "?"
                val name = league.displayName ?: slug
                val state = league.state ?: "?"
                val hex = "#%06x".format((league.display?.colour ?: 0x555555) and 0xffffff)
                player.sendMiniMessage("<gray>- <$hex>$slug</$hex> ($name) — $state</gray>")
            }
        }
    }

    @Subcommand("league edit")
    @CommandCompletion("@leagueSlugs")
    @Description("Connect to the league's sandbox build server")
    fun editLeague(player: Player, leagueSlug: String) {
        plugin.async(player) {
            // Try cache first, then direct API lookup (cache only has active leagues)
            val league = leagueCache.getLeagueBySlug(leagueSlug)
                ?: try { leaguesApi.leaguesSlugGet(leagueSlug) } catch (_: ClientException) { null }

            if (league == null) {
                player.sendRedMessage("Unknown league: $leagueSlug")
                return@async
            }

            val slug = league.slug ?: return@async

            // Check permission via allowlist
            try {
                val allowlist = leaguesApi.leagueBuilderAllowlist(slug)
                val allowedNames = allowlist.map { it.name }
                if (!allowedNames.contains(player.name)) {
                    player.sendRedMessage("You don't have build access to '${league.displayName ?: slug}'")
                    return@async
                }
            } catch (e: ClientException) {
                player.sendRedMessage("Failed to check permissions: ${e.message}")
                return@async
            }

            // Check if sandbox is already running
            val instance = try {
                leaguesApi.leagueSandboxGet(slug)
            } catch (e: ClientException) {
                if (e.statusCode == 404) null
                else {
                    player.sendRedMessage("Failed to check sandbox status: ${e.message}")
                    return@async
                }
            }

            if (instance != null) {
                when (instance.state) {
                    LeagueSandboxInstance.State.RUNNING -> {
                        player.sendGreyMessage("Connecting to sandbox...")
                        transferPlayer(player, instance.slot!!)
                        return@async
                    }
                    LeagueSandboxInstance.State.LAUNCHING -> {
                        player.sendGreyMessage("Sandbox is starting up, waiting...")
                        waitForRunningAndTransfer(player, slug, instance.slot!!)
                        return@async
                    }
                    else -> {} // STOPPING/STOPPED — launch a new one
                }
            }

            // Launch new sandbox
            player.sendGreyMessage("Starting sandbox for '${league.displayName ?: slug}'...")

            try {
                val launched = leaguesApi.leagueSandboxLaunch(slug)
                val slot = launched.slot
                    ?: run {
                        player.sendRedMessage("Failed to allocate sandbox slot")
                        return@async
                    }

                player.sendGreyMessage("Sandbox allocated on slot $slot for league '$slug'. Starting Fargate task...")
                waitForRunningAndTransfer(player, slug, slot)
            } catch (e: ClientException) {
                // Leagues built on a persistent build server (builders/builders2)
                // reject on-demand sandboxes with 409. That's expected — point the
                // editor at the persistent server instead of surfacing a raw error.
                if (e.statusCode == 409) {
                    player.sendGreyMessage("'${league.displayName ?: slug}' is built on the persistent build server")
                    player.sendGreyMessage("Use '/server builders2' to edit it")
                } else {
                    player.sendRedMessage("Failed to launch sandbox: ${e.message}")
                }
            }
        }
    }

    @Subcommand("league stop")
    @CommandCompletion("@leagueSlugs")
    @CommandPermission("decked-out.league.stop")
    @Description("Stop a league's sandbox build server")
    fun stopLeague(player: Player, leagueSlug: String) {
        plugin.async(player) {
            val league = leagueCache.getLeagueBySlug(leagueSlug)

            if (league == null) {
                player.sendRedMessage("Unknown league: $leagueSlug")
                return@async
            }

            val slug = league.slug ?: return@async

            try {
                leaguesApi.leagueSandboxStop(slug)
                player.sendGreenMessage("Sandbox for '$slug' is shutting down")
            } catch (e: ClientException) {
                if (e.statusCode == 404) {
                    player.sendGreyMessage("No running sandbox for '$slug'")
                } else {
                    player.sendRedMessage("Failed to stop sandbox: ${e.message}")
                }
            }
        }
    }

    @Subcommand("league play")
    @CommandCompletion("@leagueSlugs")
    @Description("Queue into a league's dungeon to test it (owner/editor/admin)")
    fun playLeague(player: Player, leagueSlug: String) {
        plugin.async(player) {
            val league = resolveLeague(leagueSlug)
            if (league == null) {
                player.sendRedMessage("Unknown league: $leagueSlug")
                return@async
            }
            val slug = league.slug ?: return@async

            if (!canManageLeague(player, league)) {
                player.sendRedMessage("You don't have permission to play-test '${league.displayName ?: slug}'")
                return@async
            }

            if ((league.display?.runTypeId ?: 0) <= 0) {
                player.sendRedMessage("League '$slug' isn't published yet. Set its state to active in the leagues repo and sync it")
                return@async
            }

            // One deck per league: the deck identifier is the league slug. The
            // backend derives dungeon-type from the league; we only send the slug.
            val deckId = slug

            try {
                eventsApi.eventsPost(
                    Event(
                        name = "joined-queue",
                        server = plugin.serverName,
                        player = player.name,
                        count = 1,
                        x = player.x,
                        y = player.y,
                        z = player.z,
                        metadata = mapOf(
                            "deck-id" to deckId,
                            "slug" to slug,
                        ),
                    )
                )
                player.sendGreenMessage("Queuing you into '${league.displayName ?: slug}'...")
            } catch (e: ClientException) {
                player.sendRedMessage("Failed to queue: ${e.userMessage()}")
            }
        }
    }

    // Look up a league by slug or short id, falling back to a direct API lookup
    // because the cache only holds active (published) leagues while drafts are
    // still being edited.
    private fun resolveLeague(leagueSlug: String): League? =
        leagueCache.getLeagueBySlug(leagueSlug)
            ?: try { leaguesApi.leaguesSlugGet(leagueSlug) } catch (_: ClientException) { null }

    // A player may manage a league if they are a network admin (LuckPerms) or the
    // league's owner/editor. Editors are the league's builders (owner +
    // builders.players), which the backend exposes via the builder-allowlist —
    // the same source the sandbox whitelist uses, so the two never diverge.
    private fun canManageLeague(player: Player, league: League): Boolean {
        if (player.hasPermission("decked-out.league.admin")) return true
        val slug = league.slug ?: return false
        return try {
            leaguesApi.leagueBuilderAllowlist(slug).any { it.name == player.name }
        } catch (_: ClientException) {
            false
        }
    }

    private fun waitForRunningAndTransfer(player: Player, slug: String, slot: String) {
        val maxAttempts = 60
        val pollIntervalMs = 5000L
        val pollId = System.currentTimeMillis()
        val playerKey = "${player.uniqueId}:$slug"
        activePolling[playerKey] = pollId

        for (attempt in 1..maxAttempts) {
            Thread.sleep(pollIntervalMs)

            if (!player.isOnline) return
            if (activePolling[playerKey] != pollId) return // superseded by a newer poll

            val instance = try {
                leaguesApi.leagueSandboxGet(slug)
            } catch (e: ClientException) {
                continue
            }

            when (instance.state) {
                LeagueSandboxInstance.State.RUNNING -> {
                    player.sendGreenMessage("Sandbox ready!")
                    transferPlayer(player, slot)
                    return
                }
                LeagueSandboxInstance.State.STOPPED, LeagueSandboxInstance.State.STOPPING -> {
                    player.sendRedMessage("Sandbox failed to start")
                    return
                }
                else -> {
                    if (attempt % 6 == 0) {
                        player.sendGreyMessage("Still starting... (${attempt * 5}s)")
                    }
                }
            }
        }

        player.sendRedMessage("Sandbox failed to start within 5 minutes")
    }

    private fun transferPlayer(player: Player, serverName: String) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            val out = ByteStreams.newDataOutput()
            out.writeUTF("Connect")
            out.writeUTF(serverName)
            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray())
        })
    }
}
