package com.magd.tanweer.ui.components

/**
 * An empty-state message is truthful only after a successful snapshot has been received.
 */
fun shouldShowConfirmedEmptyState(
    hasSnapshot: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
): Boolean = hasSnapshot && !isLoading && errorMessage.isNullOrBlank()
