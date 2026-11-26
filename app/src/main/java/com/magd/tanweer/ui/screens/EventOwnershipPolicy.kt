package com.magd.tanweer.ui.screens

import com.magd.tanweer.data.model.SchoolEventItem

/**
 * Conservative client-side visibility policy for event-management actions.
 * The Worker remains the authority and re-checks the creator/role on every write.
 */
internal object EventOwnershipPolicy {
    private val privilegedRoles = setOf("ADMIN", "SYSTEM_OWNER", "TEACHER", "VERIFIED_TEACHER")
    private val schoolAdminRoles = setOf("ADMIN", "SYSTEM_OWNER")

    fun canEdit(event: SchoolEventItem, currentUserId: String, currentUserRole: String): Boolean {
        if (!isConfirmedOwner(event, currentUserId)) return false

        val role = currentUserRole.trim().uppercase()
        val isSchoolWideAnnouncement = event.groupId == "all" &&
            event.category.equals("ANNOUNCEMENT", ignoreCase = true)
        if (isSchoolWideAnnouncement && role !in schoolAdminRoles) return false

        // The Worker treats holidays as official decisions and requires the corresponding
        // publication privilege even when the caller created the original row.
        if (event.category.equals("HOLIDAY", ignoreCase = true) && role !in privilegedRoles) return false

        return true
    }

    fun canDelete(event: SchoolEventItem, currentUserId: String, currentUserRole: String): Boolean {
        if (!isConfirmedOwner(event, currentUserId)) return false

        val isSchoolWideAnnouncement = event.groupId == "all" &&
            event.category.equals("ANNOUNCEMENT", ignoreCase = true)
        return !isSchoolWideAnnouncement || currentUserRole.trim().uppercase() in schoolAdminRoles
    }

    private fun isConfirmedOwner(event: SchoolEventItem, currentUserId: String): Boolean =
        currentUserId.isNotBlank() &&
            event.createdBy.isNotBlank() &&
            event.createdBy == currentUserId &&
            event.syncStatus.equals("SYNCED", ignoreCase = true)
}