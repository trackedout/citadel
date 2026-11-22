package org.trackedout.citadel.config

import org.trackedout.client.apis.LeaguesApi
import org.trackedout.client.infrastructure.ClientException
import org.trackedout.client.models.LeaguePhase
import org.trackedout.client.models.LeaguePhases
import java.util.logging.Logger

private const val NOT_FOUND = 404

// The phases of the competitive league the lobby shows. The backend picks it: the
// scored league with an active phase, else the one whose schedule ended last.
// Calls the backend, so use it off the main thread.
class CompetitivePhases(
    private val leaguesApi: LeaguesApi,
    private val logger: Logger,
) {
    // Null when no league has a phase schedule or the backend is unreachable
    fun current(): LeaguePhases? =
        try {
            leaguesApi.leaguePhasesCurrent()
        } catch (e: ClientException) {
            if (e.statusCode != NOT_FOUND) logger.warning("Failed to fetch the current league phases: ${e.message}")
            null
        } catch (e: Exception) {
            logger.warning("Failed to fetch the current league phases: ${e.message}")
            null
        }
}

fun LeaguePhases.activePhaseSchedule(): LeaguePhase? =
    activePhase?.let { number -> phases?.find { it.phase == number } }

// The league and highest completed phase the leaderboard covers
data class LeaderboardScope(val leagueSlug: String, val phase: Int) {
    val seenKey = "leaderboard-seen-$leagueSlug-phase-$phase"
}

// Null when the league has no slug or no completed phase yet
fun LeaguePhases.leaderboardScope(): LeaderboardScope? {
    val slug = leagueSlug ?: return null
    val phase = leaderboardPhase ?: return null
    return LeaderboardScope(slug, phase)
}
