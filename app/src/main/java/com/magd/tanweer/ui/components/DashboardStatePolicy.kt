package com.magd.tanweer.ui.components

import com.magd.tanweer.data.model.SyncState

/**
 * An empty-state message is truthful only after a successful snapshot has been received.
 */
fun shouldShowConfirmedEmptyState(
    hasSnapshot: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
): Boolean = hasSnapshot && !isLoading && errorMessage.isNullOrBlank()

/** Empty-list claims require a successful, validated server snapshot. */
fun hasAuthoritativeSnapshot(syncState: SyncState): Boolean =
    syncState is SyncState.Success


/** An empty catalogue is confirmed only for the exact grade whose sync succeeded. */
fun shouldShowConfirmedGradeEmptyState(
    selectedGradeId: Int,
    syncedGradeId: Int?,
    bookCount: Int,
    syncState: SyncState,
): Boolean = bookCount == 0 &&
    selectedGradeId == syncedGradeId &&
    hasAuthoritativeSnapshot(syncState)

/**
 * Empty schedule cells can be edited by staff, during an explicitly server-confirmed
 * initial setup, or after a schedule snapshot has been received. A local Flow's initial
 * emptyList() is not permission evidence.
 */
fun shouldAllowEmptyScheduleSlotEditing(
    isStaffWithDirectEdit: Boolean,
    serverConfirmsInitialSetup: Boolean,
    hasScheduleSnapshot: Boolean,
): Boolean = isStaffWithDirectEdit || serverConfirmsInitialSetup || hasScheduleSnapshot


/**
 * A successful sync from a different group must never authorize an empty state for
 * the currently selected group.
 */
fun syncStateForScope(
    syncState: SyncState,
    syncedScopeId: String?,
    requestedScopeId: String
): SyncState = if (requestedScopeId.isNotBlank() && syncedScopeId == requestedScopeId) {
    syncState
} else {
    SyncState.Idle
}

/** A server snapshot is usable only for the group currently displayed by the screen. */
fun isSnapshotForScope(snapshotScopeId: String?, requestedScopeId: String): Boolean =
    requestedScopeId.isNotBlank() && snapshotScopeId == requestedScopeId

/** A grade-specific empty result needs a successful snapshot for that exact grade. */
fun hasAuthoritativeGradeSnapshot(
    selectedGradeId: Int?,
    syncedGradeId: Int?,
    syncState: SyncState
): Boolean = selectedGradeId != null &&
    selectedGradeId == syncedGradeId &&
    hasAuthoritativeSnapshot(syncState)

/** Hide retained rows until the current requested group has its own confirmed snapshot. */
fun <T> itemsForSnapshotScope(
    items: List<T>,
    snapshotScopeId: String?,
    requestedScopeId: String,
): List<T> = if (isSnapshotForScope(snapshotScopeId, requestedScopeId)) items else emptyList()


/** A selected-day snapshot is valid only for the exact group and date being displayed. */
fun syncStateForDayScope(
    syncState: SyncState,
    snapshotGroupId: String?,
    snapshotDate: String?,
    requestedGroupId: String,
    requestedDate: String,
): SyncState = if (
    requestedGroupId.isNotBlank() &&
    requestedDate.isNotBlank() &&
    snapshotGroupId == requestedGroupId &&
    snapshotDate == requestedDate
) syncState else SyncState.Idle

/** An empty calendar day needs both a validated month and a validated day-detail snapshot. */
fun shouldShowConfirmedCalendarEmpty(
    hasMonthlySnapshot: Boolean,
    hasDayDetailsSnapshot: Boolean,
): Boolean = hasMonthlySnapshot && hasDayDetailsSnapshot
