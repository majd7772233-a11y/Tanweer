package com.magd.tanweer.ui

/** The destination that must be restored after the PDF reader closes. */
data class PdfReaderReturnDestination(
    val subScreen: SubScreen,
    val currentTab: NavigationTab,
    val knowledgeBaseSubjectId: String?
)

/** Resolves a safe return target while preventing a PDF reader from returning to itself. */
object PdfReaderNavigationPolicy {
    fun resolve(
        origin: PdfReaderReturnDestination?,
        fallbackTab: NavigationTab
    ): PdfReaderReturnDestination {
        val destination = origin ?: return libraryFallback(fallbackTab)
        return if (destination.subScreen == SubScreen.PDF_VIEWER) {
            libraryFallback(destination.currentTab)
        } else {
            destination
        }
    }

    private fun libraryFallback(tab: NavigationTab) = PdfReaderReturnDestination(
        subScreen = SubScreen.LIBRARY,
        currentTab = tab,
        knowledgeBaseSubjectId = null
    )
}