package org.trackedout.citadel.commands

import com.google.common.io.ByteStreams
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.buildServer
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendGreyMessage
import org.trackedout.citadel.sendRedMessage
import org.trackedout.citadel.userMessage
import org.trackedout.client.apis.LeaguesApi
import org.trackedout.client.apis.WorldsApi
import org.trackedout.client.infrastructure.ClientException
import org.trackedout.client.models.LeagueSandboxInstance
import org.trackedout.client.models.World

/**
 * Sends builders to the place a world is edited: its persistent build server if it
 * has one, otherwise an on-demand sandbox.
 *
 * Sandboxes are still keyed by league in the backend, so sandbox calls go through a
 * league that plays the world.
 */
class WorldEditor(
    private val plugin: Citadel,
    private val worldsApi: WorldsApi,
    private val leaguesApi: LeaguesApi,
) {
    // Track active polling loops per player to cancel stale ones
    private val activePolling = ConcurrentHashMap<String, Long>()

    /** Blocking, call from an async task */
    fun edit(player: Player, worldSlug: String, sandboxLeagueSlug: String? = null) {
        val world = findWorld(player, worldSlug) ?: return
        if (!hasBuildAccess(player, world)) return

        val buildServer = world.buildServer
        if (buildServer != null) {
            sendToBuildServer(player, world.slug, buildServer.value)
            return
        }

        val leagueSlug = sandboxLeagueSlug ?: findSandboxLeagueSlug(player, world.slug) ?: return
        editInSandbox(player, world.slug, leagueSlug)
    }

    /** Blocking, call from an async task */
    fun stop(player: Player, worldSlug: String, sandboxLeagueSlug: String? = null) {
        val world = findWorld(player, worldSlug) ?: return
        if (world.buildServer != null) {
            player.sendGreyMessage("'${world.slug}' is built on ${world.buildServer?.value}, which has no sandbox to stop")
            return
        }

        val leagueSlug = sandboxLeagueSlug ?: findSandboxLeagueSlug(player, world.slug) ?: return
        try {
            leaguesApi.leagueSandboxStop(leagueSlug)
            player.sendGreenMessage("Sandbox for '${world.slug}' is shutting down")
        } catch (e: ClientException) {
            if (e.statusCode == 404) {
                player.sendGreyMessage("No running sandbox for '${world.slug}'")
            } else {
                player.sendRedMessage("Failed to stop sandbox: ${e.message}")
            }
        }
    }

    private fun findWorld(player: Player, worldSlug: String): World? =
        try {
            worldsApi.worldsSlugGet(worldSlug)
        } catch (e: ClientException) {
            if (e.statusCode == 404) {
                player.sendRedMessage("Unknown world: $worldSlug")
            } else {
                player.sendRedMessage("Failed to look up world: ${e.message}")
            }
            null
        }

    private fun hasBuildAccess(player: Player, world: World): Boolean =
        try {
            val allowed = worldsApi.worldBuilderAllowlist(world.slug).any { it.name == player.name }
            if (!allowed) {
                player.sendRedMessage("You don't have build access to '${world.slug}'")
            }
            allowed
        } catch (e: ClientException) {
            player.sendRedMessage("Failed to check permissions: ${e.message}")
            false
        }

    // The backend allows one sandbox per world, so prefer the league that already has
    // one running over the first league that plays the world
    private fun findSandboxLeagueSlug(player: Player, worldSlug: String): String? {
        val leagueSlugs = try {
            leaguesApi.leaguesGet().results.orEmpty().filter { it.world == worldSlug }.mapNotNull { it.slug }
        } catch (e: ClientException) {
            player.sendRedMessage("Failed to look up leagues for '$worldSlug': ${e.message}")
            return null
        }
        if (leagueSlugs.isEmpty()) {
            player.sendRedMessage("No league plays '$worldSlug', so there is no sandbox to start for it")
            return null
        }
        return leagueSlugs.firstOrNull { hasLiveSandbox(it) } ?: leagueSlugs.first()
    }

    private fun hasLiveSandbox(leagueSlug: String): Boolean =
        try {
            leaguesApi.leagueSandboxGet(leagueSlug).state in LIVE_STATES
        } catch (e: ClientException) {
            false
        }

    private fun sendToBuildServer(player: Player, worldSlug: String, buildServer: String) {
        player.sendGreyMessage("Sending you to $buildServer to edit $worldSlug")
        transferPlayer(player, buildServer)
    }

    private fun editInSandbox(player: Player, worldSlug: String, leagueSlug: String) {
        val existing = try {
            leaguesApi.leagueSandboxGet(leagueSlug)
        } catch (e: ClientException) {
            if (e.statusCode != 404) {
                player.sendRedMessage("Failed to check sandbox status: ${e.message}")
                return
            }
            null
        }

        when (existing?.state) {
            LeagueSandboxInstance.State.RUNNING -> {
                player.sendGreyMessage("Connecting to the sandbox for $worldSlug")
                transferPlayer(player, existing.slot!!)
                return
            }
            LeagueSandboxInstance.State.LAUNCHING -> {
                player.sendGreyMessage("Sandbox for $worldSlug is starting up, waiting")
                waitForRunningAndTransfer(player, leagueSlug, existing.slot!!)
                return
            }
            else -> {} // none, STOPPING or STOPPED: launch a new one
        }

        launchSandbox(player, worldSlug, leagueSlug)
    }

    private fun launchSandbox(player: Player, worldSlug: String, leagueSlug: String) {
        val slot = try {
            leaguesApi.leagueSandboxLaunch(leagueSlug).slot
        } catch (e: ClientException) {
            handleLaunchFailure(player, worldSlug, e)
            return
        }

        if (slot == null) {
            player.sendRedMessage("Failed to allocate a sandbox slot")
            return
        }

        player.sendGreyMessage("Starting sandbox for $worldSlug on slot $slot")
        waitForRunningAndTransfer(player, leagueSlug, slot)
    }

    // A world with a persistent build server rejects on-demand sandboxes with 409
    // and names that server in the body
    private fun handleLaunchFailure(player: Player, worldSlug: String, e: ClientException) {
        val buildServer = e.buildServer()
        if (e.statusCode == 409 && buildServer != null) {
            sendToBuildServer(player, worldSlug, buildServer)
        } else {
            player.sendRedMessage("Failed to launch sandbox: ${e.userMessage()}")
        }
    }

    private fun waitForRunningAndTransfer(player: Player, leagueSlug: String, slot: String) {
        val maxAttempts = 60
        val pollIntervalMs = 5000L
        val pollId = System.currentTimeMillis()
        val playerKey = "${player.uniqueId}:$leagueSlug"
        activePolling[playerKey] = pollId

        for (attempt in 1..maxAttempts) {
            Thread.sleep(pollIntervalMs)

            if (!player.isOnline) return
            if (activePolling[playerKey] != pollId) return // superseded by a newer poll

            val instance = try {
                leaguesApi.leagueSandboxGet(leagueSlug)
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

    private companion object {
        val LIVE_STATES = setOf(LeagueSandboxInstance.State.RUNNING, LeagueSandboxInstance.State.LAUNCHING)
    }
}
