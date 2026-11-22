package org.trackedout.citadel.config

import org.bukkit.entity.Player
import org.trackedout.client.models.League

private const val STATE_ACTIVE = "active"
private const val STATE_COMPLETED = "completed"
private const val STATE_DRAFT = "draft"

// Staff holding this permission can select, seed and queue draft leagues to test them
const val DRAFT_QUEUE_PERMISSION = "decked-out.queue.draft"

fun League.isDraft(): Boolean = state == STATE_DRAFT

// Whether a player may pick a league as their run mode. A running league is
// always offered. A finished one is offered only to players who have a deck in
// it, so they can look back at it; everyone else would just see an empty deck.
// A draft is offered only to players who may queue drafts. Archived leagues, and
// leagues whose state is unknown, are never offered.
fun League.isSelectableRunMode(playerHasDeck: Boolean, canQueueDrafts: Boolean): Boolean = when (state) {
    STATE_ACTIVE -> true
    STATE_COMPLETED -> playerHasDeck
    STATE_DRAFT -> canQueueDrafts
    else -> false
}

// Event metadata telling the backend that the queueing player may play a draft
const val ALLOW_DRAFT_METADATA_KEY = "allow-draft"

fun Player.allowDraftMetadata(league: League?): Map<String, String> =
    if (league?.isDraft() == true && hasPermission(DRAFT_QUEUE_PERMISSION)) {
        mapOf(ALLOW_DRAFT_METADATA_KEY to "true")
    } else {
        emptyMap()
    }
