package com.magd.tanweer.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.local.HomeworkToggleResult
import com.magd.tanweer.data.model.*
import com.magd.tanweer.ui.NavigationTab
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

enum class MissedTimeRange(val label: String, val daysBack: Int) {
    SINCE_LAST_VISIT("منذ آخر دخول ⏳", 0),
    YESTERDAY("يوم أمس ⏪", 1),
    LAST_3_DAYS("آخر 3 أيام 📅", 3),
    THIS_WEEK("الأسبوع الحالي 🗓️", 7),
    LAST_TWO_WEEKS("آخر أسبوعين 📚", 14)
}

enum class CatchupCategoryFilter(val label: String, val icon: String) {
    ALL("الكل", "✨"),
    LESSONS("الدروس", "📝"),
    HOMEWORK("الواجبات", "📋"),
    EXAMS("الاختبارات", "🔴"),
    EVENTS("الفعاليات", "🎪")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatDidIMissScreen(
    viewModel: TanweerViewModel
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId ?: "" }
    val previousSessionTime by viewModel.previousSessionTimestamp.collectAsStateWithLifecycle()

    var selectedRange by remember { mutableStateOf(MissedTimeRange.SINCE_LAST_VISIT) }
    var selectedCategoryFilter by remember { mutableStateOf(CatchupCategoryFilter.ALL) }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val timeFormatter = remember { SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()) }
    val todayStr = remember { sdf.format(Date()) }

    val (startDateStr, endDateStr) = remember(selectedRange, previousSessionTime) {
        val today = sdf.format(Date())
        if (selectedRange == MissedTimeRange.SINCE_LAST_VISIT) {
            val sinceDate = sdf.format(Date(previousSessionTime))
            Pair(sinceDate, today)
        } else {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -selectedRange.daysBack)
            val from = sdf.format(cal.time)
            Pair(from, today)
        }
    }

    val allContents by viewModel.repository.getAllContents(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val allHomeworks by viewModel.repository.getHomeworks(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val allExams by viewModel.repository.getExams(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val allEvents by viewModel.repository.getEvents(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // 1. Filter Lessons documented in that range
    val missedLessons = remember(allContents, startDateStr, endDateStr) {
        allContents.filter { it.studyDate in startDateStr..endDateStr }
    }

    // 2. Filter Homeworks assigned in that range
    val missedHomeworks = remember(allHomeworks, startDateStr, endDateStr) {
        allHomeworks.filter { it.studyDate in startDateStr..endDateStr }
    }

    // 3. Filter Exams announced or occurring upcoming
    val relevantExams = remember(allExams, startDateStr, todayStr) {
        allExams.filter { it.examDate >= startDateStr }
    }

    // 4. Filter Events in that range or upcoming
    val relevantEvents = remember(allEvents, startDateStr) {
        allEvents.filter { it.eventDate >= startDateStr }
    }

    val totalCatchupItems = missedLessons.size + missedHomeworks.size + relevantExams.size + relevantEvents.size

    BackHandler {
        viewModel.navigateBack()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header Card
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = GlassSurface
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(CyanGlow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "ماذا فاتني أثناء الغياب؟ 🎒",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            val lastVisitStr = remember(previousSessionTime) {
                                try { timeFormatter.format(Date(previousSessionTime)) } catch (_: Exception) { "سابقاً" }
                            }
                            Text(
                                text = "ملخص الاستدراك الذكي (آخر دخول: $lastVisitStr)",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Time Range Selector Filter Pills
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(MissedTimeRange.values()) { range ->
                            val isSelected = range == selectedRange
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) CyanAccent else GlassSurface)
                                    .border(1.dp, if (isSelected) CyanAccent else GlassBorderSubtle, RoundedCornerShape(20.dp))
                                    .clickable { selectedRange = range }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = range.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) TextOnAccent else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Summary Statistics Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatPillCard(
                    icon = "📝",
                    count = missedLessons.size,
                    label = "دروس موثقة",
                    color = CyanAccent,
                    modifier = Modifier.weight(1f),
                    isSelected = selectedCategoryFilter == CatchupCategoryFilter.LESSONS,
                    onClick = {
                        selectedCategoryFilter = if (selectedCategoryFilter == CatchupCategoryFilter.LESSONS) CatchupCategoryFilter.ALL else CatchupCategoryFilter.LESSONS
                    }
                )
                StatPillCard(
                    icon = "📋",
                    count = missedHomeworks.size,
                    label = "واجبات",
                    color = WarmAmber,
                    modifier = Modifier.weight(1f),
                    isSelected = selectedCategoryFilter == CatchupCategoryFilter.HOMEWORK,
                    onClick = {
                        selectedCategoryFilter = if (selectedCategoryFilter == CatchupCategoryFilter.HOMEWORK) CatchupCategoryFilter.ALL else CatchupCategoryFilter.HOMEWORK
                    }
                )
                StatPillCard(
                    icon = "🔴",
                    count = relevantExams.size,
                    label = "اختبارات",
                    color = RubyRed,
                    modifier = Modifier.weight(1f),
                    isSelected = selectedCategoryFilter == CatchupCategoryFilter.EXAMS,
                    onClick = {
                        selectedCategoryFilter = if (selectedCategoryFilter == CatchupCategoryFilter.EXAMS) CatchupCategoryFilter.ALL else CatchupCategoryFilter.EXAMS
                    }
                )
                StatPillCard(
                    icon = "🎪",
                    count = relevantEvents.size,
                    label = "فعاليات",
                    color = EmeraldGreen,
                    modifier = Modifier.weight(1f),
                    isSelected = selectedCategoryFilter == CatchupCategoryFilter.EVENTS,
                    onClick = {
                        selectedCategoryFilter = if (selectedCategoryFilter == CatchupCategoryFilter.EVENTS) CatchupCategoryFilter.ALL else CatchupCategoryFilter.EVENTS
                    }
                )
            }
        }

        if (totalCatchupItems == 0) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "✨", fontSize = 32.sp)
                        Text(
                            text = "لا توجد مواد أو واجبات أو أحداث جديدة خلال هذه الفترة!",
                            fontSize = 14.sp,
                            color = EmeraldGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "أنت مواكب لكافة متطلبات الشعبة الدراسية بنجاح ✓",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 1. LESSONS & BOARD DOCUMENTATION SECTION
        // -------------------------------------------------------------
        if (selectedCategoryFilter in listOf(CatchupCategoryFilter.ALL, CatchupCategoryFilter.LESSONS) && missedLessons.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "📝", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "الدروس وشروحات السبورة الموثقة (${missedLessons.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent
                        )
                    }
                    TextButton(onClick = { viewModel.setTab(NavigationTab.TODAY) }) {
                        Text(text = "عرض الكل", color = CyanAccent, fontSize = 12.sp)
                    }
                }
            }

            items(missedLessons) { lesson ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurface
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassPill(
                                text = lesson.subjectName,
                                color = CyanAccent,
                                bgColor = CyanGlow
                            )
                            Text(
                                text = "📅 ${lesson.studyDate}",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = lesson.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (!lesson.description.isNullOrBlank()) {
                            Text(
                                text = lesson.description,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                maxLines = 2
                            )
                        }
                        if (lesson.media.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "📷 يتضمن ${lesson.media.size} صور موثقة للسبورة/الدفتر",
                                fontSize = 11.sp,
                                color = CyanAccent,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 2. HOMEWORK SECTION
        // -------------------------------------------------------------
        if (selectedCategoryFilter in listOf(CatchupCategoryFilter.ALL, CatchupCategoryFilter.HOMEWORK) && missedHomeworks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "📋", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "الواجبات والتكليفات المدرسية (${missedHomeworks.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarmAmber
                        )
                    }
                    TextButton(onClick = { viewModel.setTab(NavigationTab.HOMEWORK) }) {
                        Text(text = "عرض الكل", color = CyanAccent, fontSize = 12.sp)
                    }
                }
            }

            items(missedHomeworks) { hw ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurface
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                GlassPill(
                                    text = hw.subjectName,
                                    color = WarmAmber,
                                    bgColor = WarmAmber.copy(alpha = 0.15f)
                                )
                                Text(
                                    text = "تاريخ الحصة: ${hw.studyDate}",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = hw.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (!hw.details.isNullOrBlank()) {
                                Text(
                                    text = hw.details,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    maxLines = 2
                                )
                            }
                            if (hw.dueDate.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "⏳ موعد التسليم: ${hw.dueDate}",
                                    fontSize = 11.sp,
                                    color = WarmAmber,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                viewModel.toggleHomework(hw.id, hw.isCompleted) { res ->
                                    when (res) {
                                        is HomeworkToggleResult.SyncedWithServer -> {
                                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                        }
                                        is HomeworkToggleResult.QueuedOffline -> {
                                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                        }
                                        is HomeworkToggleResult.Failed -> {
                                            Toast.makeText(context, res.error, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(GlassSurface)
                        ) {
                            Icon(
                                imageVector = if (hw.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = "إكمال الواجب",
                                tint = if (hw.isCompleted) EmeraldGreen else TextMuted
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 3. EXAMS SECTION
        // -------------------------------------------------------------
        if (selectedCategoryFilter in listOf(CatchupCategoryFilter.ALL, CatchupCategoryFilter.EXAMS) && relevantExams.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🔴", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "الاختبارات المعلنة (${relevantExams.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = RubyRed
                        )
                    }
                    TextButton(onClick = { viewModel.setTab(NavigationTab.EXAMS) }) {
                        Text(text = "عرض الكل", color = CyanAccent, fontSize = 12.sp)
                    }
                }
            }

            items(relevantExams) { exam ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurface
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassPill(
                                text = exam.subjectName,
                                color = RubyRed,
                                bgColor = RubyRed.copy(alpha = 0.15f)
                            )
                            Text(
                                text = "📅 ${exam.examDate}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WarmAmber
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = exam.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (!exam.requiredChapters.isNullOrBlank()) {
                            Text(
                                text = "المقرر: ${exam.requiredChapters}",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                maxLines = 2
                            )
                        }
                        if (!exam.notes.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "ملاحظات: ${exam.notes}",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 4. EVENTS & ACTIVITIES SECTION
        // -------------------------------------------------------------
        if (selectedCategoryFilter in listOf(CatchupCategoryFilter.ALL, CatchupCategoryFilter.EVENTS) && relevantEvents.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🎪", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "الأنشطة والفعاليات المدرسية (${relevantEvents.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen
                        )
                    }
                    TextButton(onClick = { viewModel.setSubScreen(SubScreen.EVENTS) }) {
                        Text(text = "عرض الكل", color = CyanAccent, fontSize = 12.sp)
                    }
                }
            }

            items(relevantEvents) { event ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurface
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassPill(
                                text = event.category,
                                color = EmeraldGreen,
                                bgColor = EmeraldGreen.copy(alpha = 0.15f)
                            )
                            Text(
                                text = "📅 ${event.eventDate} ${event.timeStr ?: ""}".trim(),
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = event.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (!event.description.isNullOrBlank()) {
                            Text(
                                text = event.description,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                maxLines = 2
                            )
                        }
                        if (!event.location.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "📍 المكان: ${event.location}",
                                fontSize = 11.sp,
                                color = CyanAccent
                            )
                        }
                    }
                }
            }
        }

        // Quick Navigation Shortcuts
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassButton(
                    text = "توثيق درس جديد 📝",
                    onClick = { viewModel.openUploadDialog() },
                    modifier = Modifier.weight(1f)
                )
                GlassOutlinedButton(
                    text = "جدول الحصص 🗓️",
                    onClick = { viewModel.setSubScreen(SubScreen.SCHEDULE) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatPillCard(
    icon: String,
    count: Int,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) color.copy(alpha = 0.25f) else GlassSurface)
            .border(
                1.dp,
                if (isSelected) color else GlassBorderSubtle,
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = icon, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$count",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) color else TextPrimary
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = if (isSelected) color else TextSecondary,
                maxLines = 1
            )
        }
    }
}
