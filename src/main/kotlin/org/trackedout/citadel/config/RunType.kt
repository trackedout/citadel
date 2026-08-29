package org.trackedout.citadel.config

import org.trackedout.client.models.League

data class RunType(
    val longId: String,
    val displayName: String,
    val runTypeId: Int,
    val deckMaterial: String,
    val displayColour: Int,
) {
    // The deck type is the league slug (one deck per league).
    fun deckType(): String {
        return longId
    }
}

val unknownRunType = RunType("unknown", "Unknown", 99, "GRAY_SHULKER_BOX", 0x555555)

fun runTypeFromLeague(league: League): RunType? {
    val slug = league.slug ?: return null
    return RunType(
        longId = slug,
        displayName = league.displayName ?: slug,
        runTypeId = league.display?.runTypeId ?: 0,
        deckMaterial = league.display?.material ?: "GRAY_SHULKER_BOX",
        displayColour = league.display?.colour ?: 0x555555,
    )
}

// Resolve a RunType from an identifier that is either a league slug (canonical)
// or a numeric runTypeId (from the filter-mode scoreboard). Everything resolves
// through the league cache — no hardcoded run-type table.
fun findRunTypeByIdFromCache(leagueCache: LeagueCache, id: String): RunType? {
    if (id.isEmpty()) return null

    // Slug is authoritative.
    leagueCache.getLeagueBySlug(id)?.let { return runTypeFromLeague(it) }

    // Numeric runTypeId lookup (e.g., "2" from the filter-mode scoreboard).
    val numericId = id.toIntOrNull()
    if (numericId != null) {
        val league = leagueCache.getLeagues().find { it.display?.runTypeId == numericId }
        if (league != null) return runTypeFromLeague(league)
    }

    return null
}

fun getRunTypeByIdFromCache(leagueCache: LeagueCache, id: String): RunType {
    return findRunTypeByIdFromCache(leagueCache, id) ?: unknownRunType
}

fun getRunTypesFromCache(leagueCache: LeagueCache): List<RunType> {
    return leagueCache.getLeagues().mapNotNull { runTypeFromLeague(it) }
}
