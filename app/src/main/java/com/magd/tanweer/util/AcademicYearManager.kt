package com.magd.tanweer.util

import com.magd.tanweer.data.model.ContentItem
import com.magd.tanweer.data.model.ExamItem
import com.magd.tanweer.data.model.HomeworkItem

/**
 * Academic Calendar Configuration & Year Range Engine (Points 19, 20)
 * Replaces crude dateYear string checks with deterministic academic calendar boundaries:
 * e.g., Academic Year 2026-2027 starts 2026-08-01 and ends 2027-07-31.
 */
data class AcademicCalendarConfig(
    val startMonthDay: String = "08-01",       // August 1st: Standard academic year start
    val endMonthDay: String = "07-31",         // July 31st: Standard academic year end
    val semester1EndMonthDay: String = "01-31",// January 31st: End of Semester 1
    val semester2StartMonthDay: String = "02-01"// February 1st: Start of Semester 2
)

object AcademicYearManager {
    var config: AcademicCalendarConfig = AcademicCalendarConfig()

    /**
     * Strictly verifies whether a date string conforms to standard ISO (yyyy-MM-dd) with valid calendar values.
     */
    fun isValidIsoDate(dateStr: String?): Boolean {
        if (dateStr.isNullOrBlank() || dateStr.length < 10) return false
        val clean = dateStr.trim().take(10)
        if (!Regex("""^\d{4}-\d{2}-\d{2}$""").matches(clean)) return false
        val parts = clean.split("-")
        val y = parts[0].toIntOrNull() ?: return false
        val m = parts[1].toIntOrNull() ?: return false
        val d = parts[2].toIntOrNull() ?: return false
        return y in 2020..2099 && m in 1..12 && d in 1..31
    }

    /**
     * Resolves the current academic year dynamically from real system/device time.
     * Starts on August 1st: e.g. from 2026-08-01 through 2027-07-31 is "2026-2027".
     */
    fun getCurrentAcademicYear(): String {
        val cal = java.util.Calendar.getInstance()
        val curYear = cal.get(java.util.Calendar.YEAR)
        val curMonth = cal.get(java.util.Calendar.MONTH) + 1 // 1-12
        return if (curMonth >= 8) {
            "$curYear-${curYear + 1}"
        } else {
            "${curYear - 1}-$curYear"
        }
    }

    /**
     * Resolves exact start and end date strings for an academic year (e.g. "2026-2027").
     */
    fun getYearRange(academicYear: String): Pair<String, String> {
        val parts = academicYear.split("-")
        val startYear = parts.getOrNull(0)?.trim()?.toIntOrNull()
        val endYear = parts.getOrNull(1)?.trim()?.toIntOrNull()

        val validYear = if (parts.size == 2 && startYear != null && endYear != null && endYear == startYear + 1) {
            "${startYear}-${endYear}"
        } else {
            getCurrentAcademicYear()
        }
        val validParts = validYear.split("-")
        return Pair("${validParts[0]}-${config.startMonthDay}", "${validParts[1]}-${config.endMonthDay}")
    }

    /**
     * Verifies if a given ISO date (yyyy-MM-dd) falls strictly inside the configured academic year.
     * Prevents invalid dates or dates outside the boundary from being counted.
     */
    fun isDateInAcademicYear(dateStr: String, academicYear: String): Boolean {
        if (!isValidIsoDate(dateStr)) return false
        val cleanDate = dateStr.trim().take(10)
        val (start, end) = getYearRange(academicYear)
        return cleanDate in start..end
    }

    /**
     * Identifies which semester a date belongs to (Semester 1 vs Semester 2).
     */
    fun getSemesterForDate(dateStr: String, academicYear: String): Int {
        if (!isValidIsoDate(dateStr) || !isDateInAcademicYear(dateStr, academicYear)) return 0
        val cleanDate = dateStr.trim().take(10)
        val parts = academicYear.split("-")
        val startYear = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return 0
        val s1End = "$startYear-${config.semester1EndMonthDay}"
        return if (cleanDate <= s1End) 1 else 2
    }

    /**
     * Calculates the academic year label for any valid date.
     * Returns null if the date is invalid or does not match ISO format.
     */
    fun getAcademicYearForDate(dateStr: String): String {
        if (!isValidIsoDate(dateStr)) {
            return getCurrentAcademicYear()
        }
        val clean = dateStr.trim().take(10)
        val year = clean.take(4).toIntOrNull() ?: return getCurrentAcademicYear()
        val monthDay = clean.substring(5, 10)
        return if (monthDay >= config.startMonthDay) {
            "$year-${year + 1}"
        } else {
            "${year - 1}-$year"
        }
    }

    /**
     * Dynamically resolves the available academic years based on real school records and policy.
     * Extracts all years present in the dataset, anchors to current academic year, and returns sorted descending.
     */
    fun resolveAvailableAcademicYears(
        currentDateStr: String,
        itemDates: Collection<String> = emptyList()
    ): List<String> {
        val yearsSet = mutableSetOf<String>()
        val currentYear = if (isValidIsoDate(currentDateStr)) {
            getAcademicYearForDate(currentDateStr)
        } else {
            getCurrentAcademicYear()
        }
        yearsSet.add(currentYear)

        for (d in itemDates) {
            if (isValidIsoDate(d)) {
                yearsSet.add(getAcademicYearForDate(d))
            }
        }

        val startYr = currentYear.substringBefore("-").toIntOrNull()
            ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        yearsSet.add("${startYr - 1}-$startYr")

        return yearsSet.toList().sortedDescending()
    }
}

/**
 * Structured Academic Archive Report & Metrics Engine (Point 19)
 */
data class AcademicArchiveReport(
    val academicYear: String,
    val startDate: String,
    val endDate: String,
    val totalLessons: Int,
    val totalHomeworks: Int,
    val completedHomeworks: Int,
    val totalExams: Int,
    val personalContributionsCount: Int,
    val homeworkCompletionRate: Float,
    val semester1Lessons: Int,
    val semester2Lessons: Int,
    val semester1Homeworks: Int,
    val semester2Homeworks: Int,
    val isArchivedPastYear: Boolean
)

object AcademicArchiveEngine {

    fun generateArchiveReport(
        academicYear: String,
        contents: List<ContentItem>,
        homeworks: List<HomeworkItem>,
        exams: List<ExamItem>,
        userFullName: String?,
        currentDate: String,
        userId: String? = null
    ): AcademicArchiveReport {
        val (startDate, endDate) = AcademicYearManager.getYearRange(academicYear)

        val yearContents = contents.filter { AcademicYearManager.isDateInAcademicYear(it.studyDate, academicYear) }
        val yearHomeworks = homeworks.filter {
            AcademicYearManager.isDateInAcademicYear(it.dueDate, academicYear) ||
                    AcademicYearManager.isDateInAcademicYear(it.studyDate, academicYear)
        }
        val yearExams = exams.filter { AcademicYearManager.isDateInAcademicYear(it.examDate, academicYear) }

        val completedHw = yearHomeworks.count { it.isCompleted }
        val completionRate = if (yearHomeworks.isNotEmpty()) {
            (completedHw.toFloat() / yearHomeworks.size.toFloat()) * 100f
        } else 0f

        val personalContributions = yearContents.count {
            if (!userId.isNullOrBlank() && it.createdBy.isNotBlank()) {
                it.createdBy == userId
            } else {
                it.authorName.isNotBlank() && it.authorName == userFullName
            }
        }

        val s1Lessons = yearContents.count { AcademicYearManager.getSemesterForDate(it.studyDate, academicYear) == 1 }
        val s2Lessons = yearContents.count { AcademicYearManager.getSemesterForDate(it.studyDate, academicYear) == 2 }

        val s1Hw = yearHomeworks.count { AcademicYearManager.getSemesterForDate(it.dueDate, academicYear) == 1 }
        val s2Hw = yearHomeworks.count { AcademicYearManager.getSemesterForDate(it.dueDate, academicYear) == 2 }

        val isPastYear = currentDate > endDate

        return AcademicArchiveReport(
            academicYear = academicYear,
            startDate = startDate,
            endDate = endDate,
            totalLessons = yearContents.size,
            totalHomeworks = yearHomeworks.size,
            completedHomeworks = completedHw,
            totalExams = yearExams.size,
            personalContributionsCount = personalContributions,
            homeworkCompletionRate = completionRate,
            semester1Lessons = s1Lessons,
            semester2Lessons = s2Lessons,
            semester1Homeworks = s1Hw,
            semester2Homeworks = s2Hw,
            isArchivedPastYear = isPastYear
        )
    }
}
