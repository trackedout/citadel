package org.trackedout.citadel.mongo

const val LEAGUE_PHASE_STATS_COLLECTION = "leaguePhaseStats"

data class MongoPlayerStats(
    val leagueSlug: String,
    val phase: Int,
    val player: String,
    val stats: Stats,
)

data class Stats(
    val total: Int,
    val easy: Int,
    val medium: Int,
    val hard: Int,
    val deadly: Int,
    val deepFrost: Int,
    val wins: Int,
    val losses: Int,
    val tomesSubmitted: Int,
)
