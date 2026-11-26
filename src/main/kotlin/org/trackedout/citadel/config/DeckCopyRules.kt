package org.trackedout.citadel.config

import org.trackedout.client.models.League

// A league's spec lists the leagues whose decks may be copied over its own deck
fun League.copySources(): List<String> = deck?.copyFrom.orEmpty()

fun League.acceptsDeckCopyFrom(sourceSlug: String): Boolean = sourceSlug in copySources()

// Decks that some league accepts a copy of
fun List<League>.copySourceSlugs(): List<String> {
    val knownSlugs = mapNotNull { it.slug }.toSet()
    return flatMap { it.copySources() }.distinct().filter { it in knownSlugs }
}

// Leagues that accept a copy of the given deck
fun List<League>.copyTargetSlugs(sourceSlug: String): List<String> =
    filter { it.acceptsDeckCopyFrom(sourceSlug) }.mapNotNull { it.slug }
