package com.magd.tanweer.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.magd.tanweer.data.model.SyncState
import com.magd.tanweer.ui.components.aggregateSyncStates
import com.magd.tanweer.ui.components.shouldShowConfirmedEmptyState
import com.magd.tanweer.util.ArabicSearchEngine
import org.junit.Assert.assertEquals
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

}
