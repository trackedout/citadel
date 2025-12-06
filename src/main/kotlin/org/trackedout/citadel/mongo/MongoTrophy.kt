package org.trackedout.citadel.mongo

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.time.Instant

data class MongoTrophy(
    @BsonId val id: ObjectId = ObjectId(),

    val totKey: String,
    val armorStand: ArmorStand? = null,
    val sign: Sign,
    val player: String? = null,

    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ArmorStand(
    val head: String,
    val x: Int,
    val y: Int,
    val z: Int,
)

data class Sign(
    val text: List<String>, // should have 4 elements
    val x: Int,
    val y: Int,
    val z: Int,
)
