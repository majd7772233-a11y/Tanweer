package com.magd.tanweer.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDeliveryPolicyTest {

    @Test
    fun enabledPreferenceAllowsOnlyTheCurrentGenerationToPost() {
        assertTrue(NotificationDeliveryPolicy.mayPost(true, 4L, 4L))
        assertFalse(NotificationDeliveryPolicy.mayPost(false, 4L, 4L))
        assertFalse(NotificationDeliveryPolicy.mayPost(true, 4L, 5L))
    }

    @Test
    fun disablingAndReEnablingDoesNotReviveAnOlderNotificationCheck() {
        val oldCheckGeneration = 10L
        val generationAfterOffThenOn = 12L
        assertFalse(
            NotificationDeliveryPolicy.mayPost(
                enabled = true,
                expectedGeneration = oldCheckGeneration,
                currentGeneration = generationAfterOffThenOn
            )
        )
    }
}
