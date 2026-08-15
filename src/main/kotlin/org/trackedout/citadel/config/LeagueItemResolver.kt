package org.trackedout.citadel.config

import org.trackedout.client.models.League

/**
 * Resolves scoreboard keys from a league's items config.
 * Replaces the hardcoded ScoreboardDescriber pattern.
 */
object LeagueItemResolver {

    /**
     * Get the source (earned) scoreboard name for an item in a league.
     * For single-scoreboard items, returns the scoreboard.
     * For earned/spent items, returns the earned scoreboard.
     */
    fun sourceScoreboardName(league: League, itemKey: String): String? {
        val item = league.items?.get(itemKey) as? Map<*, *> ?: return null
        return (item["earned"] as? String) ?: (item["scoreboard"] as? String)
    }

    /**
     * Get the inversion (spent) scoreboard name for an item in a league.
     * For single-scoreboard items, returns the same scoreboard (direct decrement).
     * For earned/spent items, returns the spent scoreboard.
     */
    fun sourceInversionScoreboardName(league: League, itemKey: String): String? {
        val item = league.items?.get(itemKey) as? Map<*, *> ?: return null
        return (item["spent"] as? String) ?: (item["scoreboard"] as? String)
    }

    /**
     * Get the target scoreboard name (same as source for most items).
     */
    fun targetScoreboardName(league: League, itemKey: String): String? {
        return sourceScoreboardName(league, itemKey)
    }

    /**
     * Get the starting amount for an item (only applicable for single-scoreboard items).
     */
    fun startingAmount(league: League, itemKey: String): Int? {
        val item = league.items?.get(itemKey) as? Map<*, *> ?: return null
        return (item["startingAmount"] as? Number)?.toInt()
    }
}
