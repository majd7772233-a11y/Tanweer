package com.magd.tanweer.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeworkOwnershipPolicyTest {
    @Test
    fun syncedHomeworkActionsAreVisibleToItsCreator() {
        assertTrue(HomeworkOwnershipPolicy.canManage("student_1", "SYNCED", "student_1"))
    }

    @Test
    fun anotherStudentCannotManageTheHomework() {
        assertFalse(HomeworkOwnershipPolicy.canManage("student_1", "SYNCED", "student_2"))
    }

    @Test
    fun legacyHomeworkWithoutCreatorFailsClosed() {
        assertFalse(HomeworkOwnershipPolicy.canManage("", "SYNCED", "student_1"))
    }

    @Test
    fun blankCurrentUserFailsClosed() {
        assertFalse(HomeworkOwnershipPolicy.canManage("student_1", "SYNCED", " "))
    }

    @Test
    fun unconfirmedHomeworkCannotBeEditedOrDeleted() {
        listOf("PENDING", "SYNCING", "FAILED", "DELETE_PENDING", "LOCAL").forEach { status ->
            assertFalse(HomeworkOwnershipPolicy.canManage("student_1", status, "student_1"))
        }
    }
}
