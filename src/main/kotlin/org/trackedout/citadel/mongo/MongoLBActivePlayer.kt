package org.trackedout.citadel.mongo

data class MongoLBActivePlayer(
    val player: String,
    val claimCount: Int,
    val headLocation: HeadLocation,
)

data class HeadLocation(
    val x: Int,
    val y: Int,
    val z: Int,
)
