package org.trackedout.citadel.mongo

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.time.Instant

data class MongoTrophySection(
    @param:BsonId val id: ObjectId = ObjectId(),
    val section: String,
    val order: Int,
    val trophies: List<String>,
    val updatedAt: Instant,
)
