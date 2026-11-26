package com.magd.tanweer.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ExtraSectionsHubSearchPolicyTest {
    @Test
    fun pdfSearchMatchesBadgeKeywordsRegardlessOfCase() {
        assertTrue(ExtraSectionsHubSearchPolicy.matches("pdf", "المناهج pdf دراسية"))
        assertTrue(ExtraSectionsHubSearchPolicy.matches("PDF", "المناهج pdf دراسية"))
        assertTrue(ExtraSectionsHubSearchPolicy.matches("Pdf", "المناهج PDF دراسية"))
    }

    @Test
    fun trimsQueryAndSearchableText() {
        assertTrue(ExtraSectionsHubSearchPolicy.matches("  PDF  ", "  المكتبة PDF  "))
        assertFalse(ExtraSectionsHubSearchPolicy.matches("physics", "مكتبة الكتب والمناهج"))
    }

    @Test
    fun emptyQueryShowsAllItems() {
        assertTrue(ExtraSectionsHubSearchPolicy.matches("", "أي قسم"))
        assertTrue(ExtraSectionsHubSearchPolicy.matches("   ", "أي قسم"))
    }

    @Test
    fun matchingDoesNotDependOnTheDeviceDefaultLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertTrue(ExtraSectionsHubSearchPolicy.matches("I", "library"))
            assertTrue(ExtraSectionsHubSearchPolicy.matches("PDF", "pdf"))
        } finally {
            Locale.setDefault(original)
        }
    }
}
