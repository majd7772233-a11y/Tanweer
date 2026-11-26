package com.magd.tanweer.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeReconnectPolicyTest {
    @Test
    fun gracefulAndTransientCloseCodesReconnect() {
        listOf(1000, 1001, 1011, 1012, 1013).forEach { code ->
            assertTrue("close code $code", RealtimeReconnectPolicy.shouldReconnectAfterClose(code))
        }
    }

    @Test
    fun protocolPolicyAndOversizedFrameCloseCodesDoNotReconnect() {
        listOf(1002, 1003, 1007, 1008, 1009, 1010).forEach { code ->
            assertFalse("close code $code", RealtimeReconnectPolicy.shouldReconnectAfterClose(code))
        }
    }

    @Test
    fun networkServerAndThrottlingFailuresRetryButAuthenticationFailuresStop() {
        assertTrue(RealtimeReconnectPolicy.shouldReconnectAfterFailure(null))
        listOf(408, 425, 429, 500, 502, 503).forEach { code ->
            assertTrue("HTTP $code", RealtimeReconnectPolicy.shouldReconnectAfterFailure(code))
        }
        listOf(400, 401, 403, 404, 422).forEach { code ->
            assertFalse("HTTP $code", RealtimeReconnectPolicy.shouldReconnectAfterFailure(code))
        }
    }

    @Test
    fun reconnectDelayGrowsExponentiallyAndRemainsCapped() {
        assertEquals(1_000L, RealtimeReconnectPolicy.delayMillis(0))
        assertEquals(2_000L, RealtimeReconnectPolicy.delayMillis(1))
        assertEquals(4_000L, RealtimeReconnectPolicy.delayMillis(2))
        assertEquals(30_000L, RealtimeReconnectPolicy.delayMillis(5))
        assertEquals(30_000L, RealtimeReconnectPolicy.delayMillis(20))
    }
}
