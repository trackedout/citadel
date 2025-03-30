package org.trackedout.citadel.config

enum class LegacyRunTypes(val runType: String) {
    PRACTICE("practice"),
    COMPETITIVE("competitive"),
}

val legacyRunTypes = LegacyRunTypes.entries.map { r -> r.runType }
