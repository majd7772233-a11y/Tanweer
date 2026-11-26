package com.magd.tanweer.ui.screens

/**
 * Counts the entries currently rendered for the selected calendar day.
 * These are local Room-backed records, not a claim that an unverified server snapshot is complete.
 */
data class CalendarDaySummary(
    val lessonCount: Int,
    val examCount: Int,
    val assignmentCount: Int,
    val eventCount: Int
) {
    val totalCount: Int
        get() = lessonCount + examCount + assignmentCount + eventCount

    val shouldShowContinuationHint: Boolean
        get() = totalCount > 1
}

fun buildCalendarDaySummary(
    lessonCount: Int,
    examCount: Int,
    assignmentCount: Int,
    eventCount: Int
): CalendarDaySummary = CalendarDaySummary(
    lessonCount = lessonCount.coerceAtLeast(0),
    examCount = examCount.coerceAtLeast(0),
    assignmentCount = assignmentCount.coerceAtLeast(0),
    eventCount = eventCount.coerceAtLeast(0)
)
