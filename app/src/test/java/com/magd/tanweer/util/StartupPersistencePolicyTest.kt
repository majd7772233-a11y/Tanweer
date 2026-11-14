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
    fun explicitGenerationChangeRequiresLocalAccountReset() {
        assertFalse(DataGenerationPolicy.hasChanged("gen_initial_1", "gen_initial_1"))
        assertTrue(DataGenerationPolicy.hasChanged("gen_initial_1", "gen_initial_2"))
        assertFalse(DataGenerationPolicy.hasChanged("gen_initial_1", null))
        assertFalse(DataGenerationPolicy.hasChanged(null, "gen_initial_2"))
        assertFalse(DataGenerationPolicy.hasChanged(" ", "gen_initial_2"))
    }

    @Test
    fun localResetRemovesAccountStateButPreservesAppPreferences() {
        val keys = setOf(
            "auth_token", "last_open_date", "last_open_date_user_1",
            "selected_group_id_user_1", "selected_study_date_user_1",
            "last_active_time", "previous_session_time",
            "data_generation|https://tanweer-staging.magd.workers.dev|user_1",
            "app_theme", "font_size_scale", "auto_sync_enabled",
            "notify_homework", "haptics_enabled"
        )
        assertEquals(
            setOf(
                "auth_token", "last_open_date", "last_open_date_user_1",
                "selected_group_id_user_1", "selected_study_date_user_1",
                "last_active_time", "previous_session_time"
            ),
            LocalAccountDataPolicy.keysToRemove(keys)
        )
    }
}
