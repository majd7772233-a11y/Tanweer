package com.magd.tanweer.util

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Keeps academic navigation state stable across Android process recreation.
 * Values are scoped by user ID by the caller.
 */
internal object AcademicSessionSelection {

    fun resolveGroupId(savedGroupId: String?, defaultGroupId: String?): String {
        return savedGroupId?.takeIf { it.isNotBlank() }
            ?: defaultGroupId?.takeIf { it.isNotBlank() }
            ?: ""
    }

    /**
     * Keeps a saved selection only while it is still an active server-confirmed membership.
     * The default group is preferred next; otherwise the first server-returned membership is used.
     */
    fun resolveAuthorizedGroupId(
        savedGroupId: String?,
        defaultGroupId: String?,
        availableGroupIds: Collection<String>
    ): String {
        val available = availableGroupIds.toSet()
        return savedGroupId?.takeIf { it.isNotBlank() && it in available }
            ?: defaultGroupId?.takeIf { it.isNotBlank() && it in available }
            ?: availableGroupIds.firstOrNull().orEmpty()
    }

    fun resolveStudyDate(savedDate: String?, today: String): String {
        return savedDate?.takeIf(::isValidIsoDate) ?: today
    }

    fun isValidIsoDate(value: String): Boolean {
        if (!value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return false
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value) != null
        }.getOrDefault(false)
    }
}

/**
 * The server rotates data_generation only after an explicit system-wide purge.
 * A missing marker is a legacy/first-install state and must never trigger deletion.
 */
internal object DataGenerationPolicy {
    fun hasChanged(previousGeneration: String?, serverGeneration: String?): Boolean {
        if (serverGeneration.isNullOrBlank() || previousGeneration.isNullOrBlank()) return false
        return previousGeneration != serverGeneration
    }
}

/**
 * Preferences that belong to the signed-in account, not to the app installation.
 * Theme, typography, notification preferences and the separate device identity store
 * intentionally survive an account purge.
 */
internal object LocalAccountDataPolicy {
    private val fixedAccountKeys = setOf(
        "auth_token",
        "last_open_date",
        "last_active_time",
        "previous_session_time"
    )

    fun keysToRemove(allKeys: Set<String>): Set<String> = allKeys.filterTo(mutableSetOf()) { key ->
        key in fixedAccountKeys ||
            key.startsWith("last_open_date_") ||
            key.startsWith("selected_group_id_") ||
            key.startsWith("selected_study_date_")
    }
}
