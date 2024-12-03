package org.trackedout.citadel.mongo

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.time.Instant

data class MongoClaim(
    @BsonId private val id: ObjectId,
    val player: String,
    val type: String,
    val state: String,
    val claimant: String?,
    val metadata: Map<String, String>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun id(): String? {
        return metadata["run-id"]
    }
}
