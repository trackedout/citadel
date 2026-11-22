package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Subcommand
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

@CommandAlias("decked-out|do")
@CommandPermission("decked-out.league")
class LeagueCommand(
    private val plugin: Citadel,
    private val leagueCache: LeagueCache,
    private val leaguesApi: LeaguesApi,
    private val eventsApi: EventsApi,
    private val worldEditor: WorldEditor,
) : BaseCommand() {

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
    @Description("Alias for /do world edit, using the world the league plays")
    fun editLeague(player: Player, leagueSlug: String) {
        plugin.async(player) {
            val world = resolveWorldSlug(player, leagueSlug) ?: return@async
            player.sendGreyMessage("League '$leagueSlug' plays world '$world', editing the world")
            worldEditor.edit(player, world, sandboxLeagueSlug = leagueSlug)
        }
    }

    @Subcommand("league stop")
    @CommandCompletion("@leagueSlugs")
    @CommandPermission("decked-out.league.stop")
    @Description("Alias for /do world stop, using the world the league plays")
    fun stopLeague(player: Player, leagueSlug: String) {
        plugin.async(player) {
            val world = resolveWorldSlug(player, leagueSlug) ?: return@async
            worldEditor.stop(player, world, sandboxLeagueSlug = leagueSlug)
        }
    }

    private fun resolveWorldSlug(player: Player, leagueSlug: String): String? {
        val league = resolveLeague(leagueSlug)
        if (league == null) {
            player.sendRedMessage("Unknown league: $leagueSlug")
            return null
        }
        val world = league.world
        if (world == null) {
            player.sendRedMessage("League '$leagueSlug' has no world")
        }
        return world
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
    // for a league created since the cache last refreshed.
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
}
