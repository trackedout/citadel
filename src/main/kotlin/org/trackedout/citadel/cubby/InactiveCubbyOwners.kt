package org.trackedout.citadel.cubby

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

private const val DAYS_PER_YEAR = 365
private const val DAYS_PER_MONTH = 30
private const val COLUMN_GAP = "  "
private const val NEVER_SEEN = "never"

/** A player document: the name with its proper capitalisation and when the player last joined. */
data class KnownPlayer(val name: String, val lastSeen: Instant?)

data class InactiveOwner(val name: String, val lastSeen: Instant?, val cubbyIds: List<String>)

data class PruneCandidates(
    val inactive: List<InactiveOwner>,
    /** Owners with no player document, for example because they were renamed. These are never reset automatically. */
    val unmatched: Map<String, List<String>>,
    val staffSkipped: Int,
)

object InactiveCubbyOwners {
    /**
     * @param cubbiesByOwner cubby ids by owner name
     * @param knownPlayers player documents by lowercase player name; an owner that is absent has no player document
     */
    fun find(
        cubbiesByOwner: Map<String, List<String>>,
        staffNames: Set<String>,
        knownPlayers: Map<String, KnownPlayer>,
        cutoff: Instant,
    ): PruneCandidates {
        val staff = staffNames.map { it.lowercase() }.toSet()
        val (staffOwners, otherOwners) = cubbiesByOwner.entries.partition { it.key.lowercase() in staff }
        val (known, unknown) = otherOwners.partition { it.key.lowercase() in knownPlayers }

        val inactive = known
            .map { (owner, cubbyIds) ->
                val player = knownPlayers.getValue(owner.lowercase())
                InactiveOwner(player.name, player.lastSeen, cubbyIds)
            }
            .filter { it.lastSeen == null || it.lastSeen < cutoff }
            .sortedWith(compareBy(nullsFirst()) { it.lastSeen })

        return PruneCandidates(inactive, unknown.associate { it.key to it.value }, staffOwners.size)
    }

    fun table(owners: List<InactiveOwner>, now: Instant): List<String> {
        val rows = owners.map {
            listOf(it.name, it.lastSeen?.atOffset(ZoneOffset.UTC)?.toLocalDate()?.toString() ?: NEVER_SEEN, relativeTime(it.lastSeen, now), it.cubbyIds.joinToString(","))
        }
        val widths = (0 until 3).map { column -> rows.maxOfOrNull { it[column].length } ?: 0 }
        return rows.map { row -> row.mapIndexed { column, cell -> if (column < 3) cell.padEnd(widths[column]) else cell }.joinToString(COLUMN_GAP) }
    }

    fun relativeTime(lastSeen: Instant?, now: Instant): String {
        if (lastSeen == null) return "-"
        val days = Duration.between(lastSeen, now).toDays().toInt()
        val years = days / DAYS_PER_YEAR
        val months = days % DAYS_PER_YEAR / DAYS_PER_MONTH
        return when {
            years > 0 -> "${years}y ${months}mo ago"
            months > 0 -> "${months}mo ago"
            else -> "${days}d ago"
        }
    }
}
