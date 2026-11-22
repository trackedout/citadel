package org.trackedout.citadel

import com.google.gson.Gson
import org.trackedout.client.apis.ConfigApi
import org.trackedout.client.infrastructure.ClientError
import org.trackedout.client.infrastructure.ClientException
import java.time.Duration
import java.time.Instant

private val gson = Gson()

/**
 * Extract a human-readable message from a failed API call.
 *
 * The generated client's [ClientException.message] is only the generic HTTP
 * reason phrase (e.g. "Client error : 429 Too Many Requests"). The backend's
 * actual explanation lives in the JSON response body ({"code":..,"message":..}),
 * which is carried on the [ClientError] response. Prefer that body message so
 * players see why the request was rejected.
 */
fun ClientException.userMessage(): String {
    val body = (this.response as? ClientError<*>)?.body as? String
    if (!body.isNullOrBlank()) {
        try {
            gson.fromJson(body, DungaDungaException::class.java)?.message?.let { return it }
        } catch (_: Exception) {
            // Body wasn't the expected JSON shape; fall through to the raw body.
        }
        return body
    }
    return this.message ?: "Unknown error"
}

fun ConfigApi.getBool(entity: String, key: String, default: Boolean = false): Boolean {
    return try {
        this.configsGet(entity, key).value == "true"
    } catch (e: ClientException) {
        default
    }
}

// Returns a relative time in the future, or null if it's already past
fun getFutureRelativeTime(isoString: String): String? {
    val target = Instant.parse(isoString)
    val now = Instant.now()
    val diff = Duration.between(now, target)

    if (diff.isNegative || diff.isZero) return null

    val days = diff.toDays()
    val hours = diff.toHours() % 24
    val minutes = diff.toMinutes() % 60

    return when {
        days >= 1 -> "$days day${if (days > 1) "s" else ""}"
        hours >= 1 -> "$hours hours and $minutes minutes"
        else -> "very soon!"
    }
}
