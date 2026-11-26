package com.magd.tanweer.util

import com.magd.tanweer.data.model.ExamItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamReviewPolicyTest {
    private fun exam(
        id: String,
        isCompleted: Boolean,
        syncStatus: String = "SYNCED",
        examDate: String
    ) = ExamItem(
        id = id,
        groupId = "class_10_A",
        examDate = examDate,
        subjectId = "math",
        title = id,
        isCompleted = isCompleted,
        syncStatus = syncStatus
    )

    @Test
    fun passedDateDoesNotMarkAnUnreviewedExamAsReviewed() {
        val pastExam = exam("past_unreviewed", false, examDate = "2026-11-20")
        assertFalse(ExamReviewPolicy.isReviewed(pastExam))
        assertEquals(ExamReviewSummary(0, 1), ExamReviewPolicy.summarize(listOf(pastExam)))
    }

    @Test
    fun explicitReviewStateIsIndependentOfWhetherExamDateIsPastOrFuture() {
        val pastReviewed = exam("past_reviewed", true, examDate = "2026-11-20")
        val futureReviewed = exam("future_reviewed", true, examDate = "2026-12-20")
        assertTrue(ExamReviewPolicy.isReviewed(pastReviewed))
        assertTrue(ExamReviewPolicy.isReviewed(futureReviewed))
        assertEquals(
            ExamReviewSummary(2, 2),
            ExamReviewPolicy.summarize(listOf(pastReviewed, futureReviewed))
        )
    }

    @Test
    fun unconfirmedRowsAreExcludedFromBothReviewNumeratorAndDenominator() {
        val exams = listOf(
            exam("confirmed_reviewed", true, examDate = "2026-11-20"),
            exam("confirmed_unreviewed", false, examDate = "2026-12-20"),
            exam("failed", true, syncStatus = "FAILED", examDate = "2026-11-20"),
            exam("pending", true, syncStatus = "PENDING", examDate = "2026-11-20"),
            exam("syncing", true, syncStatus = "SYNCING", examDate = "2026-11-20"),
            exam("local", true, syncStatus = "LOCAL", examDate = "2026-11-20"),
            exam("delete_pending", true, syncStatus = "DELETE_PENDING", examDate = "2026-11-20")
        )
        assertEquals(ExamReviewSummary(1, 2), ExamReviewPolicy.summarize(exams))
        assertFalse(ExamReviewPolicy.isReviewed(exams[2]))
    }
}
