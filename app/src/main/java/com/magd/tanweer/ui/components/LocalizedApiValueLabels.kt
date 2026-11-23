package com.magd.tanweer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.magd.tanweer.R
import com.magd.tanweer.util.ApiDisplayValuePolicy
import com.magd.tanweer.util.ApiDisplayValuePolicy.ChapterTokenKind

/** Maps server event-category values to localized presentation labels. */
@Composable
fun localizedEventCategoryLabel(rawCategory: String?): String {
    val resourceId = when (ApiDisplayValuePolicy.eventCategoryKey(rawCategory)) {
        "ACTIVITY" -> R.string.api_event_category_activity
        "COMPETITION" -> R.string.api_event_category_competition
        "TRIP" -> R.string.api_event_category_trip
        "ANNOUNCEMENT" -> R.string.api_event_category_announcement
        "HOLIDAY" -> R.string.api_event_category_holiday
        "CELEBRATION" -> R.string.api_event_category_celebration
        "WORKSHOP" -> R.string.api_event_category_workshop
        else -> null
    }
    if (resourceId != null) return stringResource(resourceId)

    val fallback = ApiDisplayValuePolicy.humanizeUnknownValue(rawCategory)
        ?: stringResource(R.string.api_value_not_available)
    return stringResource(R.string.api_value_unknown, fallback)
}

/** Maps server exam-type values to localized labels; unknown values never default to MONTHLY. */
@Composable
fun localizedExamTypeLabel(rawExamType: String?): String {
    val resourceId = when (ApiDisplayValuePolicy.examTypeKey(rawExamType)) {
        "MONTHLY" -> R.string.api_exam_type_monthly
        "ORAL" -> R.string.api_exam_type_oral
        "MIDTERM" -> R.string.api_exam_type_midterm
        "FINAL" -> R.string.api_exam_type_final
        else -> null
    }
    if (resourceId != null) return stringResource(resourceId)

    val fallback = ApiDisplayValuePolicy.humanizeUnknownValue(rawExamType)
        ?: stringResource(R.string.api_value_not_available)
    return stringResource(R.string.api_value_unknown, fallback)
}

/** Decodes URI-encoded chapter lists for display only; stored/API values remain untouched. */
@Composable
fun localizedChapterListLabel(rawChapters: String?): String {
    val tokens = ApiDisplayValuePolicy.parseChapterTokens(rawChapters)
    if (tokens.isEmpty()) return stringResource(R.string.exam_full_syllabus)

    val separator = stringResource(R.string.exam_chapter_list_separator)
    val labels = mutableListOf<String>()
    for (token in tokens) {
        val label = when (token.kind) {
            ChapterTokenKind.NUMBER -> stringResource(
                R.string.exam_chapter_number,
                token.value.toIntOrNull() ?: 0
            )
            ChapterTokenKind.FINAL_REVIEW -> stringResource(R.string.exam_chapter_final_review)
            ChapterTokenKind.TEXT -> token.value
        }
        labels += label
    }
    return labels.joinToString(separator)
}
