package com.magd.tanweer.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeworkOwnershipPolicyTest {
    @Test
    fun syncedHomeworkOwnerCanEditAndDelete() {
        assertTrue(HomeworkOwnershipPolicy.canEdit("student_1", "SYNCED", "student_1"))
        assertTrue(HomeworkOwnershipPolicy.canDelete("student_1", "SYNCED", "student_1"))
    }

    @Test
    fun anotherStudentCannotEditOrDelete() {
        assertFalse(HomeworkOwnershipPolicy.canEdit("student_1", "SYNCED", "student_2"))
        assertFalse(HomeworkOwnershipPolicy.canDelete("student_1", "SYNCED", "student_2"))
    }

    @Test
    fun editPermissionDoesNotImplicitlyGrantDeletePermission() {
        assertTrue(
            HomeworkOwnershipPolicy.canEdit(
                createdBy = "student_1",
                syncStatus = "SYNCED",
                currentUserId = "teacher_1",
                canEditOthers = true
            )
        )
        assertFalse(
            HomeworkOwnershipPolicy.canDelete(
                createdBy = "student_1",
                syncStatus = "SYNCED",
                currentUserId = "teacher_1"
            )
        )
    }

    @Test
    fun deletePermissionDoesNotImplicitlyGrantEditPermission() {
        assertFalse(
            HomeworkOwnershipPolicy.canEdit(
                createdBy = "student_1",
                syncStatus = "SYNCED",
                currentUserId = "admin_1"
            )
        )
        assertTrue(
            HomeworkOwnershipPolicy.canDelete(
                createdBy = "student_1",
                syncStatus = "SYNCED",
                currentUserId = "admin_1",
                canDeleteOthers = true
            )
        )
    }

    @Test
    fun legacyHomeworkWithoutCreatorFailsClosed() {
        assertFalse(HomeworkOwnershipPolicy.canEdit("", "SYNCED", "student_1"))
        assertFalse(HomeworkOwnershipPolicy.canDelete("", "SYNCED", "student_1"))
    }

    @Test
    fun blankCurrentUserFailsClosed() {
        assertFalse(HomeworkOwnershipPolicy.canEdit("student_1", "SYNCED", " "))
        assertFalse(HomeworkOwnershipPolicy.canDelete("student_1", "SYNCED", " "))
    }

    @Test
    fun unconfirmedHomeworkCannotBeEditedOrDeletedEvenWithRolePermission() {
        listOf("PENDING", "SYNCING", "FAILED", "DELETE_PENDING", "LOCAL").forEach { status ->
            assertFalse(HomeworkOwnershipPolicy.canEdit("student_1", status, "student_1", true))
            assertFalse(HomeworkOwnershipPolicy.canDelete("student_1", status, "student_1", true))
        }
    }
}
