package com.magd.tanweer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfReaderNavigationPolicyTest {
    @Test
    fun restoresKnowledgeBaseRouteAndSelectedSubject() {
        val origin = PdfReaderReturnDestination(
            subScreen = SubScreen.SUBJECT_KNOWLEDGE_BASE,
            currentTab = NavigationTab.TODAY,
            knowledgeBaseSubjectId = "biology"
        )

        assertEquals(origin, PdfReaderNavigationPolicy.resolve(origin, NavigationTab.HOMEWORK))
    }

    @Test
    fun restoresGeneralLibraryInsteadOfKnowledgeBase() {
        val origin = PdfReaderReturnDestination(
            subScreen = SubScreen.LIBRARY,
            currentTab = NavigationTab.EXAMS,
            knowledgeBaseSubjectId = null
        )

        assertEquals(origin, PdfReaderNavigationPolicy.resolve(origin, NavigationTab.TODAY))
    }

    @Test
    fun restoresSearchAndOtherSubScreensWithoutRoutingToLibrary() {
        listOf(SubScreen.SEARCH, SubScreen.ACADEMIC_HISTORY, SubScreen.TIMELINE).forEach { screen ->
            val origin = PdfReaderReturnDestination(
                subScreen = screen,
                currentTab = NavigationTab.ISSUES,
                knowledgeBaseSubjectId = null
            )

            assertEquals(origin, PdfReaderNavigationPolicy.resolve(origin, NavigationTab.TODAY))
        }
    }

    @Test
    fun restoresRootTabWhenPdfWasOpenedFromARootTab() {
        val origin = PdfReaderReturnDestination(
            subScreen = SubScreen.NONE,
            currentTab = NavigationTab.HOMEWORK,
            knowledgeBaseSubjectId = null
        )

        assertEquals(origin, PdfReaderNavigationPolicy.resolve(origin, NavigationTab.TODAY))
    }

    @Test
    fun missingOrInvalidPdfOriginFallsBackToLibraryWithoutLosingCurrentTab() {
        val missing = PdfReaderNavigationPolicy.resolve(null, NavigationTab.CALENDAR)
        assertEquals(SubScreen.LIBRARY, missing.subScreen)
        assertEquals(NavigationTab.CALENDAR, missing.currentTab)
        assertEquals(null, missing.knowledgeBaseSubjectId)

        val invalid = PdfReaderReturnDestination(
            subScreen = SubScreen.PDF_VIEWER,
            currentTab = NavigationTab.EXAMS,
            knowledgeBaseSubjectId = "stale-subject"
        )
        val resolved = PdfReaderNavigationPolicy.resolve(invalid, NavigationTab.TODAY)
        assertEquals(SubScreen.LIBRARY, resolved.subScreen)
        assertEquals(NavigationTab.EXAMS, resolved.currentTab)
        assertEquals(null, resolved.knowledgeBaseSubjectId)
    }
}