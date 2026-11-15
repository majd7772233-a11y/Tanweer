package com.magd.tanweer.util

/**
 * Pure policy for deciding whether cached academic records still justify a reminder.
 * Dates use the app's canonical yyyy-MM-dd representation.
 */
internal object AcademicReminderPolicy {
    private val datePattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun mayDismissEmptySnapshot(autoSyncEnabled: Boolean, syncSucceeded: Boolean): Boolean =
        !autoSyncEnabled || syncSucceeded

    private fun isTodayOrFuture(date: String, today: String): Boolean =
        datePattern.matches(date) && datePattern.matches(today) && date >= today

    fun shouldShowHomework(
        enabled: Boolean,
        exists: Boolean,
        completed: Boolean,
        memberOfGroup: Boolean,
        dueDate: String,
        today: String
    ): Boolean = enabled && exists && !completed && memberOfGroup && isTodayOrFuture(dueDate, today)

    fun shouldShowExam(
        enabled: Boolean,
        exists: Boolean,
        completed: Boolean,
        memberOfGroup: Boolean,
        examDate: String,
        today: String
    ): Boolean = enabled && exists && !completed && memberOfGroup && isTodayOrFuture(examDate, today)
}
