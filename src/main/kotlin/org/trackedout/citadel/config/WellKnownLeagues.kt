package org.trackedout.citadel.config

// Slugs of the leagues that Citadel has dedicated behaviour for. Every other
// league is data driven, so these are the only ones named in code.
const val PRACTICE_SLUG = "do2-s2-practice"
const val HARDCORE_SLUG = "do2-s2-hardcore"

// Slugs that were in use before the <game>-<variant>-<mode> scheme. Trades stored
// in shops still name them until `/do shop rewrite` is run on the shop.
val PREVIOUS_SLUG_TO_SLUG = mapOf(
    "practice" to PRACTICE_SLUG,
    "hardcore" to HARDCORE_SLUG,
    "season-1" to "do2-s1-comp",
    "season-2" to "do2-s2-comp",
    "season-3" to "do2-s3-comp",
)
