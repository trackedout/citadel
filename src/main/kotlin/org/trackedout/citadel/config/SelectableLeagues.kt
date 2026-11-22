package org.trackedout.citadel.config

import org.trackedout.client.models.League

private const val STATE_ACTIVE = "active"
private const val STATE_COMPLETED = "completed"
private const val STATE_DRAFT = "draft"

fun League.isDraft(): Boolean = state == STATE_DRAFT

// The builders of a world are the only players who can select, seed and queue the
// draft leagues that play it. Minecraft player names are case-insensitive.
fun isWorldBuilder(worldBuilders: List<String>?, playerName: String): Boolean =
    worldBuilders.orEmpty().any { it.equals(playerName, ignoreCase = true) }

// Whether a player may queue into a league: running leagues, plus drafts for builders of their world
fun League.isQueueable(isWorldBuilder: Boolean): Boolean = state == STATE_ACTIVE || (isDraft() && isWorldBuilder)

// Whether a player may pick a league as their run mode. A running league is
// always offered. A finished one is offered only to players who have a deck in
// it, so they can look back at it; everyone else would just see an empty deck.
// A draft is offered only to builders of its world. Archived leagues, and
// leagues whose state is unknown, are never offered.
fun League.isSelectableRunMode(playerHasDeck: Boolean, isWorldBuilder: Boolean): Boolean = when (state) {
    STATE_ACTIVE -> true
    STATE_COMPLETED -> playerHasDeck
    STATE_DRAFT -> isWorldBuilder
    else -> false
}
