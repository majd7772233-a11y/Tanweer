package com.magd.tanweer.util

/** A validated, one-based page reference entered for a homework item. */
data class HomeworkPageReference(
    val firstPage: Int,
    val lastPage: Int = firstPage
) {
    val isRange: Boolean get() = firstPage != lastPage
    val firstPageIndex: Int get() = firstPage - 1
}

/**
 * Parses explicit homework page references without concatenating digits.
 * Examples: "10", "10-12", "صفحات: 10–12".
 */
object HomeworkPageReferencePolicy {
    private val labelPrefix = Regex(
        """^(?:الصفحة|الصفحات|الصفحات?|صفحات|صفحة|صفحه|ص|pages?|page|p\.?)\s*[:：]?\s*(.*?)$""",
        RegexOption.IGNORE_CASE
    )
    private val singlePage = Regex("""^(\d+)$""")
    private val pageRange = Regex("""^(\d+)\s*[-–—]\s*(\d+)$""")

    /**
     * Prefer the dedicated pageNumbers field. Only inspect details as a fallback
     * when it starts with an explicit page label, so homework text containing
     * unrelated numbers is never mistaken for a PDF destination.
     */
    fun parse(pageNumbers: String?, details: String? = null): HomeworkPageReference? {
        val explicitPages = pageNumbers?.trim()?.takeIf { it.isNotEmpty() }
        if (explicitPages != null) return parseExplicitValue(explicitPages)

        val detailText = details?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val labeledValue = labelPrefix.matchEntire(normalizeDigits(detailText))
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?: return null
        return parseNumericValue(labeledValue)
    }

    private fun parseExplicitValue(rawValue: String): HomeworkPageReference? {
        val normalized = normalizeDigits(rawValue)
        val value = labelPrefix.matchEntire(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?: normalized
        return parseNumericValue(value)
    }

    private fun parseNumericValue(value: String): HomeworkPageReference? {
        val range = pageRange.matchEntire(value)
        if (range != null) {
            val first = range.groupValues[1].toIntOrNull() ?: return null
            val last = range.groupValues[2].toIntOrNull() ?: return null
            if (first < 1 || last < first) return null
            return HomeworkPageReference(firstPage = first, lastPage = last)
        }

        val single = singlePage.matchEntire(value) ?: return null
        val page = single.groupValues[1].toIntOrNull() ?: return null
        if (page < 1) return null
        return HomeworkPageReference(firstPage = page)
    }

    private fun normalizeDigits(value: String): String = buildString(value.length) {
        value.forEach { char ->
            val asciiDigit = when (char) {
                in '٠'..'٩' -> '0' + (char - '٠')
                in '۰'..'۹' -> '0' + (char - '۰')
                else -> char
            }
            append(asciiDigit)
        }
    }
}