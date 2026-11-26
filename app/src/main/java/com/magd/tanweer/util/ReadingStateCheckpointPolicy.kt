package com.magd.tanweer.util

/** Immutable snapshot persisted to Room for one PDF reader state change. */
data class ReadingStateCheckpoint(
    val lastPageIndex: Int,
    val zoom: Float,
    val theme: String,
    val totalStudySeconds: Long,
    val uniquePagesCount: Int
)

/** Separates the current reading position from the unique-page counter. */
object ReadingStateCheckpointPolicy {
    fun capture(
        currentPageIndex: Int,
        uniquePageIndices: Set<Int>,
        zoom: Float,
        theme: String,
        totalStudySeconds: Long
    ): ReadingStateCheckpoint = ReadingStateCheckpoint(
        lastPageIndex = currentPageIndex.coerceAtLeast(0),
        zoom = zoom,
        theme = theme,
        totalStudySeconds = totalStudySeconds.coerceAtLeast(0L),
        uniquePagesCount = uniquePageIndices.size
    )
}
