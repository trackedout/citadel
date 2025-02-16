package org.trackedout.citadel.mongo

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.time.Instant

data class MongoEvent(
    @BsonId val id: ObjectId,
    val name: String,
    val player: String,
    val count: Long,
    val server: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val sourceIP: String?,
    val processingFailed: Boolean?,
    val metadata: Map<String, String>?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
