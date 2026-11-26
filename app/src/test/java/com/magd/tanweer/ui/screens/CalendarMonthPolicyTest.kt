package com.magd.tanweer.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarMonthPolicyTest {
    @Test
    fun movingAcrossMonthsPreservesTheSelectedDayAndYearRollover() {
        assertEquals(
            "2026-12-19",
            CalendarMonthPolicy.resolveSelectedDate("2026-11-19", "2026-12")
        )
        assertEquals(
            "2027-01-19",
            CalendarMonthPolicy.resolveSelectedDate("2026-12-19", "2027-01")
        )
        assertEquals(
            "2026-11-19",
            CalendarMonthPolicy.resolveSelectedDate("2026-12-19", "2026-11")
        )
    }

    @Test
    fun selectedDayIsClampedForShortAndLeapYearMonths() {
        assertEquals(
            "2024-02-29",
            CalendarMonthPolicy.resolveSelectedDate("2024-01-31", "2024-02")
        )
        assertEquals(
            "2025-02-28",
            CalendarMonthPolicy.resolveSelectedDate("2025-01-31", "2025-02")
        )
        assertEquals(
            "2026-04-30",
            CalendarMonthPolicy.resolveSelectedDate("2026-03-31", "2026-04")
        )
    }

    @Test
    fun malformedSelectedDateFallsBackToFirstDayOfTargetMonth() {
        assertEquals(
            "2027-01-01",
            CalendarMonthPolicy.resolveSelectedDate("2026-02-30", "2027-01")
        )
        assertEquals(
            "2027-01-01",
            CalendarMonthPolicy.resolveSelectedDate("", "2027-01")
        )
    }

    @Test
    fun invalidTargetMonthDoesNotRewriteTheCurrentSelection() {
        assertEquals(
            "2026-11-19",
            CalendarMonthPolicy.resolveSelectedDate("2026-11-19", "2026-13")
        )
    }

    @Test
    fun monthlyCountsIncludeEitherAssignmentDateButCountAnItemOnlyOncePerMonth() {
        data class Assignment(val studyDate: String?, val dueDate: String?)

        val assignments = listOf(
            Assignment("2026-11-30", "2026-12-02"),
            Assignment("2026-11-18", "2026-11-20"),
            Assignment("2026-12-01", null),
            Assignment(null, "2027-01-04"),
            Assignment("not-a-date", null),
            Assignment("2026-10-31", "2026-10-31")
        )

        assertEquals(2, CalendarMonthPolicy.countItemsInMonth(assignments, "2026-11") {
            listOf(it.studyDate, it.dueDate)
        })
        assertEquals(2, CalendarMonthPolicy.countItemsInMonth(assignments, "2026-12") {
            listOf(it.studyDate, it.dueDate)
        })
        assertEquals(1, CalendarMonthPolicy.countItemsInMonth(assignments, "2027-01") {
            listOf(it.studyDate, it.dueDate)
        })
    }

    @Test
    fun dateMembershipRejectsOtherMonthsAndInvalidCalendarDates() {
        assertTrue(CalendarMonthPolicy.isDateInMonth("2026-11-01", "2026-11"))
        assertTrue(CalendarMonthPolicy.isDateInMonth("2026-11-30", "2026-11"))
        assertFalse(CalendarMonthPolicy.isDateInMonth("2026-12-01", "2026-11"))
        assertFalse(CalendarMonthPolicy.isDateInMonth("2026-11-31", "2026-11"))
        assertFalse(CalendarMonthPolicy.isDateInMonth("2026-11-01-extra", "2026-11"))
        assertFalse(CalendarMonthPolicy.isDateInMonth("2026-11-01", "2026-1"))
    }

    @Test
    fun invalidMonthReturnsZeroWithoutCountingAnyItems() {
        assertEquals(
            0,
            CalendarMonthPolicy.countItemsInMonth(listOf("2026-11-01"), "2026-13") { listOf(it) }
        )
    }
}