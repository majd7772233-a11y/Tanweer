package com.magd.tanweer.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpRetryPolicyTest {

    @Test
    fun retriesOnlyTransientHttpFailures() {
        val cases = mapOf(
            200 to false,
            301 to false,
            400 to false,
            401 to false,
            403 to false,
            404 to false,
            409 to false,
            422 to false,
            408 to true,
            429 to true,
            500 to true,
            502 to true,
            503 to true,
            599 to true,
            600 to false
        )

        cases.forEach { (status, expected) ->
            assertEquals("Unexpected retry policy for HTTP $status", expected, isRetryableHttpStatus(status))
        }
    }
}
