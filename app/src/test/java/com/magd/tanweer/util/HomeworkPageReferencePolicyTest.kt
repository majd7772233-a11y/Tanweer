package com.magd.tanweer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeworkPageReferencePolicyTest {
    @Test
    fun parsesSinglePageWithoutChangingItsValue() {
        assertEquals(HomeworkPageReference(10), HomeworkPageReferencePolicy.parse("10"))
        assertEquals(HomeworkPageReference(1012), HomeworkPageReferencePolicy.parse("1012"))
    }

    @Test
    fun parsesHyphenatedRangeAsTwoEndpointsNotOneConcatenatedNumber() {
        val result = HomeworkPageReferencePolicy.parse("10-12")

        assertEquals(10, result?.firstPage)
        assertEquals(12, result?.lastPage)
        assertTrue(result?.isRange == true)
        assertEquals(9, result?.firstPageIndex)
    }

    @Test
    fun acceptsRangeWhitespaceAndUnicodeDashVariants() {
        assertEquals(HomeworkPageReference(10, 12), HomeworkPageReferencePolicy.parse(" 10 - 12 "))
        assertEquals(HomeworkPageReference(10, 12), HomeworkPageReferencePolicy.parse("10–12"))
        assertEquals(HomeworkPageReference(10, 12), HomeworkPageReferencePolicy.parse("10—12"))
    }

    @Test
    fun acceptsArabicIndicAndPersianDigits() {
        assertEquals(HomeworkPageReference(10, 12), HomeworkPageReferencePolicy.parse("١٠-١٢"))
        assertEquals(HomeworkPageReference(10, 12), HomeworkPageReferencePolicy.parse("۱۰-۱۲"))
    }

    @Test
    fun acceptsExplicitPageLabelsAndLabeledDetailsFallbackOnly() {
        assertEquals(HomeworkPageReference(10, 12), HomeworkPageReferencePolicy.parse("الصفحات: 10-12"))
        assertEquals(HomeworkPageReference(10), HomeworkPageReferencePolicy.parse(null, "صفحة 10"))
        assertEquals(HomeworkPageReference(10), HomeworkPageReferencePolicy.parse(null, "الصفحة: 10"))
        assertEquals(HomeworkPageReference(10, 12), HomeworkPageReferencePolicy.parse(null, "Pages: 10-12"))
        assertNull(HomeworkPageReferencePolicy.parse(null, "حل أسئلة الدرس 10-12"))
    }

    @Test
    fun malformedReversedZeroAndOverflowReferencesAreRejected() {
        listOf("", "10-", "-12", "12-10", "0", "10-0", "10-12-14", "1,2", "999999999999999999999")
            .forEach { assertNull("Expected '$it' to be rejected", HomeworkPageReferencePolicy.parse(it)) }
    }

    @Test
    fun invalidDedicatedPageFieldDoesNotFallBackToUnrelatedDetails() {
        assertNull(HomeworkPageReferencePolicy.parse("10 to 12", "صفحة 5"))
    }

    @Test
    fun equalRangeEndpointsAreRepresentedAsOnePage() {
        val result = HomeworkPageReferencePolicy.parse("10-10")

        assertEquals(HomeworkPageReference(10), result)
        assertFalse(result?.isRange ?: true)
    }
}