package com.magd.tanweer.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamOwnershipPolicyTest {
    @Test
    fun creatorCanEditConfirmedExam() {
        assertTrue(ExamOwnershipPolicy.canEdit("student-1", "SYNCED", "student-1"))
    }

    @Test
    fun anotherStudentCannotEditExam() {
        assertFalse(ExamOwnershipPolicy.canEdit("student-1", "SYNCED", "student-2"))
    }

    @Test
    fun legacyExamWithoutCreatorCannotBeEditedByStudent() {
        assertFalse(ExamOwnershipPolicy.canEdit("", "SYNCED", "student-1"))
    }

    @Test
    fun pendingExamCannotBeEditedBeforeServerConfirmation() {
        assertFalse(ExamOwnershipPolicy.canEdit("student-1", "SYNCING", "student-1"))
    }

    @Test
    fun blankCurrentUserCannotEditExam() {
        assertFalse(ExamOwnershipPolicy.canEdit("student-1", "SYNCED", ""))
    }

    @Test
    fun examModeratorCanEditConfirmedLegacyExamWithoutCreatorId() {
        assertTrue(ExamOwnershipPolicy.canEdit("", "SYNCED", "teacher-1", canModerate = true))
    }

    @Test
    fun moderatorStillCannotEditPendingExam() {
        assertFalse(ExamOwnershipPolicy.canEdit("", "SYNCING", "teacher-1", canModerate = true))
    }
}