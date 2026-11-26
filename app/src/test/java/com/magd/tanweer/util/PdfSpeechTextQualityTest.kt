package com.magd.tanweer.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSpeechTextQualityTest {
    @Test
    fun acceptsNormalArabicAndEnglishText() {
        assertTrue(PdfSpeechTextQuality.isSafeForSpeech("تتكون الخلية من نواة وعضيات متعددة."))
        assertTrue(PdfSpeechTextQuality.isSafeForSpeech("The cell contains a nucleus and several organelles."))
    }
    @Test
    fun acceptsMathOnlyPages() {
        assertTrue(PdfSpeechTextQuality.isSafeForSpeech("2 + 2 = 4"))
        assertTrue(PdfSpeechTextQuality.isSafeForSpeech("٣ × ٥ = ١٥"))
    }

    @Test
    fun rejectsReplacementAndControlCharacters() {
        assertFalse(PdfSpeechTextQuality.isSafeForSpeech("تتكون الخلية من نواة \uFFFD وعضيات."))
        assertFalse(PdfSpeechTextQuality.isSafeForSpeech("تتكون الخلية \u0001 من نواة وعضيات."))
        assertFalse(PdfSpeechTextQuality.isSafeForSpeech("تتكون الخلية \u0085 من نواة وعضيات."))
    }

    @Test
    fun rejectsKnownPdfMojibakePattern() {
        assertFalse(PdfSpeechTextQuality.isSafeForSpeech("ÁU? ?O? ÁU? ?O?"))
        assertFalse(PdfSpeechTextQuality.isSafeForSpeech("الخلية ÁU? ?O? الخلية ÁU? ?O?"))
    }

    @Test
    fun acceptsArabicQuestionsWithoutMojibakeMarkers() {
        assertTrue(PdfSpeechTextQuality.isSafeForSpeech("ما وظيفة النواة؟ وما دور الغشاء؟ وكيف تنتقل المواد؟"))
    }

    @Test
    fun acceptsScientificText() {
        assertTrue(PdfSpeechTextQuality.isSafeForSpeech("DNA وRNA carry genetic information."))
    }
}