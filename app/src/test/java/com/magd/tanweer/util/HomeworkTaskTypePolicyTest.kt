package com.magd.tanweer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeworkTaskTypePolicyTest {
    @Test
    fun nullAndBlankLegacyTypesRemainHomework() {
        assertEquals(HomeworkTaskKind.HOMEWORK, HomeworkTaskTypePolicy.classify(null))
        assertEquals(HomeworkTaskKind.HOMEWORK, HomeworkTaskTypePolicy.classify(""))
        assertEquals(HomeworkTaskKind.HOMEWORK, HomeworkTaskTypePolicy.classify("   "))
        assertTrue(HomeworkTaskTypePolicy.isHomework(null))
    }

    @Test
    fun homeworkTypeIsNormalized() {
        assertEquals(HomeworkTaskKind.HOMEWORK, HomeworkTaskTypePolicy.classify(" homework "))
        assertTrue(HomeworkTaskTypePolicy.isHomework("HOMEWORK"))
        assertTrue(HomeworkTaskTypePolicy.isHomework("homework"))
    }

    @Test
    fun taskTypeIsNormalizedAsProjectAndNeverAsHomework() {
        assertEquals(HomeworkTaskKind.TASK, HomeworkTaskTypePolicy.classify(" task "))
        assertTrue(HomeworkTaskTypePolicy.isTask("TASK"))
        assertTrue(HomeworkTaskTypePolicy.isTask("task"))
        assertFalse(HomeworkTaskTypePolicy.isHomework("TASK"))
    }

    @Test
    fun summarySeparatesHomeworkProjectsAndUnknownTypes() {
        val summary = HomeworkTaskTypePolicy.summarize(
            listOf("HOMEWORK", " homework ", "TASK", "FUTURE_KIND")
        )
        assertEquals(2, summary.homeworkCount)
        assertEquals(1, summary.projectCount)
        assertEquals(1, summary.otherAssignmentCount)
    }

    @Test
    fun summaryOfNoRecordsIsZeroForEveryCategory() {
        val summary = HomeworkTaskTypePolicy.summarize(emptyList())
        assertEquals(0, summary.homeworkCount)
        assertEquals(0, summary.projectCount)
        assertEquals(0, summary.otherAssignmentCount)
    }

    @Test
    fun unknownTypesAreNotCountedAsHomeworkOrProject() {
        assertEquals(HomeworkTaskKind.UNKNOWN, HomeworkTaskTypePolicy.classify("PROJECT_V2"))
        assertFalse(HomeworkTaskTypePolicy.isHomework("PROJECT_V2"))
        assertFalse(HomeworkTaskTypePolicy.isTask("PROJECT_V2"))
    }
}
