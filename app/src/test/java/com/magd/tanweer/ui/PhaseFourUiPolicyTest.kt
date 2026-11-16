package com.magd.tanweer.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.magd.tanweer.data.model.SyncState
import com.magd.tanweer.ui.components.aggregateSyncStates
import com.magd.tanweer.ui.components.hasAuthoritativeSnapshot
import com.magd.tanweer.ui.components.shouldShowConfirmedEmptyState
import com.magd.tanweer.ui.components.shouldShowConfirmedGradeEmptyState
import com.magd.tanweer.ui.components.shouldAllowEmptyScheduleSlotEditing
import com.magd.tanweer.util.ArabicSearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhaseFourUiPolicyTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ArabicSearchEngine.clearRecentSearches(context)
    }

    @Test
    fun search_history_stores_normalized_unique_entries_newest_first() {
        ArabicSearchEngine.saveRecentSearch(context, "  الفيزياء  ")
        ArabicSearchEngine.saveRecentSearch(context, "الكيمياء")
        ArabicSearchEngine.saveRecentSearch(context, "الفيزياء")

        assertEquals(listOf("الفيزياء", "الكيمياء"), ArabicSearchEngine.getRecentSearches(context))
    }

    @Test
    fun search_history_ignores_short_queries_and_remains_bounded_to_eight_entries() {
        ArabicSearchEngine.saveRecentSearch(context, "a")
        ArabicSearchEngine.saveRecentSearch(context, " ")

        for (index in 1..10) {
            ArabicSearchEngine.saveRecentSearch(context, "query $index")
        }

        val recent = ArabicSearchEngine.getRecentSearches(context)
        assertEquals(8, recent.size)
        assertEquals("query 10", recent.first())
        assertTrue(recent.none { it == "query 1" || it == "query 2" })
    }

    @Test
    fun aggregate_sync_state_never_hides_a_source_error_behind_loading() {
        val error = SyncState.Error("تعذر تحديث الواجبات")
        val resolved = aggregateSyncStates(listOf(SyncState.Loading, SyncState.Success(10L), error))

        assertSame(error, resolved)
    }

    @Test
    fun aggregate_sync_state_reports_cached_data_when_a_source_is_offline() {
        val offline = SyncState.OfflineCached(lastSyncedAt = 20L)
        val resolved = aggregateSyncStates(listOf(SyncState.Success(30L), offline))

        assertSame(offline, resolved)
    }

    @Test
    fun aggregate_sync_state_reports_success_only_when_every_source_succeeds() {
        val resolved = aggregateSyncStates(listOf(SyncState.Success(10L), SyncState.Success(25L)))

        assertEquals(SyncState.Success(10L), resolved)
    }

    @Test
    fun confirmed_empty_state_requires_a_successful_snapshot() {
        assertTrue(shouldShowConfirmedEmptyState(hasSnapshot = true, isLoading = false, errorMessage = null))
        assertFalse(shouldShowConfirmedEmptyState(hasSnapshot = false, isLoading = false, errorMessage = null))
        assertFalse(shouldShowConfirmedEmptyState(hasSnapshot = true, isLoading = true, errorMessage = null))
        assertFalse(shouldShowConfirmedEmptyState(hasSnapshot = true, isLoading = false, errorMessage = "network error"))
    }

    @Test
    fun dashboard_response_is_accepted_only_for_the_active_account_role_and_generation() {
        assertTrue(
            isCurrentDashboardRequest(
                requestGeneration = 7,
                activeGeneration = 7,
                requestedUserId = "user-a",
                currentUserId = "user-a",
                requestedRole = "TEACHER",
                currentRole = "teacher"
            )
        )
        assertFalse(
            isCurrentDashboardRequest(7, 8, "user-a", "user-a", "TEACHER", "TEACHER")
        )
        assertFalse(
            isCurrentDashboardRequest(7, 7, "user-a", "user-b", "TEACHER", "TEACHER")
        )
        assertFalse(
            isCurrentDashboardRequest(7, 7, "user-a", "user-a", "TEACHER", "ADMIN")
        )
    }

    @Test
    fun only_a_successful_server_snapshot_can_confirm_an_empty_list() {
        assertTrue(hasAuthoritativeSnapshot(SyncState.Success(50L)))
        assertFalse(hasAuthoritativeSnapshot(SyncState.Idle))
        assertFalse(hasAuthoritativeSnapshot(SyncState.Loading))
        assertFalse(hasAuthoritativeSnapshot(SyncState.Refreshing))
        assertFalse(hasAuthoritativeSnapshot(SyncState.OfflineCached(lastSyncedAt = 50L)))
        assertFalse(hasAuthoritativeSnapshot(SyncState.Error("تعذر الاتصال")))
    }


    @Test
    fun empty_schedule_editing_requires_staff_authority_initial_setup_or_a_confirmed_snapshot() {
        assertFalse(shouldAllowEmptyScheduleSlotEditing(false, false, false))
        assertTrue(shouldAllowEmptyScheduleSlotEditing(false, true, false))
        assertTrue(shouldAllowEmptyScheduleSlotEditing(false, false, true))
        assertTrue(shouldAllowEmptyScheduleSlotEditing(true, false, false))
    }

    @Test
    fun empty_library_state_is_confirmed_only_for_the_same_grade_and_successful_sync() {
        assertTrue(shouldShowConfirmedGradeEmptyState(10, 10, 0, SyncState.Success(100L)))
        assertFalse(shouldShowConfirmedGradeEmptyState(11, 10, 0, SyncState.Success(100L)))
        assertFalse(shouldShowConfirmedGradeEmptyState(10, 10, 0, SyncState.OfflineCached(lastSyncedAt = 100L)))
        assertFalse(shouldShowConfirmedGradeEmptyState(10, 10, 2, SyncState.Success(100L)))
    }

}
