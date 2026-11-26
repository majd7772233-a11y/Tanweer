package com.magd.tanweer.ui.components

import com.magd.tanweer.data.model.SyncState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStatusBarVisibilityPolicyTest {
    @Test
    fun initialBackgroundAndSuccessfulStatesDoNotKeepTheBannerVisible() {
        assertFalse(shouldShowSyncStatusBar(SyncState.Idle))
        assertFalse(shouldShowSyncStatusBar(SyncState.Loading))
        assertFalse(shouldShowSyncStatusBar(SyncState.Success(lastSyncedAt = 100L)))
    }

    @Test
    fun explicitRefreshShowsOnlyWhileRefreshIsInProgress() {
        assertTrue(shouldShowSyncStatusBar(SyncState.Refreshing))
        assertFalse(shouldShowSyncStatusBar(SyncState.Success(lastSyncedAt = 200L)))
    }

    @Test
    fun offlineAndErrorStatesRemainVisibleForRecovery() {
        assertTrue(shouldShowSyncStatusBar(SyncState.OfflineCached(lastSyncedAt = 100L)))
        assertTrue(shouldShowSyncStatusBar(SyncState.Error(message = "Network unavailable")))
    }
}
