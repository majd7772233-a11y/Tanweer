package com.magd.tanweer.ui.screens

object HomeworkOwnershipPolicy {
    private fun isConfirmed(syncStatus: String?): Boolean =
        syncStatus.equals("SYNCED", ignoreCase = true)

    private fun isOwner(createdBy: String?, currentUserId: String?): Boolean {
        val ownerId = createdBy?.trim().orEmpty()
        val userId = currentUserId?.trim().orEmpty()
        return ownerId.isNotEmpty() && userId.isNotEmpty() && ownerId == userId
    }

    fun canEdit(
        createdBy: String?,
        syncStatus: String?,
        currentUserId: String?,
        canEditOthers: Boolean = false
    ): Boolean = isConfirmed(syncStatus) && (isOwner(createdBy, currentUserId) || canEditOthers)

    fun canDelete(
        createdBy: String?,
        syncStatus: String?,
        currentUserId: String?,
        canDeleteOthers: Boolean = false
    ): Boolean = isConfirmed(syncStatus) && (isOwner(createdBy, currentUserId) || canDeleteOthers)
}
