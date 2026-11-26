package com.magd.tanweer.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarDaySummaryPolicyTest {
    @Test
    fun countsEveryRenderedCategoryAndShowsContinuationForLongDays() {
        val summary = buildCalendarDaySummary(
            lessonCount = 1,
            examCount = 3,
            assignmentCount = 2,
            eventCount = 1
        )

        assertEquals(7, summary.totalCount)
        assertEquals(1, summary.lessonCount)
        assertEquals(3, summary.examCount)
        assertEquals(2, summary.assignmentCount)
        assertEquals(1, summary.eventCount)
        assertTrue(summary.shouldShowContinuationHint)
    }

    @Test
    fun hidesSummaryCueForEmptyAndSingleItemDays() {
        val empty = buildCalendarDaySummary(0, 0, 0, 0)
        val single = buildCalendarDaySummary(1, 0, 0, 0)

        assertEquals(0, empty.totalCount)
        assertFalse(empty.shouldShowContinuationHint)
        assertEquals(1, single.totalCount)
        assertFalse(single.shouldShowContinuationHint)
    }

    @Test
    fun negativeCategoryCountsAreClampedToZero() {
        val summary = buildCalendarDaySummary(-2, 1, -3, 0)

        assertEquals(1, summary.totalCount)
        assertFalse(summary.shouldShowContinuationHint)
    }
}
