package com.magd.tanweer.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingStateCheckpointPolicyTest {
    private fun capture(page: Int, visited: Set<Int>) = ReadingStateCheckpointPolicy.capture(
        currentPageIndex = page,
        uniquePageIndices = visited,
        zoom = 1.25f,
        theme = "NIGHT",
        totalStudySeconds = 120L
    )

    @Test
    fun currentPositionIsCapturedEvenWhenPageWasAlreadyVisited() {
        val checkpoint = capture(page = 1, visited = setOf(1, 99))
        assertEquals(1, checkpoint.lastPageIndex)
        assertEquals(2, checkpoint.uniquePagesCount)
    }

    @Test
    fun currentPositionDoesNotFollowHighestUniquePageIndex() {
        assertEquals(1, capture(page = 1, visited = setOf(1, 99)).lastPageIndex)
    }

    @Test
    fun checkpointKeepsTheOtherReadingFields() {
        val checkpoint = capture(page = 2, visited = setOf(0, 1, 2))
        assertEquals(1.25f, checkpoint.zoom)
        assertEquals("NIGHT", checkpoint.theme)
        assertEquals(120L, checkpoint.totalStudySeconds)
        assertEquals(3, checkpoint.uniquePagesCount)
    }

    @Test
    fun negativePageAndTimeAreSafelyClamped() {
        val checkpoint = ReadingStateCheckpointPolicy.capture(
            currentPageIndex = -1,
            uniquePageIndices = emptySet(),
            zoom = 1f,
            theme = "DAY",
            totalStudySeconds = -1L
        )
        assertEquals(0, checkpoint.lastPageIndex)
        assertEquals(0L, checkpoint.totalStudySeconds)
    }
}
