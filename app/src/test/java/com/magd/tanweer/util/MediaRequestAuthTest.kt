package com.magd.tanweer.util

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRequestAuthTest {
    @Test
    fun sendsLiveBearerTokenToConfiguredTanweerOrigin() {
        val headers = buildMediaRequestHeaders(
            remoteUrl = "https://api.tanweer.test/api/v1/media/med_123",
            apiBaseUrl = "https://api.tanweer.test/",
            authToken = "  session-token  ",
            groupId = "class_10_A"
        )

        assertEquals("Bearer session-token", headers["Authorization"])
        assertEquals("class_10_A", headers["X-Group-Id"])
    }

    @Test
    fun neverSendsBearerTokenToExternalMediaHost() {
        val headers = buildMediaRequestHeaders(
            remoteUrl = "https://cdn.example.test/lesson/page.jpg",
            apiBaseUrl = "https://api.tanweer.test/",
            authToken = "session-token",
            groupId = "class_10_A"
        )

        assertTrue(headers.isEmpty())
        assertFalse(headers.containsKey("Authorization"))
    }

    @Test(expected = IOException::class)
    fun rejectsInternalMediaRequestWhenNoActiveSessionTokenExists() {
        buildMediaRequestHeaders(
            remoteUrl = "https://api.tanweer.test/api/v1/media/med_123",
            apiBaseUrl = "https://api.tanweer.test/",
            authToken = null,
            groupId = "class_10_A"
        )
    }

    @Test
    fun treatsExplicitDefaultPortAsSameOrigin() {
        val headers = buildMediaRequestHeaders(
            remoteUrl = "https://api.tanweer.test:443/api/v1/media/med_123",
            apiBaseUrl = "https://api.tanweer.test/",
            authToken = "session-token",
            groupId = ""
        )

        assertEquals("Bearer session-token", headers["Authorization"])
        assertFalse(headers.containsKey("X-Group-Id"))
    }
}
