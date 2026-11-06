package com.magd.tanweer.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSnapshotValidationTest {

    @Test
    fun acceptsOnlySnapshotsWhoseDeclaredCountMatchesTheReceivedRows() {
        assertTrue(ServerSnapshotValidation.hasMatchingCount(0, 0))
        assertTrue(ServerSnapshotValidation.hasMatchingCount(4, 4))
        assertFalse(ServerSnapshotValidation.hasMatchingCount(4, 3))
        assertFalse(ServerSnapshotValidation.hasMatchingCount(0, 2))
        assertFalse(ServerSnapshotValidation.hasMatchingCount(-1, 0))
    }

    @Test
    fun rejectsBlankAndDuplicateRecordIdsBeforeRoomReconciliation() {
        assertTrue(ServerSnapshotValidation.hasUniqueNonBlankIds(emptyList()))
        assertTrue(ServerSnapshotValidation.hasUniqueNonBlankIds(listOf("a", "b")))
        assertFalse(ServerSnapshotValidation.hasUniqueNonBlankIds(listOf("a", "")))
        assertFalse(ServerSnapshotValidation.hasUniqueNonBlankIds(listOf("a", "a")))
    }
}
