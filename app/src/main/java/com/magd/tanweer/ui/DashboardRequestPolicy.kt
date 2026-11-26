package com.magd.tanweer.ui

/**
 * Prevents dashboard requests started for a previous account, role, or session
 * generation from publishing results into the current signed-in state.
 */
internal fun isCurrentDashboardRequest(
    requestGeneration: Int,
    activeGeneration: Int,
    requestedUserId: String,
    currentUserId: String?,
    requestedRole: String,
    currentRole: String?,
): Boolean = requestGeneration == activeGeneration &&
    !currentUserId.isNullOrBlank() &&
    requestedUserId == currentUserId &&
    !currentRole.isNullOrBlank() &&
    requestedRole.equals(currentRole, ignoreCase = true)
