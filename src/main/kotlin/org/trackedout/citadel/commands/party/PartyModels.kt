package org.trackedout.citadel.commands.party

import com.squareup.moshi.Json

data class GameMode(
    @Json(name = "name") val name: String,
    @Json(name = "displayName") val displayName: String,
    @Json(name = "minPlayers") val minPlayers: Int,
    @Json(name = "maxPlayers") val maxPlayers: Int,
    @Json(name = "permission") val permission: String,
    @Json(name = "enabled") val enabled: Boolean,
)

data class GameRegistryResponse(
    @Json(name = "results") val results: List<GameMode>,
)

data class Party(
    @Json(name = "id") val id: String,
    @Json(name = "leader") val leader: String,
    @Json(name = "game") val game: String,
    @Json(name = "displayName") val displayName: String? = null,
    @Json(name = "members") val members: List<String> = emptyList(),
    @Json(name = "state") val state: String? = null,
    @Json(name = "maxPlayers") val maxPlayers: Int? = null,
)

data class PartyGetResponse(
    @Json(name = "results") val results: List<Party>? = null,
    @Json(name = "id") val id: String? = null,
    @Json(name = "leader") val leader: String? = null,
    @Json(name = "game") val game: String? = null,
    @Json(name = "displayName") val displayName: String? = null,
    @Json(name = "members") val members: List<String>? = null,
    @Json(name = "state") val state: String? = null,
    @Json(name = "maxPlayers") val maxPlayers: Int? = null,
)

data class CreatePartyRequest(
    @Json(name = "leader") val leader: String,
    @Json(name = "game") val game: String,
)

data class PartyPlayerRequest(
    @Json(name = "player") val player: String,
)
