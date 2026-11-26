package com.magd.tanweer.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MissedDateRangePolicyTest {
    private val utc = TimeZone.getTimeZone("UTC")

    private fun epoch(dateTime: String, zone: TimeZone = utc): Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = zone
        }.parse(dateTime)!!.time

    private fun range(
        selected: MissedTimeRange,
        dateTime: String,
        lastOpenDate: String = "",
        zone: TimeZone = utc
    ) = MissedDateRangePolicy.resolve(
        range = selected,
        lastOpenDate = lastOpenDate,
        nowMillis = epoch(dateTime, zone),
        timeZone = zone
    )

    @Test
    fun yesterdayIsExactlyOneLocalDateAcrossYearBoundary() {
        assertEquals(
            MissedDateRange("2026-12-31", "2026-12-31"),
            range(MissedTimeRange.YESTERDAY, "2027-01-01 12:00:00")
        )
    }

    @Test
    fun yesterdayHandlesLeapDay() {
        assertEquals(
            MissedDateRange("2024-02-29", "2024-02-29"),
            range(MissedTimeRange.YESTERDAY, "2024-03-01 12:00:00")
        )
    }

    @Test
    fun lastThreeDaysIncludesTodayAndExactlyTwoPreviousDates() {
        assertEquals(
            MissedDateRange("2026-12-30", "2027-01-01"),
            range(MissedTimeRange.LAST_3_DAYS, "2027-01-01 12:00:00")
        )
    }

    @Test
    fun currentWeekStartsOnSundayAndEndsToday() {
        assertEquals(
            MissedDateRange("2026-11-15", "2026-11-20"),
            range(MissedTimeRange.THIS_WEEK, "2026-11-20 12:00:00")
        )
        assertEquals(
            MissedDateRange("2026-11-22", "2026-11-23"),
            range(MissedTimeRange.THIS_WEEK, "2026-11-23 12:00:00")
        )
    }

    @Test
    fun lastTwoWeeksContainsExactlyFourteenDatesIncludingToday() {
        assertEquals(
            MissedDateRange("2026-11-07", "2026-11-20"),
            range(MissedTimeRange.LAST_TWO_WEEKS, "2026-11-20 12:00:00")
        )
    }

    @Test
    fun sinceLastVisitUsesStoredDateAndClampsInvalidOrFutureValuesToToday() {
        assertEquals(
            MissedDateRange("2026-11-17", "2026-11-20"),
            range(
                MissedTimeRange.SINCE_LAST_VISIT,
                "2026-11-20 12:00:00",
                lastOpenDate = "2026-11-17"
            )
        )
        assertEquals(
            MissedDateRange("2026-11-20", "2026-11-20"),
            range(
                MissedTimeRange.SINCE_LAST_VISIT,
                "2026-11-20 12:00:00",
                lastOpenDate = "2026-11-21"
            )
        )
        assertEquals(
            MissedDateRange("2026-11-20", "2026-11-20"),
            range(
                MissedTimeRange.SINCE_LAST_VISIT,
                "2026-11-20 12:00:00",
                lastOpenDate = "2026-02-30"
            )
        )
    }

    @Test
    fun localTimezoneControlsDateAroundMidnight() {
        // One fixed UTC instant is Nov 20 in Los Angeles but Nov 21 in Kiritimati.
        val instantMillis = epoch("2026-11-21 00:30:00", utc)
        val losAngeles = TimeZone.getTimeZone("America/Los_Angeles")
        val kiritimati = TimeZone.getTimeZone("Pacific/Kiritimati")

        assertEquals(
            MissedDateRange("2026-11-19", "2026-11-19"),
            MissedDateRangePolicy.resolve(
                MissedTimeRange.YESTERDAY,
                "",
                instantMillis,
                losAngeles
            )
        )
        assertEquals(
            MissedDateRange("2026-11-20", "2026-11-20"),
            MissedDateRangePolicy.resolve(
                MissedTimeRange.YESTERDAY,
                "",
                instantMillis,
                kiritimati
            )
        )
    }

    @Test
    fun lastThreeDaysUsesCalendarDaysAcrossDaylightSavingTransition() {
        val newYork = TimeZone.getTimeZone("America/New_York")
        assertEquals(
            MissedDateRange("2025-03-07", "2025-03-09"),
            range(
                MissedTimeRange.LAST_3_DAYS,
                "2025-03-09 12:00:00",
                zone = newYork
            )
        )
    }
}