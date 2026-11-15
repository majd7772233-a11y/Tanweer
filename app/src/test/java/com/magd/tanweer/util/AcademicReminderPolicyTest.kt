package com.magd.tanweer.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicReminderPolicyTest {
    @Test
    fun `empty snapshots can clear alerts only when their source is trusted`() {
        assertTrue(AcademicReminderPolicy.mayDismissEmptySnapshot(false, false))
        assertTrue(AcademicReminderPolicy.mayDismissEmptySnapshot(true, true))
        assertFalse(AcademicReminderPolicy.mayDismissEmptySnapshot(true, false))
    }

    @Test
    fun `homework reminder requires an active incomplete item and a valid current due date`() {
        assertTrue(AcademicReminderPolicy.shouldShowHomework(true, true, false, true, "2026-11-16", "2026-11-15"))
        assertFalse(AcademicReminderPolicy.shouldShowHomework(true, true, true, true, "2026-11-16", "2026-11-15"))
        assertFalse(AcademicReminderPolicy.shouldShowHomework(true, true, false, false, "2026-11-16", "2026-11-15"))
        assertFalse(AcademicReminderPolicy.shouldShowHomework(true, false, false, true, "2026-11-16", "2026-11-15"))
        assertFalse(AcademicReminderPolicy.shouldShowHomework(true, true, false, true, "2026-11-14", "2026-11-15"))
        assertFalse(AcademicReminderPolicy.shouldShowHomework(true, true, false, true, "next-week", "2026-11-15"))
        assertFalse(AcademicReminderPolicy.shouldShowHomework(false, true, false, true, "2026-11-16", "2026-11-15"))
    }

    @Test
    fun `exam reminder requires an active incomplete upcoming exam`() {
        assertTrue(AcademicReminderPolicy.shouldShowExam(true, true, false, true, "2026-11-16", "2026-11-15"))
        assertFalse(AcademicReminderPolicy.shouldShowExam(true, true, true, true, "2026-11-16", "2026-11-15"))
        assertFalse(AcademicReminderPolicy.shouldShowExam(true, true, false, true, "2026-11-14", "2026-11-15"))
        assertFalse(AcademicReminderPolicy.shouldShowExam(true, false, false, true, "2026-11-16", "2026-11-15"))
        assertFalse(AcademicReminderPolicy.shouldShowExam(true, true, false, false, "2026-11-16", "2026-11-15"))
        assertFalse(AcademicReminderPolicy.shouldShowExam(true, true, false, true, "invalid", "2026-11-15"))
        assertFalse(AcademicReminderPolicy.shouldShowExam(false, true, false, true, "2026-11-16", "2026-11-15"))
    }
}
