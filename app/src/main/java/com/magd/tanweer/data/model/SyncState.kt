package com.magd.tanweer.data.model

sealed class SyncState {
    object Idle : SyncState()
    object Loading : SyncState()
    object Refreshing : SyncState()
    data class Success(val lastSyncedAt: Long = System.currentTimeMillis()) : SyncState()
    data class OfflineCached(
        val lastSyncedAt: Long = System.currentTimeMillis(),
        val message: String = "⚡ تعمل الآن من آخر بيانات محفوظة (أوفلاين)"
    ) : SyncState()
    data class Error(
        val message: String,
        val isOffline: Boolean = false,
        val lastSyncedAt: Long? = null
    ) : SyncState()

    fun isBusy(): Boolean = this is Loading || this is Refreshing
}
