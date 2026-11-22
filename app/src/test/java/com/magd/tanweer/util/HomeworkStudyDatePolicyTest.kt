package com.magd.tanweer.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeworkStudyDatePolicyTest {
    @Test
    fun preservesAnIntentionalPreviousStudyDateInsteadOfForcingToday() {
        assertEquals("2026-11-18", HomeworkStudyDatePolicy.initial("2026-11-18", "2026-11-19"))
    }

    @Test
    fun fallsBackToTodayWhenNoValidStudyDateWasSelected() {
        assertEquals("2026-11-19", HomeworkStudyDatePolicy.initial(null, "2026-11-19"))
        assertEquals("2026-11-19", HomeworkStudyDatePolicy.initial("", "2026-11-19"))
        assertEquals("2026-11-19", HomeworkStudyDatePolicy.initial("not-a-date", "2026-11-19"))
    }
}