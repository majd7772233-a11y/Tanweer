package com.magd.tanweer.util

import java.net.URLDecoder
import java.util.Locale

/** Presentation-only mapping for API enums and URI-encoded chapter lists. */
object ApiDisplayValuePolicy {
    enum class ChapterTokenKind { NUMBER, FINAL_REVIEW, TEXT }

    data class ChapterToken(
        val kind: ChapterTokenKind,
        val value: String
    )

    private val eventCategories = setOf(
        "ACTIVITY",
        "COMPETITION",
        "TRIP",
        "ANNOUNCEMENT",
        "HOLIDAY",
        "CELEBRATION",
        "WORKSHOP"
    )

    private val examTypes = setOf("MONTHLY", "ORAL", "MIDTERM", "FINAL")

    fun eventCategoryKey(rawValue: String?): String? = normalizeEnumKey(rawValue)
        .takeIf { it in eventCategories }

    fun examTypeKey(rawValue: String?): String? {
        val normalized = normalizeEnumKey(rawValue)
        // QUIZ is retired; historical rows are presented as the supported MONTHLY type.
        val canonical = if (normalized == "QUIZ") "MONTHLY" else normalized
        return canonical.takeIf { it in examTypes }
    }


    fun humanizeUnknownValue(rawValue: String?): String? {
        val decoded = decodeUriComponent(rawValue.orEmpty())
            .trim()
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
        if (decoded.isBlank()) return null

        return decoded.split(' ').joinToString(" ") { word ->
            if (word.any { it in '\u0600'..'\u06FF' }) {
                word
            } else {
                word.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
        }
    }

    fun parseChapterTokens(rawValue: String?): List<ChapterToken> {
        if (rawValue.isNullOrBlank()) return emptyList()

        return decodeUriComponent(rawValue)
            .split(Regex("[,;\\r\\n]+"))
            .mapNotNull { rawToken ->
                val token = rawToken.trim().trim('_').trim()
                if (token.isBlank()) return@mapNotNull null

                val normalized = token
                    .replace(Regex("\\s+"), "_")
                    .uppercase(Locale.ROOT)
                val chapterNumber = Regex("^(?:CH|CHAPTER)[_-]?(\\d+)$")
                    .matchEntire(normalized)
                    ?.groupValues
                    ?.getOrNull(1)

                when {
                    chapterNumber != null -> ChapterToken(ChapterTokenKind.NUMBER, chapterNumber)
                    normalized in setOf("FINAL_REVIEW", "FINAL_REVISION", "FINAL_RECAP") ->
                        ChapterToken(ChapterTokenKind.FINAL_REVIEW, "")
                    else -> ChapterToken(
                        ChapterTokenKind.TEXT,
                        humanizeUnknownValue(token).orEmpty()
                    ).takeIf { it.value.isNotBlank() }
                }
            }
    }

    /** Decode a URI component without converting a literal plus sign into a space. */
    fun decodeUriComponent(rawValue: String): String = runCatching {
        URLDecoder.decode(rawValue.replace("+", "%2B"), Charsets.UTF_8.name())
    }.getOrElse {
        rawValue
            .replace("%2C", ",", ignoreCase = true)
            .replace("%20", " ", ignoreCase = true)
    }

    private fun normalizeEnumKey(rawValue: String?): String = rawValue
        .orEmpty()
        .trim()
        .uppercase(Locale.ROOT)
        .replace('-', '_')
        .replace(' ', '_')
}
