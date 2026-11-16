package org.trackedout.citadel.commands.party

import org.trackedout.client.models.League

private const val SOLO = 1

// Most players a party for this league can hold, leader included
val League.maxPartySize: Int
    get() = party?.maxPlayers ?: SOLO

fun League.supportsParties(): Boolean = maxPartySize > SOLO
