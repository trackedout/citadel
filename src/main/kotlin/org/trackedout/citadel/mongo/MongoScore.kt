package org.trackedout.citadel.mongo

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.time.Instant

data class MongoScore(
    @BsonId val id: ObjectId,

    val player: String,
    val key: String,
    val value: Long,
    val metadata: Map<String, String>?,

    val createdAt: Instant,
    val updatedAt: Instant,
)
