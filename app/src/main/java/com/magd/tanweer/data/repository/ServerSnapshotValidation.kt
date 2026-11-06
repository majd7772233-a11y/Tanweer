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

    fun hasUniqueNonBlankIds(ids: Collection<String>): Boolean {
        if (ids.any { it.isBlank() }) return false
        return ids.size == ids.toSet().size
    }
}
