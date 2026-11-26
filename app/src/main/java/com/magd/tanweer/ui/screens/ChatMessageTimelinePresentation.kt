package com.magd.tanweer.ui.screens

import com.magd.tanweer.data.model.ChatMessageItem
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ChatMessagePresentation(
    val message: ChatMessageItem,
    val dayKey: String,
    val dateLabel: String,
    val timeLabel: String,
    val startsNewDay: Boolean
)

/**
 * Adds local-time date/time labels without changing message order or list indices.
 * Keeping exactly one presentation per message preserves deep-link and auto-scroll indices.
 */
fun buildChatMessagePresentations(
    messages: List<ChatMessageItem>,
    timeZone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault()
): List<ChatMessagePresentation> {
    if (messages.isEmpty()) return emptyList()

    val dayKeyFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        this.timeZone = timeZone
    }
    val dateFormatter = DateFormat.getDateInstance(DateFormat.FULL, locale).apply {
        this.timeZone = timeZone
    }
    val timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT, locale).apply {
        this.timeZone = timeZone
    }

    var previousDayKey: String? = null
    return messages.map { message ->
        val date = Date(message.timestamp)
        val dayKey = dayKeyFormatter.format(date)
        val startsNewDay = dayKey != previousDayKey
        previousDayKey = dayKey

        ChatMessagePresentation(
            message = message,
            dayKey = dayKey,
            dateLabel = dateFormatter.format(date),
            timeLabel = timeFormatter.format(date),
            startsNewDay = startsNewDay
        )
    }
}
