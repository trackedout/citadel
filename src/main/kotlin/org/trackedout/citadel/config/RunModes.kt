package org.trackedout.citadel.config

import org.trackedout.client.apis.ConfigApi
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.models.Config
import org.trackedout.client.models.League
import java.util.logging.Logger

// Per-player config key holding the league slug the player selected as their run mode
const val FILTER_MODE_SLUG_CONFIG = "filter-mode-slug"

// Run mode for players who have not picked one, or whose pick is no longer offered
const val DEFAULT_RUN_MODE_SLUG = PRACTICE_SLUG

// Which leagues a player can pick as their run mode, and which one they are on.
// Calls the backend, so use it off the main thread.
class RunModes(
    private val leagueCache: LeagueCache,
    private val configApi: ConfigApi,
    private val inventoryApi: InventoryApi,
    private val logger: Logger,
) {
    fun selectableLeagues(playerName: String): List<League> =
        leagueCache.getLeagues().filter { isSelectable(it, playerName) }

    fun isSelectable(league: League, playerName: String): Boolean =
        league.isSelectableRunMode(playerHasDeck = league.slug?.let { hasDeckIn(playerName, it) } ?: false)

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

        if (chosenSlug != null) {
            logger.info("Run mode '$chosenSlug' is not available to $playerName, using '$DEFAULT_RUN_MODE_SLUG'")
        }
        return leagueCache.getLeagueBySlug(DEFAULT_RUN_MODE_SLUG)?.let { runTypeFromLeague(it) }
    }

    // Store the player's run mode. Callers check isSelectable first and refresh the
    // player's inventory afterwards.
    fun choose(playerName: String, slug: String) {
        configApi.configsAddConfigPost(Config(entity = playerName, key = FILTER_MODE_SLUG_CONFIG, value = slug))
    }

    private fun hasDeckIn(playerName: String, slug: String): Boolean =
        inventoryApi.inventoryCardsGet(player = playerName, deckType = slug, limit = 1).results?.isNotEmpty() == true
}
