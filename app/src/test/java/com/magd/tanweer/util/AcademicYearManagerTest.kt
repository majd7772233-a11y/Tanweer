package com.magd.tanweer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicYearManagerTest {

    @Test
    fun acceptsRealCalendarDatesIncludingLeapDays() {
        assertTrue(AcademicYearManager.isValidIsoDate("2024-02-29"))
        assertTrue(AcademicYearManager.isValidIsoDate("2026-11-06"))
        assertFalse(AcademicYearManager.isValidIsoDate("2026-02-29"))
        assertFalse(AcademicYearManager.isValidIsoDate("2026-04-31"))
        assertFalse(AcademicYearManager.isValidIsoDate("2026-13-01"))
        assertFalse(AcademicYearManager.isValidIsoDate("2026-00-10"))
        assertFalse(AcademicYearManager.isValidIsoDate("2026-11-00"))
        assertFalse(AcademicYearManager.isValidIsoDate("2026/11/06"))
    }

    @Test
    fun resolvesAcademicYearFromConfiguredAugustBoundary() {
        assertEquals("2025-2026", AcademicYearManager.getAcademicYearForDate("2026-07-31"))
        assertEquals("2026-2027", AcademicYearManager.getAcademicYearForDate("2026-08-01"))
        assertEquals("2026-2027", AcademicYearManager.getAcademicYearForDate("2027-01-31"))
        assertEquals("2026-2027", AcademicYearManager.getAcademicYearForDate("2027-02-01"))
        assertEquals("2026-2027", AcademicYearManager.getAcademicYearForDate("not-a-date"))
    }

    @Test
    fun resolvesOnlyCurrentAndActuallyRecordedAcademicYears() {
        val years = AcademicYearManager.resolveAvailableAcademicYears(
            currentDateStr = "2026-11-06",
            itemDates = listOf(
                "2024-09-15",
                "2023-05-20",
                "2024-09-15",
                "2026-02-31",
                ""
            )
        )

        assertEquals(
            listOf("2026-2027", "2024-2025", "2022-2023"),
            years
        )
        assertFalse("2025-2026" in years)
    }

    @Test
    fun academicYearRangesAndSemesterBoundariesAreExact() {
        assertEquals(
            "2026-08-01" to "2027-07-31",
            AcademicYearManager.getYearRange("2026-2027")
        )
        assertEquals(1, AcademicYearManager.getSemesterForDate("2027-01-31", "2026-2027"))
        assertEquals(2, AcademicYearManager.getSemesterForDate("2027-02-01", "2026-2027"))
        assertEquals(0, AcademicYearManager.getSemesterForDate("2027-08-01", "2026-2027"))
    }
}
