package com.magd.tanweer.ui.screens

import com.magd.tanweer.data.model.ChatMessageItem
import com.magd.tanweer.data.model.GroupItem
import com.magd.tanweer.data.model.User
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupChatMessageDeletePolicyTest {
    private val group = GroupItem(id = "group-1", name = "Class", type = "CLASS")

    private fun user(id: String, role: String = "STUDENT") = User(
        id = id,
        fullName = "Test user",
        phoneNumber = "777000000",
        role = role
    )

    private fun message(
        senderId: String = "owner-1",
        groupId: String = "group-1",
        status: String = "SENT"
    ) = ChatMessageItem(
        id = "message-1",
        groupId = groupId,
        senderId = senderId,
        senderName = "Sender",
        senderGradeSection = "Grade 10 - A",
        text = "A group message",
        status = status
    )

    @Test
    fun exactOwnerCanDeleteTheirOwnPersistedMessage() {
        assertTrue(GroupChatMessageDeletePolicy.canDelete(message(), user("owner-1"), group))
    }

    @Test
    fun anotherStudentCannotDeleteSomeoneElsesMessage() {
        assertFalse(GroupChatMessageDeletePolicy.canDelete(message(), user("student-2"), group))
    }

    @Test
    fun activeGroupModeratorCanModerateAnotherMembersMessage() {
        val moderatorGroup = group.copy(role = "MODERATOR")
        assertTrue(GroupChatMessageDeletePolicy.canDelete(message(), user("moderator-1"), moderatorGroup))
    }

    @Test
    fun globalTeacherCanModerateAnotherMembersMessage() {
        assertTrue(GroupChatMessageDeletePolicy.canDelete(message(), user("teacher-1", "TEACHER"), group))
    }

    @Test
    fun globalAdminCanModerateAnotherMembersMessage() {
        assertTrue(GroupChatMessageDeletePolicy.canDelete(message(), user("admin-1", "ADMIN"), group))
    }

    @Test
    fun systemOwnerCanModerateAnotherMembersMessage() {
        assertTrue(GroupChatMessageDeletePolicy.canDelete(message(), user("owner-admin", "SYSTEM_OWNER"), group))
    }

    @Test
    fun messageFromAnotherGroupCannotBeDeletedFromCurrentChat() {
        assertFalse(GroupChatMessageDeletePolicy.canDelete(message(groupId = "group-2"), user("owner-1"), group))
    }

    @Test
    fun pendingOrFailedMessageCannotBeDeleted() {
        assertFalse(GroupChatMessageDeletePolicy.canDelete(message(status = "SENDING"), user("owner-1"), group))
        assertFalse(GroupChatMessageDeletePolicy.canDelete(message(status = "FAILED"), user("owner-1"), group))
    }

    @Test
    fun missingIdentityOrGroupFailsClosed() {
        assertFalse(GroupChatMessageDeletePolicy.canDelete(message(), null, group))
        assertFalse(GroupChatMessageDeletePolicy.canDelete(message(), user("owner-1"), null))
    }
}
