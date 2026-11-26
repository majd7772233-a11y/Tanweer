package com.magd.tanweer.data.remote

import com.magd.tanweer.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkEnvironmentTest {

    private val expectedBaseUrl =
        if (BuildConfig.FLAVOR == "staging") {
            "https://tanweer-staging.magd.workers.dev/"
        } else {
            "https://tanweer.magd.workers.dev/"
        }

    @Test
    fun apiBaseUrlMatchesSelectedBuildFlavor() {
        assertEquals(expectedBaseUrl, NetworkModule.apiBaseUrl)
    }

    @Test
    fun relativeApiUrlsUseSelectedBackend() {
        assertEquals(
            "${expectedBaseUrl}api/v1/health",
            NetworkModule.resolveApiUrl("/api/v1/health")
        )
    }

    @Test
    fun websocketUrlsUseSelectedBackend() {
        val expectedWebSocketBase = expectedBaseUrl.replaceFirst("https://", "wss://")
        assertEquals(
            "${expectedWebSocketBase}ws/groups/test-group",
            NetworkModule.resolveWebSocketUrl("/ws/groups/test-group")
        )
    }

    @Test
    fun absoluteExternalUrlsRemainUnchanged() {
        val externalUrl = "https://cdn.example.com/media/image.webp"
        assertEquals(externalUrl, NetworkModule.resolveApiUrl(externalUrl))
    }
}
