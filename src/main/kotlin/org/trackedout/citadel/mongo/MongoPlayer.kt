package org.trackedout.citadel.mongo

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.time.Instant

data class MongoPlayer(
    @BsonId val id: ObjectId,

    val playerName: String,
    val server: String,
    val state: String,
    val isAllowedToPlayDO2: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,

    val lastLocation: LastLocation?,
    val lastSeen: Instant?,
)

data class LastLocation(
    @BsonId val id: ObjectId,

    val x: Double,
    val y: Long,
    val z: Double,
)
