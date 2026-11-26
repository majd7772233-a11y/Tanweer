package com.magd.tanweer.util

/** Independent page sets for the current session, local day, book lifetime and auto-scroll. */
enum class ReadingPageVisitSource { HUMAN, AUTO_SCROLL }

data class ReadingPageActivity(
    val sessionHumanPages: Set<Int> = emptySet(),
    val dailyHumanPages: Set<Int> = emptySet(),
    val lifetimeUniquePages: Set<Int> = emptySet(),
    val autoScrolledPages: Set<Int> = emptySet()
)

object ReadingPageActivityPolicy {
    fun recordHumanPage(activity: ReadingPageActivity, pageIndex: Int): ReadingPageActivity {
        if (pageIndex < 0) return activity
        return activity.copy(
            sessionHumanPages = activity.sessionHumanPages + pageIndex,
            dailyHumanPages = activity.dailyHumanPages + pageIndex,
            lifetimeUniquePages = activity.lifetimeUniquePages + pageIndex
        )
    }

    fun recordAutoScrolledPage(activity: ReadingPageActivity, pageIndex: Int): ReadingPageActivity {
        if (pageIndex < 0) return activity
        return activity.copy(autoScrolledPages = activity.autoScrolledPages + pageIndex)
    }

    fun pagesPerHour(sessionHumanPageCount: Int, sessionSeconds: Long): Int {
        if (sessionHumanPageCount <= 0 || sessionSeconds <= 60L) return 0
        return ((sessionHumanPageCount.toDouble() * 3600.0) / sessionSeconds.toDouble())
            .toInt()
            .coerceAtLeast(0)
    }

    fun dailyStorageKey(bookId: String, localDate: String): String =
        "daily_human_unique_pages_${bookId}_${localDate}"

    fun parsePageIndices(raw: String?): Set<Int> = raw
        ?.split(',')
        ?.mapNotNull { it.trim().toIntOrNull()?.takeIf { index -> index >= 0 } }
        ?.toSet()
        .orEmpty()

    fun serializePageIndices(pageIndices: Set<Int>): String =
        pageIndices.filter { it >= 0 }.sorted().joinToString(",")
}
