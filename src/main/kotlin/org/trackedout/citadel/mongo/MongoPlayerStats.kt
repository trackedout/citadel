package org.trackedout.citadel.mongo

data class MongoPlayerStats(
    val player: String,
    val stats: Stats,
)

data class Stats(
    val total: Int,
    val practice: RunStats,
    val competitive: RunStats,
    val tomesSubmitted: Int,
)

data class RunStats(
    val total: Int,
    val easy: Int,
    val medium: Int,
    val hard: Int,
    val deadly: Int,
    val deepFrost: Int,
    val wins: Int,
    val losses: Int,
)
