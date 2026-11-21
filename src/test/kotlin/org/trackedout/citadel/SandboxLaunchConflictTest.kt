package org.trackedout.citadel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.trackedout.client.infrastructure.ClientError
import org.trackedout.client.infrastructure.ClientException

class SandboxLaunchConflictTest {
    private fun conflict(body: Any?) =
        ClientException("conflict", 409, ClientError<Any>(body = body, statusCode = 409))

    @Test
    fun `build server is read from a 409 body`() {
        val body = """{"code":409,"message":"Built on a persistent server","buildServer":"builders2"}"""

        assertEquals("builders2", conflict(body).buildServer())
    }

    @Test
    fun `build server is null when the body has none`() {
        assertNull(conflict("""{"code":409,"message":"nope"}""").buildServer())
    }

    @Test
    fun `build server is null when the body is not json`() {
        assertNull(conflict("not json").buildServer())
    }
}
