package com.magd.tanweer.util

import com.magd.tanweer.data.model.ScheduleSlot
import java.util.Calendar

sealed class AcademicDayStatus {
    object Weekend : AcademicDayStatus()
    object NoSchedule : AcademicDayStatus()
    data class BeforeSchool(val firstSlot: ScheduleSlot, val startTime: String) : AcademicDayStatus()
    data class InSession(
        val activeSlot: ScheduleSlot,
        val nextSlot: ScheduleSlot?,
        val startTime: String,
        val endTime: String
    ) : AcademicDayStatus()
    data class BreakTime(val nextSlot: ScheduleSlot, val nextStartTime: String) : AcademicDayStatus()
    object AfterSchool : AcademicDayStatus()
}

object AcademicTimeEngine {

    private val defaultPeriodTimes = listOf(
        1 to ("08:00" to "08:45"),
        2 to ("08:50" to "09:35"),
        3 to ("09:40" to "10:25"),
        4 to ("10:45" to "11:30"),
        5 to ("11:35" to "12:20"),
        6 to ("12:25" to "13:10"),
        7 to ("13:15" to "14:00")
    )

    fun parseTimeToMinutes(timeStr: String?): Int? {
        if (timeStr.isNullOrBlank()) return null
        return try {
            val parts = timeStr.trim().split(":")
            val h = parts[0].toInt()
            val m = if (parts.size > 1) parts[1].toInt() else 0
            h * 60 + m
        } catch (e: Exception) {
            null
        }
    }

    fun getSlotEffectiveTimes(slot: ScheduleSlot): Pair<String, String> {
        val defaultPair = defaultPeriodTimes.find { it.first == slot.slotOrder }?.second
            ?: ("08:00" to "08:45")
        val start = if (!slot.startTime.isNullOrBlank()) slot.startTime else defaultPair.first
        val end = if (!slot.endTime.isNullOrBlank()) slot.endTime else defaultPair.second
        return Pair(start, end)
    }

    fun getSlotMinutesRange(slot: ScheduleSlot): Pair<Int, Int> {
        val (startStr, endStr) = getSlotEffectiveTimes(slot)
        val startMin = parseTimeToMinutes(startStr) ?: (8 * 60 + (slot.slotOrder - 1) * 50)
        val endMin = parseTimeToMinutes(endStr) ?: (startMin + 45)
        return Pair(startMin, endMin)
    }

    fun computeDayStatus(
        slots: List<ScheduleSlot>,
        calendar: Calendar,
        isWeekend: Boolean
    ): AcademicDayStatus {
        if (isWeekend) return AcademicDayStatus.Weekend
        if (slots.isEmpty()) return AcademicDayStatus.NoSchedule

        val sorted = slots.sortedBy { it.slotOrder }
        val nowMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        val firstSlot = sorted.first()
        val (firstStartMin, _) = getSlotMinutesRange(firstSlot)
        val (firstStartStr, _) = getSlotEffectiveTimes(firstSlot)

        if (nowMinutes < firstStartMin) {
            return AcademicDayStatus.BeforeSchool(firstSlot, firstStartStr)
        }

        val lastSlot = sorted.last()
        val (_, lastEndMin) = getSlotMinutesRange(lastSlot)

        if (nowMinutes > lastEndMin) {
            return AcademicDayStatus.AfterSchool
        }

        // Check if currently in a specific slot
        for (i in sorted.indices) {
            val slot = sorted[i]
            val (startMin, endMin) = getSlotMinutesRange(slot)
            if (nowMinutes in startMin..endMin) {
                val nextSlot = if (i + 1 < sorted.size) sorted[i + 1] else null
                val (startStr, endStr) = getSlotEffectiveTimes(slot)
                return AcademicDayStatus.InSession(slot, nextSlot, startStr, endStr)
            }
        }

        // If not in a slot, we are in a break between slots
        val upcomingSlot = sorted.firstOrNull { slot ->
            val (startMin, _) = getSlotMinutesRange(slot)
            nowMinutes < startMin
        }

        if (upcomingSlot != null) {
            val (startStr, _) = getSlotEffectiveTimes(upcomingSlot)
            return AcademicDayStatus.BreakTime(upcomingSlot, startStr)
        }

        return AcademicDayStatus.AfterSchool
    }
}
