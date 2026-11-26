package com.magd.tanweer.ui.screens

import com.magd.tanweer.R
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class MissedTimeRange(val labelRes: Int) {
    SINCE_LAST_VISIT(R.string.catchup_range_since_last_visit),
    YESTERDAY(R.string.catchup_range_yesterday),
    LAST_3_DAYS(R.string.catchup_range_last_three_days),
    THIS_WEEK(R.string.catchup_range_this_week),
    LAST_TWO_WEEKS(R.string.catchup_range_last_two_weeks)
}

data class MissedDateRange(
    val startDate: String,
    val endDate: String
)

/**
 * Resolves catch-up periods as local calendar dates, not elapsed 24-hour blocks.
 *
 * Yesterday is one local date. Last 3 days and last 2 weeks include today.
 * The current week starts Sunday and runs through today. Since last visit keeps
 * the stored visit date through today; invalid/future values safely use today.
 */
object MissedDateRangePolicy {
    private const val DATE_PATTERN = "yyyy-MM-dd"

    fun resolve(
        range: MissedTimeRange,
        lastOpenDate: String,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): MissedDateRange {
        val today = calendarFor(nowMillis, timeZone)
        val start = copyDate(today)
        val end = copyDate(today)

        when (range) {
            MissedTimeRange.SINCE_LAST_VISIT -> {
                val storedVisit = parseDate(lastOpenDate, timeZone)
                if (storedVisit != null && !storedVisit.after(today)) {
                    start.timeInMillis = storedVisit.timeInMillis
                }
            }
            MissedTimeRange.YESTERDAY -> {
                start.add(Calendar.DAY_OF_MONTH, -1)
                end.add(Calendar.DAY_OF_MONTH, -1)
            }
            MissedTimeRange.LAST_3_DAYS -> start.add(Calendar.DAY_OF_MONTH, -2)
            MissedTimeRange.THIS_WEEK -> {
                val daysSinceSunday =
                    (today.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 7) % 7
                start.add(Calendar.DAY_OF_MONTH, -daysSinceSunday)
            }
            MissedTimeRange.LAST_TWO_WEEKS -> start.add(Calendar.DAY_OF_MONTH, -13)
        }

        return MissedDateRange(formatDate(start, timeZone), formatDate(end, timeZone))
    }

    private fun calendarFor(nowMillis: Long, timeZone: TimeZone): Calendar =
        Calendar.getInstance(timeZone, Locale.US).apply {
            firstDayOfWeek = Calendar.SUNDAY
            minimalDaysInFirstWeek = 1
            timeInMillis = nowMillis
            clearTime()
        }

    private fun parseDate(value: String, timeZone: TimeZone): Calendar? {
        if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(value)) return null
        val formatter = formatter(timeZone)
        val parsed = try {
            formatter.parse(value)
        } catch (_: ParseException) {
            null
        } ?: return null
        if (formatter.format(parsed) != value) return null

        return Calendar.getInstance(timeZone, Locale.US).apply {
            firstDayOfWeek = Calendar.SUNDAY
            minimalDaysInFirstWeek = 1
            timeInMillis = parsed.time
            clearTime()
        }
    }

    private fun formatDate(calendar: Calendar, timeZone: TimeZone): String =
        formatter(timeZone).format(calendar.time)

    private fun formatter(timeZone: TimeZone) =
        SimpleDateFormat(DATE_PATTERN, Locale.US).apply {
            isLenient = false
            this.timeZone = timeZone
        }

    private fun copyDate(source: Calendar): Calendar =
        (source.clone() as Calendar).apply { clearTime() }

    private fun Calendar.clearTime() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
