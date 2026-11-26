package com.magd.tanweer.util

import com.magd.tanweer.data.model.ExamItem

data class ExamReviewSummary(
    val reviewedCount: Int,
    val totalCount: Int
)

/**
 * Keeps the exam schedule date separate from the student's personal review state.
 *
 * A passed date never implies that the student reviewed the material. Only an
 * explicitly completed, server-confirmed exam contributes to the review count.
 */
object ExamReviewPolicy {
    private const val CONFIRMED_SYNC_STATUS = "SYNCED"

    fun isConfirmed(exam: ExamItem): Boolean =
        exam.syncStatus.trim().equals(CONFIRMED_SYNC_STATUS, ignoreCase = true)

    fun isReviewed(exam: ExamItem): Boolean =
        isConfirmed(exam) && exam.isCompleted

    fun summarize(exams: Iterable<ExamItem>): ExamReviewSummary {
        val confirmedExams = exams.filter(::isConfirmed)
        return ExamReviewSummary(
            reviewedCount = confirmedExams.count { it.isCompleted },
            totalCount = confirmedExams.size
        )
    }
}
