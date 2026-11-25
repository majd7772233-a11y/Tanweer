package com.magd.tanweer.data.notifications

import android.content.Intent
import java.util.Locale

/**
 * The only navigation targets accepted from locally generated reminder notifications.
 * Values are deliberately allow-listed; arbitrary extras must never select a screen.
 */
internal enum class NotificationDestination(val extraValue: String) {
    HOMEWORK("HOMEWORK"),
    EXAMS("EXAMS"),
    SCHEDULE("SCHEDULE"),
    VERSION_CHECK("VERSION_CHECK"),
    CHAT("CHAT"),
    ISSUES("ISSUES");

    companion object {
        fun parse(value: String?): NotificationDestination? {
            val normalized = value?.trim()?.uppercase(Locale.ROOT) ?: return null
            return values().firstOrNull { it.extraValue == normalized }
        }
    }
}

/**
 * Holds a cold-start notification target until the first ViewModel is constructed.
 * Consume is one-shot so Activity recreation cannot replay an old notification.
 */
internal object NotificationLaunchStore {
    @Volatile
    private var pendingDestination: NotificationDestination? = null

    @Synchronized
    fun stage(value: String?) {
        pendingDestination = NotificationDestination.parse(value)
    }

    @Synchronized
    fun consume(): NotificationDestination? {
        val destination = pendingDestination
        pendingDestination = null
        return destination
    }
}

internal object NotificationNavigationPolicy {
    /**
     * Reuse the existing task/activity rather than clearing the task and relaunching
     * the full application from the notification shade.
     */
    fun activityLaunchFlags(): Int =
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP

    fun mayOpenAfterValidation(
        profileSyncSucceeded: Boolean,
        membershipsSyncSucceeded: Boolean,
        sameSession: Boolean
    ): Boolean = profileSyncSucceeded && membershipsSyncSucceeded && sameSession
}
