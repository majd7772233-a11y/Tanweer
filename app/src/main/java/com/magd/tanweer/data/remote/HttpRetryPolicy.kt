package com.magd.tanweer.data.remote

/**
 * Only transient HTTP failures should be retried through the offline outbox.
 * Client errors are terminal except request timeouts and rate limiting.
 */
internal fun isRetryableHttpStatus(code: Int): Boolean {
    return code == 408 || code == 429 || code in 500..599
}

/**
 * Signals that retrying the same outbox mutation cannot fix a client-side rejection.
 */
internal class PermanentHttpFailureException(
    val statusCode: Int,
    message: String
) : Exception(message)
