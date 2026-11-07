package com.magd.tanweer.data.repository

/**
 * Validates server snapshots before Room reconciliation is allowed to remove
 * previously synchronized rows. A truncated or malformed snapshot must never
 * be interpreted as an authoritative empty dataset.
 */
internal object ServerSnapshotValidation {
    fun hasMatchingCount(declaredCount: Int, receivedCount: Int): Boolean {
        return declaredCount >= 0 && declaredCount == receivedCount
    }

    /** Older Workers may omit the count until they are upgraded; once present it is authoritative. */
    fun hasOptionalMatchingCount(declaredCount: Int?, receivedCount: Int): Boolean {
        return declaredCount == null || hasMatchingCount(declaredCount, receivedCount)
    }

    fun hasUniqueNonBlankIds(ids: Collection<String>): Boolean {
        if (ids.any { it.isBlank() }) return false
        return ids.size == ids.toSet().size
    }

    fun isValidYearMonth(month: String): Boolean {
        return Regex("""^\d{4}-(0[1-9]|1[0-2])$""").matches(month)
    }

    fun belongsToMonth(date: String, month: String): Boolean {
        if (!isValidYearMonth(month)) return false
        if (!Regex("""^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$""").matches(date)) {
            return false
        }
        return date.substring(0, 7) == month
    }

    fun allBelongToMonth(dates: Collection<String>, month: String): Boolean {
        return isValidYearMonth(month) && dates.all { belongsToMonth(it, month) }
    }

    fun allBelongToGroup(
        groupIds: Collection<String>,
        groupId: String,
        allowGlobalGroup: Boolean = false
    ): Boolean {
        if (groupId.isBlank()) return false
        return groupIds.all { it == groupId || (allowGlobalGroup && it == "all") }
    }
}
