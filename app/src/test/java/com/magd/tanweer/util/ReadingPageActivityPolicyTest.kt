package com.magd.tanweer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingPageActivityPolicyTest {
    @Test
    fun humanPageUpdatesSessionDailyAndLifetimeSetsTogether() {
        val next = ReadingPageActivityPolicy.recordHumanPage(ReadingPageActivity(), 11)

        assertEquals(setOf(11), next.sessionHumanPages)
        assertEquals(setOf(11), next.dailyHumanPages)
        assertEquals(setOf(11), next.lifetimeUniquePages)
        assertTrue(next.autoScrolledPages.isEmpty())
    }

    @Test
    fun revisitingPageDoesNotIncreaseAnyUniqueHumanPageCount() {
        val first = ReadingPageActivityPolicy.recordHumanPage(ReadingPageActivity(), 4)
        val repeated = ReadingPageActivityPolicy.recordHumanPage(first, 4)

        assertEquals(setOf(4), repeated.sessionHumanPages)
        assertEquals(setOf(4), repeated.dailyHumanPages)
        assertEquals(setOf(4), repeated.lifetimeUniquePages)
    }

    @Test
    fun autoScrolledPageIsTrackedSeparatelyAndNeverCountsAsHumanReading() {
        val manual = ReadingPageActivityPolicy.recordHumanPage(ReadingPageActivity(), 3)
        val next = ReadingPageActivityPolicy.recordAutoScrolledPage(manual, 4)

        assertEquals(setOf(3), next.sessionHumanPages)
        assertEquals(setOf(3), next.dailyHumanPages)
        assertEquals(setOf(3), next.lifetimeUniquePages)
        assertEquals(setOf(4), next.autoScrolledPages)
    }

    @Test
    fun negativePageIndexDoesNotChangeAnyCounter() {
        val initial = ReadingPageActivity()
        assertEquals(initial, ReadingPageActivityPolicy.recordHumanPage(initial, -1))
        assertEquals(initial, ReadingPageActivityPolicy.recordAutoScrolledPage(initial, -1))
    }

    @Test
    fun velocityUsesSessionHumanPagesRatherThanLifetimePages() {
        assertEquals(3, ReadingPageActivityPolicy.pagesPerHour(3, 3600L))
        assertEquals(6, ReadingPageActivityPolicy.pagesPerHour(3, 1800L))
    }

    @Test
    fun velocityIsZeroForEmptyOrTooShortSessions() {
        assertEquals(0, ReadingPageActivityPolicy.pagesPerHour(10, 60L))
        assertEquals(0, ReadingPageActivityPolicy.pagesPerHour(0, 3600L))
    }

    @Test
    fun dailyStorageKeysAreScopedByBothBookAndLocalDate() {
        val today = ReadingPageActivityPolicy.dailyStorageKey("book-a", "2026-11-23")
        assertNotEquals(today, ReadingPageActivityPolicy.dailyStorageKey("book-a", "2026-11-24"))
        assertNotEquals(today, ReadingPageActivityPolicy.dailyStorageKey("book-b", "2026-11-23"))
    }

    @Test
    fun savedPageSetParsingIsSafeAndSerializationIsDeterministic() {
        assertEquals(setOf(1, 3, 8), ReadingPageActivityPolicy.parsePageIndices("3,1,3,-1,nope,8"))
        assertEquals("1,3,8", ReadingPageActivityPolicy.serializePageIndices(setOf(8, 3, 1)))
    }
}
