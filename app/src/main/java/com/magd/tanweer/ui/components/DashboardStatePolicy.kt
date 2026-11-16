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
