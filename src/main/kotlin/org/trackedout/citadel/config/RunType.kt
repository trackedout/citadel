package org.trackedout.citadel.config

import org.trackedout.client.models.League

data class RunType(
    val shortId: Char,
    val longId: String,
    val displayName: String,
    val runTypeId: Int,
    val deckMaterial: String,
    val displayColour: Int,
) {
    fun deckType(): String {
        return shortId.toString()
    }
}

val unknownRunType = RunType('u', "unknown", "Unknown", 99, "GRAY_SHULKER_BOX", 0x555555)

fun runTypeFromLeague(league: League): RunType? {
    val shortId = league.display?.shortId?.firstOrNull() ?: return null
    val slug = league.slug ?: return null
    return RunType(
        shortId = shortId,
        longId = slug,
        displayName = league.displayName ?: slug,
        runTypeId = 0,
        deckMaterial = league.display?.material ?: "GRAY_SHULKER_BOX",
        displayColour = league.display?.colour ?: 0x555555,
    )
}

fun findRunTypeByIdFromCache(leagueCache: LeagueCache, id: String): RunType? {
    if (id.isEmpty()) return null

    val league = leagueCache.getLeagueByShortId(id.take(1))
        ?: leagueCache.getLeagueBySlug(id)
    return league?.let { runTypeFromLeague(it) }
}

fun getRunTypeByIdFromCache(leagueCache: LeagueCache, id: String): RunType {
    return findRunTypeByIdFromCache(leagueCache, id) ?: unknownRunType
}

fun getRunTypesFromCache(leagueCache: LeagueCache): List<RunType> {
    return leagueCache.getLeagues().mapNotNull { runTypeFromLeague(it) }
}
