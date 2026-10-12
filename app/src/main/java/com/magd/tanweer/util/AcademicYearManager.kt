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
     * Resolves exact start and end date strings for an academic year (e.g. "2026-2027").
     */
    fun getYearRange(academicYear: String): Pair<String, String> {
        val parts = academicYear.split("-")
        if (parts.size != 2) {
            val curYear = "2026"
            return Pair("$curYear-${config.startMonthDay}", "${curYear.toInt() + 1}-${config.endMonthDay}")
        }
        val startYear = parts[0].trim()
        val endYear = parts[1].trim()
        return Pair("$startYear-${config.startMonthDay}", "$endYear-${config.endMonthDay}")
    }

    /**
     * Verifies if a given ISO date (yyyy-MM-dd) falls strictly inside the configured academic year.
     * Prevents January 2026 from falsely belonging to 2026-2027.
     */
    fun isDateInAcademicYear(dateStr: String, academicYear: String): Boolean {
        if (dateStr.isBlank()) return false
        val cleanDate = dateStr.trim().take(10)
        val (start, end) = getYearRange(academicYear)
        return cleanDate in start..end
    }

    /**
     * Identifies which semester a date belongs to (Semester 1 vs Semester 2).
     */
    fun getSemesterForDate(dateStr: String, academicYear: String): Int {
        if (!isDateInAcademicYear(dateStr, academicYear)) return 0
        val cleanDate = dateStr.trim().take(10)
        val parts = academicYear.split("-")
        val startYear = parts[0].trim()
        val s1End = "$startYear-${config.semester1EndMonthDay}"
        return if (cleanDate <= s1End) 1 else 2
    }

    /**
     * Calculates the academic year label for any given date.
     */
    fun getAcademicYearForDate(dateStr: String): String {
        if (dateStr.length < 10) return "2026-2027"
        val year = dateStr.take(4).toIntOrNull() ?: 2026
        val monthDay = dateStr.substring(5, 10)
        return if (monthDay >= config.startMonthDay) {
            "$year-${year + 1}"
        } else {
            "${year - 1}-$year"
        }
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
        currentDate: String
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

        val personalContributions = yearContents.count { it.authorName.isNotBlank() && it.authorName == userFullName }

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
