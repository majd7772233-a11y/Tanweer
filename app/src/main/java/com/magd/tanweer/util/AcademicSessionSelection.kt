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
 * A missing local generation marker means this installation predates the
 * marker, not that its user's cached database is stale.
 */
internal object DataGenerationPolicy {
    fun shouldPurgeLocalCache(previousGeneration: String?, serverGeneration: String?): Boolean {
        if (serverGeneration.isNullOrBlank()) return false
        return !previousGeneration.isNullOrBlank() && previousGeneration != serverGeneration
    }
}
