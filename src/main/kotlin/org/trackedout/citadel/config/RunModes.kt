package org.trackedout.citadel.config

import org.trackedout.client.apis.ConfigApi
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.apis.WorldsApi
import org.trackedout.client.models.Config
import org.trackedout.client.models.Event
import org.trackedout.client.models.League
import java.util.logging.Logger

// Per-player config key holding the league slug the player selected as their run mode
const val FILTER_MODE_SLUG_CONFIG = "filter-mode-slug"

private const val DRAFT_LEAGUE_SELECTED_EVENT = "draft-league-selected"

// Which leagues a player can pick as their run mode, and which one they are on.
// Calls the backend, so use it off the main thread.
class RunModes(
    private val leagueCache: LeagueCache,
    private val serverSettings: ServerSettings,
    private val configApi: ConfigApi,
    private val inventoryApi: InventoryApi,
    private val eventsApi: EventsApi,
    private val worldsApi: WorldsApi,
    private val serverName: String,
    private val logger: Logger,
) {
    fun selectableLeagues(playerName: String): List<League> =
        leagueCache.getLeagues().filter { isSelectable(it, playerName) }

    fun isSelectable(league: League, playerName: String): Boolean =
        league.isSelectableRunMode(
            playerHasDeck = league.slug?.let { hasDeckIn(playerName, it) } ?: false,
            isWorldBuilder = isBuilderOfDraftWorld(league, playerName),
        )

    // The player's chosen run mode, or the default when they have none or their
    // choice is no longer offered to them (e.g. a finished league they never played)
    fun current(playerName: String): RunType? {
        val chosenSlug = try {
            configApi.configsGet(playerName, FILTER_MODE_SLUG_CONFIG).value?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }

        val chosen = chosenSlug?.let { leagueCache.getLeagueBySlug(it) }
        if (chosen != null && isSelectable(chosen, playerName)) {
            return runTypeFromLeague(chosen)
        }

        val default = defaultLeague()
        if (chosenSlug != null) {
            logger.info("Run mode '$chosenSlug' is not available to $playerName, using '${default?.slug}'")
        }
        return default?.let { runTypeFromLeague(it) }
    }

    // The run mode of players who have not picked one, or whose pick is no longer
    // offered: the settings' defaultRunMode
    private fun defaultLeague(): League? {
        val slug = serverSettings.current()?.defaultRunMode
        val default = slug?.let { leagueCache.getLeagueBySlug(it) }
        if (default == null) {
            logger.warning("Settings name no known default run mode (defaultRunMode '$slug'), players without a run mode get none")
        }
        return default
    }

    // Store the player's run mode. Callers check isSelectable first and refresh the
    // player's inventory afterwards. A draft has no deck or shards for the player
    // yet, so the backend seeds them before the run mode is stored.
    fun choose(playerName: String, slug: String) {
        if (leagueCache.getLeagueBySlug(slug)?.isDraft() == true) {
            eventsApi.eventsPost(
                Event(
                    name = DRAFT_LEAGUE_SELECTED_EVENT,
                    server = serverName,
                    player = playerName,
                    count = 1,
                    x = 0.0,
                    y = 0.0,
                    z = 0.0,
                    metadata = mapOf("slug" to slug),
                )
            )
        }
        configApi.configsAddConfigPost(Config(entity = playerName, key = FILTER_MODE_SLUG_CONFIG, value = slug))
    }

    // Only drafts are gated on world builders, so only they cost a world lookup
    private fun isBuilderOfDraftWorld(league: League, playerName: String): Boolean {
        val worldSlug = league.world?.takeIf { league.isDraft() } ?: return false
        return isWorldBuilder(worldsApi.worldsSlugGet(worldSlug).builders, playerName)
    }

    private fun hasDeckIn(playerName: String, slug: String): Boolean =
        inventoryApi.inventoryCardsGet(player = playerName, deckType = slug, limit = 1).results?.isNotEmpty() == true
}
