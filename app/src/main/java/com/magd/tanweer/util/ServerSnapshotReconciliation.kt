package com.magd.tanweer.util

/**
 * Merges an authoritative server snapshot with local mutations that have not
 * been acknowledged by the server yet. A pending local row wins on ID collision;
 * pending rows missing from the server response are retained until their mutation
 * is resolved. All non-pending rows are replaced by the server snapshot.
 */
internal object ServerSnapshotReconciliation {

    fun <T> preservePending(
        serverRows: List<T>,
        localPendingRows: List<T>,
        idSelector: (T) -> String
    ): List<T> {
        if (localPendingRows.isEmpty()) return serverRows

        val pendingById = localPendingRows.associateBy(idSelector)
        val serverRowsWithoutConflicts = serverRows.filterNot { idSelector(it) in pendingById }
        return serverRowsWithoutConflicts + localPendingRows
    }
}
