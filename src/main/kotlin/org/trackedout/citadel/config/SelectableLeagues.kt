package org.trackedout.citadel.config

import org.trackedout.client.models.League

private const val STATE_ACTIVE = "active"
private const val STATE_COMPLETED = "completed"

// A league a player may pick as their run mode: one that is running, or a finished
// one whose deck they can still view. Draft and archived leagues, and leagues
// whose state is unknown, are not offered.
fun League.isSelectableRunMode(): Boolean = state == STATE_ACTIVE || state == STATE_COMPLETED
