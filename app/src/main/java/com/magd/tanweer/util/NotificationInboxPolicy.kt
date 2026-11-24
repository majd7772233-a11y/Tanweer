package com.magd.tanweer.util

import com.magd.tanweer.data.model.AppNotification

enum class NotificationInboxFilter {
    ALL,
    UNREAD,
    READ
}

/** UI filtering rules for the account inbox; secret recovery delivery stays in Profile. */
object NotificationInboxPolicy {
    fun filter(
        notifications: List<AppNotification>,
        filter: NotificationInboxFilter
    ): List<AppNotification> {
        return notifications
            .asSequence()
            .filterNot { it.type == "RECOVERY_CODE_RESET" }
            .filter { notification ->
                when (filter) {
                    NotificationInboxFilter.ALL -> true
                    NotificationInboxFilter.UNREAD -> notification.readAt == null
                    NotificationInboxFilter.READ -> notification.readAt != null
                }
            }
            .sortedByDescending { it.createdAt }
            .toList()
    }

    fun unreadCount(notifications: List<AppNotification>): Int =
        notifications.count {
            it.type != "RECOVERY_CODE_RESET" &&
                it.archivedAt == null &&
                it.readAt == null
        }
}
