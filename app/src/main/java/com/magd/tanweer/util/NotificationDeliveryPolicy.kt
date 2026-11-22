package com.magd.tanweer.util

/**
 * Prevents an asynchronous reminder check that started before a preference
 * change from publishing after the user has changed that preference.
 */
internal object NotificationDeliveryPolicy {
    fun mayPost(
        enabled: Boolean,
        expectedGeneration: Long,
        currentGeneration: Long
    ): Boolean = enabled && expectedGeneration == currentGeneration
}
