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
    fun acceptsLegacySnapshotsWithoutCountsButRejectsMismatchingDeclaredCounts() {
        assertTrue(ServerSnapshotValidation.hasOptionalMatchingCount(null, 4))
        assertTrue(ServerSnapshotValidation.hasOptionalMatchingCount(0, 0))
        assertTrue(ServerSnapshotValidation.hasOptionalMatchingCount(4, 4))
        assertFalse(ServerSnapshotValidation.hasOptionalMatchingCount(5, 4))
        assertFalse(ServerSnapshotValidation.hasOptionalMatchingCount(-1, 0))
    }

    @Test
    fun rejectsBlankAndDuplicateRecordIdsBeforeRoomReconciliation() {
        assertTrue(ServerSnapshotValidation.hasUniqueNonBlankIds(emptyList()))
        assertTrue(ServerSnapshotValidation.hasUniqueNonBlankIds(listOf("a", "b")))
        assertFalse(ServerSnapshotValidation.hasUniqueNonBlankIds(listOf("a", "")))
        assertFalse(ServerSnapshotValidation.hasUniqueNonBlankIds(listOf("a", "a")))
    }

    @Test
    fun validatesMonthScopedRecordsBeforeReplacingMonthlySnapshots() {
        assertTrue(ServerSnapshotValidation.isValidYearMonth("2026-11"))
        assertFalse(ServerSnapshotValidation.isValidYearMonth("2026-13"))
        assertFalse(ServerSnapshotValidation.isValidYearMonth("2026/11"))

        assertTrue(ServerSnapshotValidation.belongsToMonth("2026-11-01", "2026-11"))
        assertFalse(ServerSnapshotValidation.belongsToMonth("2026-12-01", "2026-11"))
        assertFalse(ServerSnapshotValidation.belongsToMonth("2026-11-xx", "2026-11"))
        assertFalse(ServerSnapshotValidation.belongsToMonth("2026-11-01", "2026-13"))

        assertTrue(ServerSnapshotValidation.allBelongToMonth(emptyList(), "2026-11"))
        assertFalse(ServerSnapshotValidation.allBelongToMonth(listOf("2026-11-01", "2026-12-01"), "2026-11"))
    }

    @Test
    fun validatesGroupScopesAndAllowsOnlyExplicitGlobalEvents() {
        assertTrue(ServerSnapshotValidation.allBelongToGroup(emptyList(), "group_a"))
        assertTrue(ServerSnapshotValidation.allBelongToGroup(listOf("group_a"), "group_a"))
        assertFalse(ServerSnapshotValidation.allBelongToGroup(listOf("group_b"), "group_a"))
        assertFalse(ServerSnapshotValidation.allBelongToGroup(listOf("all"), "group_a"))
        assertTrue(ServerSnapshotValidation.allBelongToGroup(listOf("group_a", "all"), "group_a", allowGlobalGroup = true))
    }
}
