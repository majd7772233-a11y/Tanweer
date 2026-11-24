package com.magd.tanweer.util

import com.magd.tanweer.data.model.AppNotification
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationInboxPolicyTest {
    private fun notification(
        id: String,
        createdAt: Long = 0,
        readAt: Long? = null,
        type: String = "GROUP_JOIN_ACCEPTED",
        archivedAt: Long? = null
    ) = AppNotification(
        id = id,
        type = type,
        title = id,
        body = "body",
        createdAt = createdAt,
        readAt = readAt,
        archivedAt = archivedAt
    )

    @Test
    fun unreadAndReadFiltersAreBasedOnServerReadTimestamp() {
        val items = listOf(notification("unread"), notification("read", readAt = 10))
        assertEquals(listOf("unread"), NotificationInboxPolicy.filter(items, NotificationInboxFilter.UNREAD).map { it.id })
        assertEquals(listOf("read"), NotificationInboxPolicy.filter(items, NotificationInboxFilter.READ).map { it.id })
        assertEquals(2, NotificationInboxPolicy.filter(items, NotificationInboxFilter.ALL).size)
    }

    @Test
    fun recoveryCodeDeliveryNeverAppearsInTheGeneralInboxOrUnreadCount() {
        val items = listOf(
            notification("group", createdAt = 2),
            notification("recovery", createdAt = 3, type = "RECOVERY_CODE_RESET")
        )
        assertEquals(listOf("group"), NotificationInboxPolicy.filter(items, NotificationInboxFilter.ALL).map { it.id })
        assertEquals(1, NotificationInboxPolicy.unreadCount(items))
    }

    @Test
    fun archivedNotificationsDoNotContributeToTheInboxUnreadBadge() {
        val items = listOf(
            notification("active", createdAt = 3),
            notification("archived", createdAt = 2, archivedAt = 100),
            notification("read", createdAt = 1, readAt = 20)
        )
        assertEquals(1, NotificationInboxPolicy.unreadCount(items))
    }

    @Test
    fun newestNotificationsAppearFirst() {
        val items = listOf(
            notification("old", createdAt = 1),
            notification("new", createdAt = 3),
            notification("middle", createdAt = 2)
        )
        assertEquals(listOf("new", "middle", "old"), NotificationInboxPolicy.filter(items, NotificationInboxFilter.ALL).map { it.id })
    }
}
