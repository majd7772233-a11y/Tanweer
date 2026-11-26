package com.magd.tanweer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicSearchEngineIdentifierQueryTest {
    private val targetId = "QA_HW_20261119_A"

    @Test
    fun structuredIdentifierMatchesOnlyWhenEverySegmentIsPresent() {
        assertTrue(
            ArabicSearchEngine.calculateRelevance(
                query = targetId,
                title = "Homework QA_HW_20261119_A"
            ) > 0
        )
    }

    @Test
    fun substringInsideAnotherIdentifierSegmentDoesNotCreateAMatch() {
        assertEquals(
            0,
            ArabicSearchEngine.calculateRelevance(query = targetId, title = "QATEST2")
        )
        assertEquals(
            0,
            ArabicSearchEngine.calculateRelevance(
                query = targetId,
                title = "SYNCQA_ISSUE_20261118_A1"
            )
        )
    }

    @Test
    fun allIdentifierSegmentsMayAppearAcrossSearchableFields() {
        assertTrue(
            ArabicSearchEngine.calculateRelevance(
                query = targetId,
                title = "QA homework",
                body = "HW date 20261119 section A"
            ) > 0
        )
    }

    @Test
    fun structuredIdentifiersCanUseOnlySingleCharacterSegments() {
        assertTrue(
            ArabicSearchEngine.calculateRelevance(
                query = "A_B_C_1",
                title = "A B C 1"
            ) > 0
        )
    }

    @Test
    fun naturalLanguageAndNonStructuredQueriesKeepPartialTokenSearch() {
        assertTrue(
            ArabicSearchEngine.calculateRelevance(
                query = "رياضيات اختبار",
                title = "اختبار الرياضيات"
            ) > 0
        )
        assertTrue(
            ArabicSearchEngine.calculateRelevance(
                query = "QA HW",
                title = "QATEST2"
            ) > 0
        )
    }
}