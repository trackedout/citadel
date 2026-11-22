package org.trackedout.citadel.config

// Slugs that were in use before the <game>-<variant>-<mode> scheme. Trades stored
// in shops still name them until `/do shop rewrite` is run on the shop.
val PREVIOUS_SLUG_TO_SLUG = mapOf(
    "practice" to "do2-s2-practice",
    "hardcore" to "do2-s2-hardcore",
    "season-1" to "do2-s1-comp",
    "season-2" to "do2-s2-comp",
    "season-3" to "do2-s3-comp",
)
