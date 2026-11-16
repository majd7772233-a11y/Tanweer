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
