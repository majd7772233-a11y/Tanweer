package com.magd.tanweer.data.remote

internal object RealtimeReconnectPolicy {
    const val BASE_DELAY_MS = 1_000L
    const val MAX_DELAY_MS = 30_000L
    const val MAX_BACKOFF_STEP = 5

    private val permanentCloseCodes = setOf(1002, 1003, 1007, 1008, 1009, 1010)

    fun shouldReconnectAfterClose(code: Int): Boolean = code !in permanentCloseCodes

    fun shouldReconnectAfterFailure(httpCode: Int?): Boolean {
        if (httpCode == null) return true
        return httpCode == 408 ||
            httpCode == 425 ||
            httpCode == 429 ||
            httpCode >= 500
    }

    fun delayMillis(attempt: Int): Long {
        val safeAttempt = attempt.coerceAtLeast(0).coerceAtMost(MAX_BACKOFF_STEP)
        return (BASE_DELAY_MS * (1L shl safeAttempt)).coerceAtMost(MAX_DELAY_MS)
    }
}
