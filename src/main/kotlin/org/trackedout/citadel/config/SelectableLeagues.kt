package org.trackedout.citadel.config

import org.trackedout.client.models.League

private const val STATE_ACTIVE = "active"
private const val STATE_COMPLETED = "completed"

// Whether a player may pick a league as their run mode. A running league is
// always offered. A finished one is offered only to players who have a deck in
// it, so they can look back at it; everyone else would just see an empty deck.
// Draft and archived leagues, and leagues whose state is unknown, are never offered.
fun League.isSelectableRunMode(playerHasDeck: Boolean): Boolean = when (state) {
    STATE_ACTIVE -> true
    STATE_COMPLETED -> playerHasDeck
    else -> false
}
