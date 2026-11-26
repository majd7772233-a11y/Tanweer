package com.magd.tanweer.ui.screens

import com.magd.tanweer.data.model.ChatMessageItem
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageTimelinePresentationTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun marksOnlyTheFirstMessageOfEachLocalDayAndFormatsItsTime() {
        val messages = listOf(
            message("first", timestamp(2026, Calendar.NOVEMBER, 26, 8, 5)),
            message("second", timestamp(2026, Calendar.NOVEMBER, 26, 11, 30)),
            message("third", timestamp(2026, Calendar.NOVEMBER, 27, 0, 15))
        )

        val presentation = buildChatMessagePresentations(messages, utc, Locale.US)

        assertEquals(listOf("first", "second", "third"), presentation.map { it.message.id })
        assertEquals(listOf(true, false, true), presentation.map { it.startsNewDay })
        assertEquals("8:05 AM", presentation[0].timeLabel)
        assertEquals("11:30 AM", presentation[1].timeLabel)
        assertEquals("12:15 AM", presentation[2].timeLabel)
        assertTrue(presentation[0].dateLabel.isNotBlank())
        assertTrue(presentation[2].dateLabel.isNotBlank())
        assertEquals("2026-11-26", presentation[0].dayKey)
        assertEquals("2026-11-27", presentation[2].dayKey)
    }

    @Test
    fun emptyHistoryProducesNoTimelineRows() {
        assertTrue(buildChatMessagePresentations(emptyList(), utc, Locale.US).isEmpty())
    }

    @Test
    fun timezoneControlsTheVisibleCalendarDay() {
        val nearUtcMidnight = timestamp(2026, Calendar.NOVEMBER, 26, 22, 30)
        val utcPresentation = buildChatMessagePresentations(
            listOf(message("utc", nearUtcMidnight)),
            utc,
            Locale.US
        )
        val adenPresentation = buildChatMessagePresentations(
            listOf(message("aden", nearUtcMidnight)),
            TimeZone.getTimeZone("Asia/Aden"),
            Locale.US
        )

        assertEquals("2026-11-26", utcPresentation.single().dayKey)
        assertEquals("2026-11-27", adenPresentation.single().dayKey)
    }

    private fun message(id: String, timestamp: Long) = ChatMessageItem(
        id = id,
        groupId = "group",
        senderId = "sender",
        senderName = "Sender",
        senderGradeSection = "Grade",
        text = id,
        timestamp = timestamp
    )

    private fun timestamp(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(utc, Locale.US).apply {
            clear()
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
