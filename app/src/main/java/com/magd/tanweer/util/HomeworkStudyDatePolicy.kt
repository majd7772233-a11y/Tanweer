package com.magd.tanweer.util

/**
 * Resolves the date initially shown by the homework form without silently replacing
 * an intentionally selected academic date with the device's current date.
 */
object HomeworkStudyDatePolicy {
    fun initial(selectedStudyDate: String?, todayDate: String): String =
        selectedStudyDate?.takeIf { AcademicSessionSelection.isValidIsoDate(it) } ?: todayDate
}