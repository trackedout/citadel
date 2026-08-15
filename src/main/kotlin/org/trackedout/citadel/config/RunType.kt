package org.trackedout.citadel.config

import org.trackedout.client.models.League

data class RunType(
    val shortId: String,
    val longId: String,
    val displayName: String,
    val runTypeId: Int,
    val deckMaterial: String,
    val displayColour: Int,
) {
    fun deckType(): String {
        return shortId
    }
}

val unknownRunType = RunType("u", "unknown", "Unknown", 99, "GRAY_SHULKER_BOX", 0x555555)

fun runTypeFromLeague(league: League): RunType? {
    val shortId = league.display?.shortId ?: return null
    val slug = league.slug ?: return null
    return RunType(
        shortId = shortId,
        longId = slug,
        displayName = league.displayName ?: slug,
        runTypeId = league.display?.runTypeId ?: 0,
        deckMaterial = league.display?.material ?: "GRAY_SHULKER_BOX",
        displayColour = league.display?.colour ?: 0x555555,
    )
}

fun findRunTypeByIdFromCache(leagueCache: LeagueCache, id: String): RunType? {
    if (id.isEmpty()) return null

    // Try numeric runTypeId lookup (e.g., "2" from the filter-mode scoreboard)
    val numericId = id.toIntOrNull()
    if (numericId != null) {
        val league = leagueCache.getLeagues().find { it.display?.runTypeId == numericId }
        if (league != null) return runTypeFromLeague(league)
    }

    // Try shortId match (e.g. "c", "mm", "s3")
    val league = leagueCache.getLeagueByShortId(id)
        ?: leagueCache.getLeagueBySlug(id)
    return league?.let { runTypeFromLeague(it) }
}

fun getRunTypeByIdFromCache(leagueCache: LeagueCache, id: String): RunType {
    return findRunTypeByIdFromCache(leagueCache, id) ?: unknownRunType
}

fun getRunTypesFromCache(leagueCache: LeagueCache): List<RunType> {
    return leagueCache.getLeagues().mapNotNull { runTypeFromLeague(it) }
}
