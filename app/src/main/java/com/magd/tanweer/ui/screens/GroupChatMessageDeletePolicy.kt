package com.magd.tanweer.ui.screens

import com.magd.tanweer.data.model.ChatMessageItem
import com.magd.tanweer.data.model.GroupItem
import com.magd.tanweer.data.model.Permission
import com.magd.tanweer.data.model.PermissionEvaluator
import com.magd.tanweer.data.model.User

/**
 * Client-side visibility hint only. The Worker remains the authority for deletion.
 * Owners may remove their own persisted message; moderation is evaluated separately.
 */
object GroupChatMessageDeletePolicy {
    fun canDelete(
        message: ChatMessageItem,
        user: User?,
        group: GroupItem?
    ): Boolean {
        if (user == null || group == null) return false
        if (user.id.isBlank() || group.id.isBlank() || message.id.isBlank() || message.senderId.isBlank()) return false
        if (message.groupId != group.id || !message.status.equals("SENT", ignoreCase = true)) return false

        val isOwner = message.senderId == user.id
        val isSystemOwner = user.role.equals("SYSTEM_OWNER", ignoreCase = true)
        val canModerate = isSystemOwner || PermissionEvaluator.hasPermission(
            user = user,
            permission = Permission.MODERATE_CHAT,
            groupRole = group.role
        )
        return isOwner || canModerate
    }
}
