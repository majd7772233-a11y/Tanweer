package com.magd.tanweer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiDisplayValuePolicyTest {
    @Test
    fun eventCategoryKeysAreCaseInsensitiveAndLimitedToKnownValues() {
        assertEquals("ACTIVITY", ApiDisplayValuePolicy.eventCategoryKey(" activity "))
        assertEquals("COMPETITION", ApiDisplayValuePolicy.eventCategoryKey("competition"))
        assertEquals("ANNOUNCEMENT", ApiDisplayValuePolicy.eventCategoryKey("announcement"))
        assertEquals("CELEBRATION", ApiDisplayValuePolicy.eventCategoryKey("Celebration"))
        assertNull(ApiDisplayValuePolicy.eventCategoryKey("UNRECOGNIZED_CATEGORY"))
    }

    @Test
    fun examTypesAreMappedWithoutTreatingUnknownValuesAsMonthly() {
        assertEquals("MONTHLY", ApiDisplayValuePolicy.examTypeKey("monthly"))
        assertEquals("ORAL", ApiDisplayValuePolicy.examTypeKey("ORAL"))
        assertEquals("MIDTERM", ApiDisplayValuePolicy.examTypeKey("midterm"))
        assertEquals("FINAL", ApiDisplayValuePolicy.examTypeKey("FINAL"))
        assertEquals("MONTHLY", ApiDisplayValuePolicy.examTypeKey("QUIZ"))
    }

    @Test
    fun encodedChapterListDecodesSeparatorsAndIdentifiers() {
        val tokens = ApiDisplayValuePolicy.parseChapterTokens("CH1%2C_CH2%2C_Final_Review")

        assertEquals(
            listOf(
                ApiDisplayValuePolicy.ChapterToken(ApiDisplayValuePolicy.ChapterTokenKind.NUMBER, "1"),
                ApiDisplayValuePolicy.ChapterToken(ApiDisplayValuePolicy.ChapterTokenKind.NUMBER, "2"),
                ApiDisplayValuePolicy.ChapterToken(ApiDisplayValuePolicy.ChapterTokenKind.FINAL_REVIEW, "")
            ),
            tokens
        )
    }

    @Test
    fun chapterListSupportsPlainDelimitersAndChapterWordVariants() {
        val tokens = ApiDisplayValuePolicy.parseChapterTokens("Chapter 1; CH-3; Final Review")
        assertEquals(3, tokens.size)
        assertEquals(ApiDisplayValuePolicy.ChapterTokenKind.NUMBER, tokens[0].kind)
        assertEquals("1", tokens[0].value)
        assertEquals("3", tokens[1].value)
        assertEquals(ApiDisplayValuePolicy.ChapterTokenKind.FINAL_REVIEW, tokens[2].kind)
    }

    @Test
    fun unknownChapterIdentifiersAreHumanizedRatherThanShownAsRawEnums() {
        assertEquals("Supplementary Topics", ApiDisplayValuePolicy.humanizeUnknownValue("SUPPLEMENTARY_TOPICS"))
        assertEquals("Activity", ApiDisplayValuePolicy.humanizeUnknownValue("ACTIVITY"))
    }

    @Test
    fun uriDecodingPreservesLiteralPlusAndDecodesPercentSequences() {
        assertEquals("A+B,_C", ApiDisplayValuePolicy.decodeUriComponent("A+B%2C_C"))
    }

    @Test
    fun malformedPercentSequencesDoNotCrashDisplayFormatting() {
        assertEquals("CH1%2", ApiDisplayValuePolicy.decodeUriComponent("CH1%2"))
        assertTrue(ApiDisplayValuePolicy.parseChapterTokens("CH1%2").isNotEmpty())
    }

    @Test
    fun nullOrBlankChapterListsRemainEmpty() {
        assertTrue(ApiDisplayValuePolicy.parseChapterTokens(null).isEmpty())
        assertTrue(ApiDisplayValuePolicy.parseChapterTokens("   ").isEmpty())
    }
}
