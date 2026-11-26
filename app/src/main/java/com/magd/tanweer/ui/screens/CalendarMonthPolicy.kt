package com.magd.tanweer.ui.screens

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Keeps calendar selection and summary counts scoped to the month being displayed.
 *
 * Assignments are associated with both their study and due dates. An assignment
 * contributes once to each month in which either date falls, matching the day
 * markers used by CalendarScreen.
 */
object CalendarMonthPolicy {
    private const val DATE_PATTERN = "yyyy-MM-dd"
    private const val MONTH_PATTERN = "yyyy-MM"
    private val utc: TimeZone
        get() = TimeZone.getTimeZone("UTC")

    /**
     * Preserves the selected day number when moving to another month, clamping
     * it to that month's final day (for example, Jan 31 -> Feb 28/29).
     * Invalid selections safely land on the first day of the target month.
     */
    fun resolveSelectedDate(selectedDate: String, targetMonth: String): String {
        val target = parseCalendarValue(targetMonth, MONTH_PATTERN)
            ?: return selectedDate
        val selected = parseCalendarValue(selectedDate, DATE_PATTERN)
        val selectedDay = selected?.get(Calendar.DAY_OF_MONTH) ?: 1
        target.set(
            Calendar.DAY_OF_MONTH,
            selectedDay.coerceAtMost(target.getActualMaximum(Calendar.DAY_OF_MONTH))
        )
        return formatter(DATE_PATTERN).format(target.time)
    }

    fun isDateInMonth(date: String?, month: String): Boolean {
        if (parseCalendarValue(month, MONTH_PATTERN) == null) return false
        val parsedDate = date?.let { parseCalendarValue(it, DATE_PATTERN) } ?: return false
        return formatter(MONTH_PATTERN).format(parsedDate.time) == month
    }

    /**
     * Counts each item once when at least one of its relevant dates belongs
     * to the requested month. Multiple matching dates on one item do not
     * inflate the total.
     */
    fun <T> countItemsInMonth(
        items: Iterable<T>,
        month: String,
        datesForItem: (T) -> Iterable<String?>
    ): Int {
        if (parseCalendarValue(month, MONTH_PATTERN) == null) return 0
        return items.count { item ->
            datesForItem(item).any { date -> isDateInMonth(date, month) }
        }
    }

    private fun parseCalendarValue(value: String, pattern: String): Calendar? {
        val expectedLength = if (pattern == DATE_PATTERN) 10 else 7
        if (value.length != expectedLength) return null
        val formatter = formatter(pattern)
        val position = ParsePosition(0)
        val parsed = formatter.parse(value, position) ?: return null
        if (position.index != value.length || formatter.format(parsed) != value) return null
        return Calendar.getInstance(utc, Locale.US).apply {
            isLenient = false
            timeInMillis = parsed.time
        }
    }

    private fun formatter(pattern: String) =
        SimpleDateFormat(pattern, Locale.US).apply {
            isLenient = false
            timeZone = utc
        }
}