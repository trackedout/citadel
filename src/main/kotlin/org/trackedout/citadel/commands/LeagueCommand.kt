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
import org.trackedout.citadel.sendRedMessage
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.LeaguesApi
import org.trackedout.client.infrastructure.ClientException
import org.trackedout.client.models.Event
import org.trackedout.client.models.League
import org.trackedout.client.models.LeagueMemberRequest
import org.trackedout.client.models.LeagueSandboxInstance
import org.trackedout.client.models.LeaguesCreateRequest
import org.trackedout.client.models.LeaguesCreateRequestBuilders

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

    @Subcommand("league create")
    @CommandCompletion("@nothing")
    @CommandPermission("decked-out.league.create")
    @Description("Create a new league with a world seeded from builders2")
    fun createLeague(player: Player, slug: String) {
        plugin.async(player) {
            if (!slug.matches(Regex("^[a-z0-9-]+$"))) {
                player.sendRedMessage("Invalid slug: must be lowercase alphanumeric with hyphens only")
                return@async
            }

            val existing = leagueCache.getLeagueBySlug(slug)
            if (existing != null) {
                player.sendRedMessage("League '$slug' already exists")
                return@async
            }

            player.sendGreyMessage("Creating league '$slug' and seeding world from builders2...")

            try {
                val request = LeaguesCreateRequest(
                    slug = slug,
                    displayName = slug.replace("-", " ").replaceFirstChar { it.uppercase() },
                    owner = player.name,
                    builders = LeaguesCreateRequestBuilders(
                        players = emptyList(),
                        idleTimeoutMinutes = 30,
                    ),
                )

                leaguesApi.leaguesCreate(request)
                leagueCache.refresh()

                player.sendGreenMessage("League '$slug' created! World seeded from builders2.")
                player.sendGreyMessage("Use '/do league edit $slug' to start building.")
            } catch (e: ClientException) {
                if (e.statusCode == 409) {
                    player.sendRedMessage("League '$slug' already exists")
                } else {
                    player.sendRedMessage("Failed to create league: ${e.message}")
                }
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
                ?: leagueCache.getLeagueByShortId(leagueSlug)
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
                player.sendRedMessage("Failed to launch sandbox: ${e.message}")
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
                ?: leagueCache.getLeagueByShortId(leagueSlug)

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

    @Subcommand("league publish")
    @CommandCompletion("@leagueSlugs")
    @Description("Publish a league so it becomes a playable mode")
    fun publishLeague(player: Player, leagueSlug: String) {
        plugin.async(player) {
            val league = resolveLeague(leagueSlug)
            if (league == null) {
                player.sendRedMessage("Unknown league: $leagueSlug")
                return@async
            }
            val slug = league.slug ?: return@async

            if (!canManageLeague(player, league)) {
                player.sendRedMessage("You don't have permission to publish '${league.displayName ?: slug}'")
                return@async
            }

            player.sendGreyMessage("Publishing league '$slug'...")
            try {
                val published = leaguesApi.leaguePublish(slug)
                leagueCache.refresh()
                player.sendGreenMessage("League '$slug' is now playable as '${published.display?.shortId ?: slug}'.")
                player.sendGreyMessage("Players can select it as a mode and use '/do league play $slug' to test it.")
            } catch (e: ClientException) {
                player.sendRedMessage("Failed to publish league: ${e.message}")
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

            val shortId = league.display?.shortId
            if (shortId.isNullOrEmpty() || (league.display?.runTypeId ?: 0) <= 0) {
                player.sendRedMessage("League '$slug' isn't published yet. Run '/do league publish $slug' first.")
                return@async
            }

            val dungeonType = league.dungeon?.type ?: slug
            // Default to deck #1 for the league's run-type (e.g. shortId "ab" -> "ab1").
            val deckId = "${shortId}1"

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
                            "run-type" to shortId,
                            "dungeon-type" to dungeonType,
                        ),
                    )
                )
                player.sendGreenMessage("Queuing you into '${league.displayName ?: slug}' (dungeon-type: $dungeonType)...")
            } catch (e: ClientException) {
                player.sendRedMessage("Failed to queue: ${e.message}")
            }
        }
    }

    @Subcommand("league editors add")
    @CommandCompletion("@leagueSlugs @nothing")
    @Description("Add an editor (builder) to a league")
    fun addEditor(player: Player, leagueSlug: String, editor: String) {
        mutateLeague(player, leagueSlug, "add editor '$editor' to") { slug ->
            leaguesApi.leagueAddEditor(slug, LeagueMemberRequest(player = editor))
        }
    }

    @Subcommand("league editors remove")
    @CommandCompletion("@leagueSlugs @nothing")
    @Description("Remove an editor (builder) from a league")
    fun removeEditor(player: Player, leagueSlug: String, editor: String) {
        mutateLeague(player, leagueSlug, "remove editor '$editor' from") { slug ->
            leaguesApi.leagueRemoveEditor(slug, LeagueMemberRequest(player = editor))
        }
    }

    @Subcommand("league members add")
    @CommandCompletion("@leagueSlugs @nothing")
    @Description("Add a member to a league")
    fun addMember(player: Player, leagueSlug: String, member: String) {
        mutateLeague(player, leagueSlug, "add member '$member' to") { slug ->
            leaguesApi.leagueAddMember(slug, LeagueMemberRequest(player = member))
        }
    }

    @Subcommand("league members remove")
    @CommandCompletion("@leagueSlugs @nothing")
    @Description("Remove a member from a league")
    fun removeMember(player: Player, leagueSlug: String, member: String) {
        mutateLeague(player, leagueSlug, "remove member '$member' from") { slug ->
            leaguesApi.leagueRemoveMember(slug, LeagueMemberRequest(player = member))
        }
    }

    // Look up a league by slug or short id, falling back to a direct API lookup
    // because the cache only holds active (published) leagues while drafts are
    // still being edited.
    private fun resolveLeague(leagueSlug: String): League? =
        leagueCache.getLeagueBySlug(leagueSlug)
            ?: leagueCache.getLeagueByShortId(leagueSlug)
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

    private fun mutateLeague(player: Player, leagueSlug: String, action: String, mutate: (String) -> Unit) {
        plugin.async(player) {
            val league = resolveLeague(leagueSlug)
            if (league == null) {
                player.sendRedMessage("Unknown league: $leagueSlug")
                return@async
            }
            val slug = league.slug ?: return@async

            if (!canManageLeague(player, league)) {
                player.sendRedMessage("You don't have permission to manage '${league.displayName ?: slug}'")
                return@async
            }

            try {
                mutate(slug)
                leagueCache.refresh()
                player.sendGreenMessage("Updated '$slug': $action league.")
            } catch (e: ClientException) {
                player.sendRedMessage("Failed to $action league: ${e.message}")
            }
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
