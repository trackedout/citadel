package org.trackedout.citadel

import org.trackedout.client.infrastructure.ClientError
import org.trackedout.client.infrastructure.ClientException
import org.trackedout.client.infrastructure.Serializer

private data class SandboxLaunchConflict(val buildServer: String? = null)

/**
 * The persistent build server named in a 409 body from a sandbox launch
 * ({"code":409,"message":..,"buildServer":"builders2"}), or null when absent.
 */
fun ClientException.buildServer(): String? {
    val body = (this.response as? ClientError<*>)?.body as? String ?: return null
    return try {
        Serializer.moshi.adapter(SandboxLaunchConflict::class.java).fromJson(body)?.buildServer
    } catch (_: Exception) {
        null
    }
}
