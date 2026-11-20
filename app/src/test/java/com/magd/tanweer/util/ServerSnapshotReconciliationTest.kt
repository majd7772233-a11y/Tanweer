package com.magd.tanweer.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerSnapshotReconciliationTest {
    private data class Row(val id: String, val status: String, val value: String)

    @Test
    fun localPendingRowsWinIdConflictsAndRemainWhenAbsentFromServer() {
        val server = listOf(
            Row("server-only", "SYNCED", "fresh"),
            Row("local-pending", "SYNCED", "stale-server-copy"),
            Row("local-syncing", "SYNCED", "stale-server-copy-2")
        )
        val localPending = listOf(
            Row("local-pending", "PENDING", "optimistic-value"),
            Row("local-syncing", "SYNCING", "in-flight-value"),
            Row("not-on-server", "FAILED", "retained-failure")
        )

        val reconciled = ServerSnapshotReconciliation.preservePending(
            serverRows = server,
            localPendingRows = localPending,
            idSelector = Row::id
        )

        assertEquals(
            listOf("server-only", "local-pending", "local-syncing", "not-on-server"),
            reconciled.map { it.id }
        )
        assertEquals("optimistic-value", reconciled.first { it.id == "local-pending" }.value)
        assertEquals("in-flight-value", reconciled.first { it.id == "local-syncing" }.value)
        assertEquals("retained-failure", reconciled.first { it.id == "not-on-server" }.value)
    }

    @Test
    fun emptyPendingListUsesTheServerSnapshotAsAuthoritative() {
        val server = listOf(Row("server-a", "SYNCED", "a"), Row("server-b", "SYNCED", "b"))
        assertEquals(
            server,
            ServerSnapshotReconciliation.preservePending(serverRows = server, localPendingRows = emptyList(), idSelector = Row::id)
        )
    }

    @Test
    fun pendingRowsAreDeduplicatedByIdWithoutLosingLocalValue() {
        val result = ServerSnapshotReconciliation.preservePending(
            serverRows = listOf(Row("same", "SYNCED", "server")),
            localPendingRows = listOf(Row("same", "PENDING", "local")),
            idSelector = Row::id
        )
        assertEquals(1, result.size)
        assertEquals("local", result.single().value)
    }
}
