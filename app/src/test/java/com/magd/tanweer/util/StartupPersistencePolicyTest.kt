package com.magd.tanweer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupPersistencePolicyTest {

    private data class SnapshotRecord(val id: String, val value: String)

    @Test
    fun pendingLocalMutationWinsWhenServerReturnsSameId() {
        val merged = ServerSnapshotReconciliation.preservePending(
            serverRows = listOf(SnapshotRecord("item-1", "server-value")),
            localPendingRows = listOf(SnapshotRecord("item-1", "local-pending-value"))
        ) { it.id }

        assertEquals(listOf(SnapshotRecord("item-1", "local-pending-value")), merged)
    }

    @Test
    fun pendingLocalMutationSurvivesWhenMissingFromServerSnapshot() {
        val merged = ServerSnapshotReconciliation.preservePending(
            serverRows = listOf(SnapshotRecord("server-only", "server-value")),
            localPendingRows = listOf(SnapshotRecord("local-pending", "unsent-value"))
        ) { it.id }

        assertEquals(
            listOf(
                SnapshotRecord("server-only", "server-value"),
                SnapshotRecord("local-pending", "unsent-value")
            ),
            merged
        )
    }

    @Test
    fun serverSnapshotReplacesRowsWithoutPendingLocalMutations() {
        val merged = ServerSnapshotReconciliation.preservePending(
            serverRows = listOf(SnapshotRecord("item-1", "fresh-server-value")),
            localPendingRows = emptyList()
        ) { it.id }

        assertEquals(listOf(SnapshotRecord("item-1", "fresh-server-value")), merged)
    }

    @Test
    fun savedGroupWinsOverDefaultGroup() {
        assertEquals(
            "class_10_C",
            AcademicSessionSelection.resolveGroupId("class_10_C", "class_10_B")
        )
    }

    @Test
    fun blankSavedGroupFallsBackToAccountDefault() {
        assertEquals(
            "class_10_B",
            AcademicSessionSelection.resolveGroupId(" ", "class_10_B")
        )
    }

    @Test
    fun missingGroupValuesResolveSafely() {
        assertEquals("", AcademicSessionSelection.resolveGroupId(null, null))
    }

    @Test
    fun savedGroupIsKeptOnlyWhenServerConfirmsMembership() {
        assertEquals(
            "class_10_C",
            AcademicSessionSelection.resolveAuthorizedGroupId(
                "class_10_C",
                "class_10_B",
                listOf("class_10_B", "class_10_C")
            )
        )
    }

    @Test
    fun removedSavedGroupFallsBackToAuthorizedDefault() {
        assertEquals(
            "class_10_B",
            AcademicSessionSelection.resolveAuthorizedGroupId(
                "class_10_C",
                "class_10_B",
                listOf("class_10_B")
            )
        )
    }

    @Test
    fun unavailableDefaultFallsBackToFirstActiveMembership() {
        assertEquals(
            "class_10_A",
            AcademicSessionSelection.resolveAuthorizedGroupId(
                "class_10_C",
                "class_10_B",
                listOf("class_10_A", "class_10_D")
            )
        )
    }

    @Test
    fun noActiveMembershipResolvesToEmptyGroup() {
        assertEquals(
            "",
            AcademicSessionSelection.resolveAuthorizedGroupId(
                "class_10_C",
                "class_10_B",
                emptyList()
            )
        )
    }

    @Test
    fun validSavedStudyDateSurvivesRestart() {
        assertEquals(
            "2026-10-31",
            AcademicSessionSelection.resolveStudyDate("2026-10-31", "2026-11-02")
        )
    }

    @Test
    fun invalidSavedStudyDateFallsBackToToday() {
        assertEquals(
            "2026-11-02",
            AcademicSessionSelection.resolveStudyDate("2026-02-31", "2026-11-02")
        )
    }

    @Test
    fun missingGenerationMarkerDoesNotPretendAChangeOccurred() {
        assertFalse(DataGenerationPolicy.hasChanged(null, "gen_initial_1"))
        assertFalse(DataGenerationPolicy.hasChanged("", "gen_initial_1"))
    }

    @Test
    fun explicitGenerationChangeIsDetectedOnlyAsARefreshSignal() {
        assertFalse(DataGenerationPolicy.hasChanged("gen_initial_1", "gen_initial_1"))
        assertTrue(DataGenerationPolicy.hasChanged("gen_initial_1", "gen_initial_2"))
        assertFalse(DataGenerationPolicy.hasChanged("gen_initial_1", null))
    }
}
