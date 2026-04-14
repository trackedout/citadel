package org.trackedout.citadel

import org.trackedout.client.apis.ConfigApi
import org.trackedout.client.infrastructure.ClientException
import java.time.Duration
import java.time.Instant

fun ConfigApi.getInt(entity: String, key: String): Int? {
    return try {
        this.configsGet(entity, key).let { Integer.parseInt(it.value) }
    } catch (e: ClientException) {
        System.err.println("Error fetching config for $entity and key $key: ${e.message}")
        e.printStackTrace()
        null // default
    }
}

fun ConfigApi.getRelativeFutureDate(entity: String, key: String): String? {
    return try {
        this.configsGet(entity, key).value?.let {
            getFutureRelativeTime(it)
        }
    } catch (e: ClientException) {
        System.err.println("Error fetching config for $entity and key $key: ${e.message}")
        e.printStackTrace()
        null // default
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
