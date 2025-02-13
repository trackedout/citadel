package org.trackedout.citadel.mongo

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.time.Instant

data class MongoDungeon(
    @BsonId val id: ObjectId,

    val name: String,
    val ip: String,
    val state: String,

    val activePlayers: Long,
    val requiresRebuild: Boolean,
    val inUseDate: Instant? = null,

    val healthySince: Instant? = null,
    val unhealthySince: Instant? = null,

    val createdAt: Instant,
    val updatedAt: Instant,
)

/*
export enum InstanceStates {
  AVAILABLE = 'available', // ready for use
  RESERVED = 'reserved', // assigned to a player, waiting for the player to be notified
  AWAITING_PLAYER = 'awaiting-player', // player notified + attempted teleport to the dungeon
  IN_USE = 'in-use', // player has connected to the dungeon
  BUILDING = 'building', // starting up / rebuilding
  UNREACHABLE = 'unreachable', // health-checks failed
}
 */

enum class DungeonState {
    AVAILABLE,
    RESERVED,
    AWAITING_PLAYER,
    IN_USE,
    BUILDING,
    UNREACHABLE
}
