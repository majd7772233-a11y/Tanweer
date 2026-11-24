package com.magd.tanweer.ui.screens

object HomeworkOwnershipPolicy {
    fun canManage(
        createdBy: String?,
        syncStatus: String?,
        currentUserId: String?
    ): Boolean {
        if (!syncStatus.equals("SYNCED", ignoreCase = true)) return false
        val ownerId = createdBy?.trim().orEmpty()
        val userId = currentUserId?.trim().orEmpty()
        return ownerId.isNotEmpty() && userId.isNotEmpty() && ownerId == userId
    }
}
