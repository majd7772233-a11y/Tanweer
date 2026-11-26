package com.magd.tanweer.ui.screens

object ExamOwnershipPolicy {
    fun canEdit(
        createdBy: String?,
        syncStatus: String?,
        currentUserId: String?,
        canModerate: Boolean = false
    ): Boolean {
        if (!syncStatus.equals("SYNCED", ignoreCase = true)) return false
        val ownerId = createdBy?.trim().orEmpty()
        val userId = currentUserId?.trim().orEmpty()
        val isCreator = ownerId.isNotEmpty() && userId.isNotEmpty() && ownerId == userId
        return isCreator || canModerate
    }
}