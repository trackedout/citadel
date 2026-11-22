package org.trackedout.citadel.shop

import org.trackedout.citadel.config.PREVIOUS_SLUG_TO_SLUG
import org.trackedout.client.models.SettingsLeagues

// Rewrites shop trades that predate the current league slugs. The leagues the
// legacy run-type chars stand for come from the settings
class ShopRuleRewriter(leagues: SettingsLeagues) {
    // Legacy grammar: [r]<char><SOURCE>x<n>=<char><TARGET>x<m>[,<TARGET>x<m>...]
    // The leading char (and the per-target char after '=') selected the run type.
    private val LEGACY_RULE_REGEX =
        "^(?<repeat>r|)(?<type>[a-z])(?<sourceType>.+?)x(?<sourceCount>\\d{1,3})=[a-z](?<targetTypes>(?:.+?x\\d{1,3},?)+)$".toRegex()

    // The slug that opens a slug-syntax rule: <slug>:<SOURCE>x<n>=...
    private val RULE_SLUG_REGEX = "^(?<slug>[a-z0-9-]+):".toRegex()

    // Legacy single-char run-type prefix -> canonical league slug
    private val legacyCharToSlug = mapOf(
        "p" to leagues.practice,
        "c" to leagues.shopRewriteCompetitive,
        "h" to leagues.hardcore,
    )

    // Either a legacy char-prefixed rule, or a slug-syntax rule naming a previous
    // slug (practice -> do2-s2-practice). Returns null if there is nothing to rewrite.
    fun rewrite(rule: String): String? = rewritePreviousSlug(rule) ?: rewriteLegacyCharRule(rule)

    private fun rewritePreviousSlug(rule: String): String? {
        val slug = RULE_SLUG_REGEX.find(rule)?.groups?.get("slug")?.value ?: return null
        val renamed = PREVIOUS_SLUG_TO_SLUG[slug] ?: return null
        return renamed + rule.removePrefix(slug)
    }

    // Rewrite a single legacy char-prefixed rule to the slug syntax
    // (<slug>:<SOURCE>x<n>=<TARGETS>[:r])
    private fun rewriteLegacyCharRule(rule: String): String? {
        val match = LEGACY_RULE_REGEX.matchEntire(rule) ?: return null
        val groups = match.groups

        val char = groups["type"]?.value ?: return null
        val slug = legacyCharToSlug[char] ?: return null
        val sourceType = groups["sourceType"]?.value ?: return null
        val sourceCount = groups["sourceCount"]?.value ?: return null
        val targetTypes = groups["targetTypes"]?.value ?: return null
        val repeat = groups["repeat"]?.value == "r"

        val suffix = if (repeat) ":r" else ""
        return "$slug:${sourceType}x$sourceCount=$targetTypes$suffix"
    }
}
