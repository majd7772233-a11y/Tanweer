package com.magd.tanweer.data.notifications

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationNavigationPolicyTest {

    @Test
    fun parsesOnlyKnownNotificationDestinations() {
        assertEquals(NotificationDestination.HOMEWORK, NotificationDestination.parse("HOMEWORK"))
        assertEquals(NotificationDestination.EXAMS, NotificationDestination.parse(" exams "))
        assertEquals(NotificationDestination.SCHEDULE, NotificationDestination.parse("Schedule"))
        assertEquals(NotificationDestination.VERSION_CHECK, NotificationDestination.parse("VERSION_CHECK"))
        assertEquals(NotificationDestination.CHAT, NotificationDestination.parse("CHAT"))
        assertEquals(NotificationDestination.ISSUES, NotificationDestination.parse("ISSUES"))
        assertNull(NotificationDestination.parse(null))
        assertNull(NotificationDestination.parse(""))
        assertNull(NotificationDestination.parse("ADMIN_DASHBOARD"))
        assertNull(NotificationDestination.parse("../private"))
    }

    @Test
    fun coldStartNotificationTargetIsConsumedOnlyOnce() {
        NotificationLaunchStore.stage("HOMEWORK")

        assertEquals(NotificationDestination.HOMEWORK, NotificationLaunchStore.consume())
        assertNull(NotificationLaunchStore.consume())
    }

    @Test
    fun notificationLaunchReusesTheExistingTaskInsteadOfClearingIt() {
        val flags = NotificationNavigationPolicy.activityLaunchFlags()

        assertTrue(flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertFalse(flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
    }

    @Test
    fun protectedDestinationRequiresBothFreshServerSnapshotsAndTheSameSession() {
        assertTrue(NotificationNavigationPolicy.mayOpenAfterValidation(true, true, true))
        assertFalse(NotificationNavigationPolicy.mayOpenAfterValidation(false, true, true))
        assertFalse(NotificationNavigationPolicy.mayOpenAfterValidation(true, false, true))
        assertFalse(NotificationNavigationPolicy.mayOpenAfterValidation(true, true, false))
    }
}
