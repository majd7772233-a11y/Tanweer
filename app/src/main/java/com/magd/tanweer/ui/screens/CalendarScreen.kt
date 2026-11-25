package com.magd.tanweer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.R
import com.magd.tanweer.data.model.*
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.GroupSyncSource
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*
import com.magd.tanweer.util.HomeworkTaskKind
import com.magd.tanweer.util.HomeworkTaskTypePolicy
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: TanweerViewModel
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId ?: "" }
    val groupSyncScopeIds by viewModel.groupSyncScopeIds.collectAsStateWithLifecycle()
    val calendarSyncState by viewModel.calendarSyncState.collectAsStateWithLifecycle()
    val calendarSyncMonthScope by viewModel.calendarSyncMonthScope.collectAsStateWithLifecycle()
    var currentCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDateStr by remember { mutableStateOf(TanweerViewModel.getTodayDateString()) }
    var showAddActionSheet by remember { mutableStateOf(false) }
    val monthQueryFormat = remember { SimpleDateFormat("yyyy-MM", Locale.US) }
    val currentCalendarMonth = remember(currentCalendar.timeInMillis) { monthQueryFormat.format(currentCalendar.time) }
    val scopedCalendarSyncState = syncStateForScope(calendarSyncState, groupSyncScopeIds[GroupSyncSource.CALENDAR], activeGroupId)
    val hasCalendarSnapshot = hasAuthoritativeCalendarMonthSnapshot(scopedCalendarSyncState, calendarSyncMonthScope, currentCalendarMonth)

    val dayDetailsSyncState by viewModel.dayDetailsSyncState.collectAsStateWithLifecycle()
    val dayDetailsSyncGroupId by viewModel.dayDetailsSyncGroupId.collectAsStateWithLifecycle()
    val dayDetailsSyncDate by viewModel.dayDetailsSyncDate.collectAsStateWithLifecycle()
    val scopedDayDetailsSyncState = syncStateForDayScope(
        dayDetailsSyncState,
        dayDetailsSyncGroupId,
        dayDetailsSyncDate,
        activeGroupId,
        selectedDateStr,
    )
    val hasDayDetailsSnapshot = hasAuthoritativeSnapshot(scopedDayDetailsSyncState)
    val hasConfirmedEmptyDay = shouldShowConfirmedCalendarEmpty(hasCalendarSnapshot, hasDayDetailsSnapshot)

    var showAddHolidayDialog by remember { mutableStateOf(false) }
    var holidayTitle by remember { mutableStateOf("") }
    var holidayReason by remember { mutableStateOf("") }
    var isSchoolWideHoliday by remember { mutableStateOf(true) }
    var holidayError by remember { mutableStateOf<String?>(null) }
    var isSubmittingHoliday by remember { mutableStateOf(false) }

    var activeViewingContent by remember { mutableStateOf<ContentItem?>(null) }
    var activeViewingHomework by remember { mutableStateOf<HomeworkItem?>(null) }
    var activeViewingExam by remember { mutableStateOf<ExamItem?>(null) }
    var activeViewingEvent by remember { mutableStateOf<SchoolEventItem?>(null) }

    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale("ar")) }
    val dayFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    LaunchedEffect(activeGroupId, currentCalendar.timeInMillis) {
        if (activeGroupId.isNotBlank()) {
            val ym = monthQueryFormat.format(currentCalendar.time)
            viewModel.syncCalendarMonth(activeGroupId, ym)
        }
    }

    LaunchedEffect(activeGroupId, selectedDateStr) {
        if (activeGroupId.isNotBlank() && selectedDateStr.isNotBlank()) {
            viewModel.syncDayDetails(activeGroupId, selectedDateStr)
        }
    }

    fun changeCalendarMonth(monthOffset: Int) {
        val next = currentCalendar.clone() as Calendar
        next.set(Calendar.DAY_OF_MONTH, 1)
        next.add(Calendar.MONTH, monthOffset)
        currentCalendar = next

        val targetMonth = monthQueryFormat.format(next.time)
        val nextSelectedDate = CalendarMonthPolicy.resolveSelectedDate(selectedDateStr, targetMonth)
        selectedDateStr = nextSelectedDate
        viewModel.setSelectedDate(nextSelectedDate)
    }

    // Day content queries
    val dayContents by viewModel.repository.getDayContents(activeGroupId, selectedDateStr)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val allContents by viewModel.repository.getAllContents(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val homeworks by viewModel.repository.getHomeworks(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val dayAssignments = remember(homeworks, selectedDateStr) {
        homeworks.filter { it.studyDate == selectedDateStr || it.dueDate == selectedDateStr }
    }
    val calendarHomeworkCount = remember(homeworks, currentCalendarMonth) {
        CalendarMonthPolicy.countItemsInMonth(
            homeworks.filter { HomeworkTaskTypePolicy.isHomework(it.taskType) },
            currentCalendarMonth
        ) { listOf(it.studyDate, it.dueDate) }
    }
    val calendarProjectCount = remember(homeworks, currentCalendarMonth) {
        CalendarMonthPolicy.countItemsInMonth(
            homeworks.filter { HomeworkTaskTypePolicy.isTask(it.taskType) },
            currentCalendarMonth
        ) { listOf(it.studyDate, it.dueDate) }
    }
    val calendarOtherAssignmentCount = remember(homeworks, currentCalendarMonth) {
        CalendarMonthPolicy.countItemsInMonth(
            homeworks.filter {
                HomeworkTaskTypePolicy.classify(it.taskType) == HomeworkTaskKind.UNKNOWN
            },
            currentCalendarMonth
        ) { listOf(it.studyDate, it.dueDate) }
    }
    val exams by viewModel.repository.getExams(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val calendarExamCount = remember(exams, currentCalendarMonth) {
        CalendarMonthPolicy.countItemsInMonth(exams, currentCalendarMonth) { listOf(it.examDate) }
    }
    val dayExams = remember(exams, selectedDateStr) {
        exams.filter { it.examDate == selectedDateStr }
    }

    val events by viewModel.repository.getEvents(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val calendarEventCount = remember(events, currentCalendarMonth) {
        CalendarMonthPolicy.countItemsInMonth(events, currentCalendarMonth) { listOf(it.eventDate) }
    }
    val dayEvents = remember(events, selectedDateStr) {
        events.filter { it.eventDate == selectedDateStr }
    }

    // Days in current month
    val daysInMonth = remember(currentCalendar.timeInMillis, exams, homeworks, events, allContents) {
        val cal = currentCalendar.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sun, 2 = Mon ...

        val list = mutableListOf<CalendarDayItem>()
        // Blank offset slots (0 = Sun based)
        val offset = (firstDayOfWeek - 1) % 7
        for (i in 0 until offset) {
            list.add(CalendarDayItem(dayNumber = 0, dateStr = "", isCurrentMonth = false))
        }

        val todayStr = TanweerViewModel.getTodayDateString()
        for (day in 1..maxDays) {
            cal.set(Calendar.DAY_OF_MONTH, day)
            val dStr = dayFormat.format(cal.time)
            list.add(
                CalendarDayItem(
                    dayNumber = day,
                    dateStr = dStr,
                    isCurrentMonth = true,
                    isToday = (dStr == todayStr),
                    hasLesson = allContents.any { it.studyDate == dStr },
                    hasExam = exams.any { it.examDate == dStr },
                    hasHomework = homeworks.any {
                        HomeworkTaskTypePolicy.isHomework(it.taskType) &&
                            (it.studyDate == dStr || it.dueDate == dStr)
                    },
                    hasTask = homeworks.any {
                        HomeworkTaskTypePolicy.isTask(it.taskType) &&
                            (it.studyDate == dStr || it.dueDate == dStr)
                    },
                    hasOtherAssignment = homeworks.any {
                        HomeworkTaskTypePolicy.classify(it.taskType) == HomeworkTaskKind.UNKNOWN &&
                            (it.studyDate == dStr || it.dueDate == dStr)
                    },
                    hasEvent = events.any { it.eventDate == dStr },
                    hasHoliday = events.any { it.eventDate == dStr && it.category == "HOLIDAY" }
                )
            )
        }
        list
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if ((dayContents.isNotEmpty() || dayAssignments.isNotEmpty() || dayExams.isNotEmpty() || dayEvents.isNotEmpty()) &&
            (scopedDayDetailsSyncState is SyncState.Error || scopedDayDetailsSyncState is SyncState.OfflineCached)) {
            item {
                SyncStatusBar(
                    syncState = scopedDayDetailsSyncState,
                    onRefresh = { viewModel.syncDayDetails(activeGroupId, selectedDateStr) }
                )
            }
        }

        // Month Navigation Bar
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = GlassSurface
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { changeCalendarMonth(-1) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "الشهر السابق",
                            tint = CyanAccent
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = monthYearFormat.format(currentCalendar.time),
                            fontSize = tanweerTextSize(17f),
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "التقويم الدراسي الزجاجي",
                            fontSize = tanweerTextSize(11f),
                            color = TextSecondary
                        )
                    }

                    IconButton(onClick = { changeCalendarMonth(1) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "الشهر التالي",
                            tint = CyanAccent
                        )
                    }
                }
            }
        }

        // Calendar Quick Highlights Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val curMonthPrefix = remember(currentCalendar.timeInMillis) { monthQueryFormat.format(currentCalendar.time) }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(EmeraldGreen.copy(alpha = 0.12f))
                        .border(1.dp, EmeraldGreen.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📖 تفاصيل الدروس", fontSize = tanweerTextSize(11f), color = EmeraldGreen, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(RubyRed.copy(alpha = 0.12f))
                        .border(1.dp, RubyRed.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🔴 ${if (hasCalendarSnapshot) calendarExamCount.toString() else "—"} اختبارات", fontSize = tanweerTextSize(11f), color = RubyRed, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(WarmAmber.copy(alpha = 0.12f))
                        .border(1.dp, WarmAmber.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (hasCalendarSnapshot) stringResource(R.string.calendar_count_homework, calendarHomeworkCount) else "—",
                            fontSize = tanweerTextSize(10f),
                            color = WarmAmber,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (hasCalendarSnapshot) stringResource(R.string.calendar_count_projects, calendarProjectCount) else "—",
                            fontSize = tanweerTextSize(10f),
                            color = PurpleAccent,
                            fontWeight = FontWeight.Bold
                        )
                        if (hasCalendarSnapshot && calendarOtherAssignmentCount > 0) {
                            Text(
                                text = stringResource(R.string.calendar_count_other_assignments, calendarOtherAssignmentCount),
                                fontSize = tanweerTextSize(9f),
                                color = TextMuted
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PurpleAccent.copy(alpha = 0.15f))
                        .border(1.dp, PurpleAccent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .clickable { viewModel.setSubScreen(SubScreen.EVENTS) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🎪 ${if (hasCalendarSnapshot) calendarEventCount.toString() else "—"} فعاليات", fontSize = tanweerTextSize(11f), color = PurpleAccent, fontWeight = FontWeight.Bold)
                        Text("‹", fontSize = tanweerTextSize(13f), color = PurpleAccent)
                    }
                }
            }
        }

        // Calendar Day Headers (الأحد -> السبت)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf("أحد", "إثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت").forEach { name ->
                    Text(
                        text = name,
                        fontSize = tanweerTextSize(12f),
                        fontWeight = FontWeight.Bold,
                        color = if (name == "جمعة" || name == "سبت") TextMuted else CyanAccent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Calendar Grid
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MidnightSurface
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(daysInMonth) { item ->
                        if (!item.isCurrentMonth) {
                            Box(modifier = Modifier.size(36.dp))
                        } else {
                            val isSelected = item.dateStr == selectedDateStr
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> CyanAccent
                                            item.isToday -> CyanGlow
                                            item.hasHoliday -> EmeraldGreen.copy(alpha = 0.22f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        when {
                                            isSelected -> CyanAccent
                                            item.isToday -> CyanAccent
                                            item.hasHoliday -> EmeraldGreen.copy(alpha = 0.8f)
                                            else -> Color.Transparent
                                        },
                                        CircleShape
                                    )
                                    .clickable {
                                        selectedDateStr = item.dateStr
                                        viewModel.setSelectedDate(item.dateStr)
                                        viewModel.syncDayDetails(activeGroupId, item.dateStr)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = item.dayNumber.toString(),
                                        fontSize = tanweerTextSize(13f),
                                        fontWeight = if (isSelected || item.isToday || item.hasHoliday) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSelected -> TextOnAccent
                                            item.hasHoliday -> EmeraldGreen
                                            else -> TextPrimary
                                        }
                                    )
                                    // Activity indicators
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        if (item.hasHoliday) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(EmeraldGreen)
                                            )
                                        }
                                        if (item.hasLesson) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(EmeraldGreen)
                                            )
                                        }
                                        if (item.hasExam) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(RubyRed)
                                            )
                                        }
                                        if (item.hasHomework) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(WarmAmber)
                                            )
                                        }
                                        if (item.hasTask) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(PurpleAccent)
                                            )
                                        }
                                        if (item.hasOtherAssignment) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(TextMuted)
                                            )
                                        }
                                        if (item.hasEvent && !item.hasHoliday) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(PurpleAccent)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Day Details Title & Backdated Add Button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "📖 تفاصيل: ${TanweerViewModel.getFormattedArabicDate(selectedDateStr)}",
                        fontSize = tanweerTextSize(15f),
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                    Text(
                        text = stringResource(R.string.calendar_day_add_description),
                        fontSize = tanweerTextSize(11f),
                        color = TextSecondary
                    )
                }

                GlassButton(
                    text = "إضافة لليوم",
                    icon = Icons.Default.Add,
                    onClick = {
                        viewModel.setSelectedDate(selectedDateStr)
                        showAddActionSheet = true
                    },
                    modifier = Modifier.height(36.dp)
                )
            }
        }

        // Selected Day Holiday Banner (Point 35)
        val selectedDayHoliday = dayEvents.find { it.category == "HOLIDAY" }
        if (selectedDayHoliday != null) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = EmeraldGreen.copy(alpha = 0.7f),
                    backgroundColor = EmeraldGreen.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🌴", fontSize = tanweerTextSize(32f))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "إجازة مدرسية رسمية",
                                    fontSize = tanweerTextSize(14f),
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldGreen
                                )
                                GlassPill(
                                    text = if (selectedDayHoliday.groupId == "all") "كامل المدرسة 🏫" else "الشعبة فقط",
                                    color = EmeraldGreen,
                                    bgColor = EmeraldGreen.copy(alpha = 0.2f)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = selectedDayHoliday.title,
                                fontSize = tanweerTextSize(13f),
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            if (!selectedDayHoliday.description.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "المبرر الإداري: ${selectedDayHoliday.description}",
                                    fontSize = tanweerTextSize(11f),
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Selected Day Exams
        if (dayExams.isNotEmpty()) {
            items(dayExams) { exam ->
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeViewingExam = exam },
                    borderColor = RubyRed.copy(alpha = 0.5f),
                    backgroundColor = GlassSurface
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔴", fontSize = tanweerTextSize(16f))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "اختبار ${exam.subjectName}: ${exam.title}",
                                    fontSize = tanweerTextSize(14f),
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            if (!exam.requiredChapters.isNullOrBlank()) {
                                Text(
                                    text = "الفصول المقررة: ${localizedChapterListLabel(exam.requiredChapters)}",
                                    fontSize = tanweerTextSize(12f),
                                    color = RubyRed,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Text(
                                text = "اضغط لعرض تفاصيل ومقرر الاختبار 🔍",
                                fontSize = tanweerTextSize(10f),
                                color = TextMuted,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        GlassPill(text = "اختبار", color = RubyRed, bgColor = Color(0x33FF3366))
                    }
                }
            }
        }

        // Selected Day Assignments: distinguish HOMEWORK, TASK projects and unknown types.
        if (dayAssignments.isNotEmpty()) {
            items(dayAssignments, key = { it.id }) { hw ->
                val taskKind = HomeworkTaskTypePolicy.classify(hw.taskType)
                val typeLabel = when (taskKind) {
                    HomeworkTaskKind.HOMEWORK -> R.string.homework_type_label_homework
                    HomeworkTaskKind.TASK -> R.string.homework_type_label_project
                    HomeworkTaskKind.UNKNOWN -> R.string.homework_type_label_other
                }
                val typeColor = when (taskKind) {
                    HomeworkTaskKind.HOMEWORK -> WarmAmber
                    HomeworkTaskKind.TASK -> PurpleAccent
                    HomeworkTaskKind.UNKNOWN -> TextMuted
                }
                val typeIcon = when (taskKind) {
                    HomeworkTaskKind.HOMEWORK -> "📝"
                    HomeworkTaskKind.TASK -> "🎯"
                    HomeworkTaskKind.UNKNOWN -> "📌"
                }
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeViewingHomework = hw },
                    borderColor = typeColor.copy(alpha = 0.4f),
                    backgroundColor = GlassSurface
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$typeIcon ${stringResource(typeLabel)} ${hw.subjectName}: ${hw.title}",
                                fontSize = tanweerTextSize(14f),
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = stringResource(R.string.calendar_assignment_due_date, hw.dueDate),
                                fontSize = tanweerTextSize(12f),
                                color = typeColor
                            )
                            Text(
                                text = stringResource(R.string.calendar_assignment_details_hint),
                                fontSize = tanweerTextSize(10f),
                                color = TextMuted,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Checkbox(
                            checked = hw.isCompleted,
                            onCheckedChange = { viewModel.toggleHomework(hw.id, hw.isCompleted) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = EmeraldGreen,
                                uncheckedColor = TextSecondary
                            )
                        )
                    }
                }
            }
        }

        // Selected Day Events
        if (dayEvents.isNotEmpty()) {
            items(dayEvents) { ev ->
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeViewingEvent = ev },
                    borderColor = PurpleAccent.copy(alpha = 0.5f),
                    backgroundColor = GlassSurface
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when (ev.category.uppercase()) {
                                        "ACTIVITY" -> "🏆"
                                        "CELEBRATION" -> "🎉"
                                        "HOLIDAY" -> "🏖️"
                                        "WORKSHOP" -> "💡"
                                        else -> "🎪"
                                    },
                                    fontSize = tanweerTextSize(16f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "فعالية: ${ev.title}",
                                    fontSize = tanweerTextSize(14f),
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            if (!ev.description.isNullOrBlank()) {
                                Text(
                                    text = ev.description,
                                    fontSize = tanweerTextSize(12f),
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            if (!ev.location.isNullOrBlank() || !ev.timeStr.isNullOrBlank()) {
                                Text(
                                    text = "${ev.timeStr?.let { "⏰ $it " } ?: ""}${ev.location?.let { "📍 $it" } ?: ""}",
                                    fontSize = tanweerTextSize(11f),
                                    color = PurpleAccent,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Text(
                                text = "اضغط لعرض تفاصيل الفعالية 🔍",
                                fontSize = tanweerTextSize(10f),
                                color = TextMuted,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        GlassPill(text = "فعالية 🎪", color = PurpleAccent, bgColor = PurpleAccent.copy(alpha = 0.15f))
                    }
                }
            }
        }

        // Selected Day Lessons
        if (dayContents.isNotEmpty()) {
            items(dayContents) { content ->
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeViewingContent = content },
                    backgroundColor = GlassSurface
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${content.subjectIcon} درس ${content.subjectName}: ${content.title}",
                                fontSize = tanweerTextSize(14f),
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            GlassPill(text = "درس موثق", color = EmeraldGreen, bgColor = Color(0x2200E676))
                        }
                        if (!content.description.isNullOrBlank()) {
                            Text(
                                text = content.description,
                                fontSize = tanweerTextSize(12f),
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "بواسطة: ${content.authorName} (${content.authorGradeSection})",
                                fontSize = tanweerTextSize(11f),
                                color = CyanAccent
                            )
                            Text(
                                text = "📖 اضغط لفتح الصور والنقل للدفتر",
                                fontSize = tanweerTextSize(11f),
                                color = CyanAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else if (dayExams.isEmpty() && dayAssignments.isEmpty() && dayEvents.isEmpty()) {
            item {
                if (hasConfirmedEmptyDay) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = GlassSurface.copy(alpha = 0.4f)
                    ) {
                        Text(
                            text = stringResource(R.string.calendar_empty_day_message),
                            fontSize = tanweerTextSize(13f),
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PendingSyncEmptyState(
                            dataLabel = stringResource(com.magd.tanweer.R.string.calendar_day_details_label, selectedDateStr),
                            syncState = aggregateSyncStates(listOf(scopedCalendarSyncState, scopedDayDetailsSyncState))
                        )
                        TextButton(
                            onClick = {
                                viewModel.syncCalendarMonth(activeGroupId, monthQueryFormat.format(currentCalendar.time), isRefresh = true)
                                viewModel.syncDayDetails(activeGroupId, selectedDateStr)
                            }
                        ) {
                            Text(stringResource(com.magd.tanweer.R.string.calendar_retry_day_sync), color = CyanAccent)
                        }
                    }
                }
            }
        }
    }

    if (showAddActionSheet) {
        CalendarAddActionSheet(
            dateLabel = TanweerViewModel.getFormattedArabicDate(selectedDateStr),
            canDeclareSchoolHoliday = currentUser?.role in listOf("TEACHER", "ADMIN"),
            onDismissRequest = { showAddActionSheet = false },
            onAddLesson = {
                showAddActionSheet = false
                viewModel.setSelectedDate(selectedDateStr)
                viewModel.openUploadDialog()
            },
            onAddHomework = {
                showAddActionSheet = false
                viewModel.setSelectedDate(selectedDateStr)
                viewModel.openAddHomeworkDialog(selectedDateStr)
            },
            onAddExam = {
                showAddActionSheet = false
                viewModel.setSelectedDate(selectedDateStr)
                viewModel.openAddExamDialog(selectedDateStr)
            },
            onAddEvent = {
                showAddActionSheet = false
                viewModel.setSelectedDate(selectedDateStr)
                viewModel.openAddEventDialog(selectedDateStr)
            },
            onDeclareSchoolHoliday = {
                showAddActionSheet = false
                holidayTitle = ""
                holidayReason = ""
                holidayError = null
                showAddHolidayDialog = true
            }
        )
    }

    if (showAddHolidayDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSubmittingHoliday) showAddHolidayDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🌴", fontSize = tanweerTextSize(22f))
                    Text("إعلان إجازة مدرسية رسمية", fontSize = tanweerTextSize(16f), fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "التاريخ: ${TanweerViewModel.getFormattedArabicDate(selectedDateStr)} ($selectedDateStr)",
                        fontSize = tanweerTextSize(12f),
                        color = CyanAccent
                    )

                    OutlinedTextField(
                        value = holidayTitle,
                        onValueChange = { holidayTitle = it },
                        label = { Text("عنوان الإجازة (مثلاً: إجازة رسمية / يوم المعلم)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = holidayReason,
                        onValueChange = { holidayReason = it },
                        label = { Text("السبب والمبرر الإداري الرسمي (إلزامي) ⚠️") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isSchoolWideHoliday = !isSchoolWideHoliday }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("نطاق الإجازة", fontSize = tanweerTextSize(13f), fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(
                                if (isSchoolWideHoliday) "كامل المدرسة (جميع الشعب والمراحل)" else "شعبتي الحالية فقط",
                                fontSize = tanweerTextSize(11f),
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = isSchoolWideHoliday,
                            onCheckedChange = { isSchoolWideHoliday = it }
                        )
                    }

                    if (holidayError != null) {
                        Text(
                            text = holidayError ?: "",
                            color = RubyRed,
                            fontSize = tanweerTextSize(12f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (holidayTitle.isBlank()) {
                            holidayError = "الرجاء إدخال عنوان الإجازة"
                            return@Button
                        }
                        if (holidayReason.isBlank()) {
                            holidayError = "سبب ومبرر الإجازة إلزامي لاعتمادها وتوثيقها"
                            return@Button
                        }
                        holidayError = null
                        isSubmittingHoliday = true
                        viewModel.createHoliday(
                            title = holidayTitle.trim(),
                            reason = holidayReason.trim(),
                            eventDate = selectedDateStr,
                            isSchoolWide = isSchoolWideHoliday
                        ) { result ->
                            isSubmittingHoliday = false
                            if (result.isSuccess) {
                                showAddHolidayDialog = false
                            } else {
                                holidayError = result.exceptionOrNull()?.localizedMessage ?: "فشلت عملية إنشاء الإجازة"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    enabled = !isSubmittingHoliday
                ) {
                    if (isSubmittingHoliday) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TextOnAccent, strokeWidth = 2.dp)
                    } else {
                        Text("اعتماد وتثبيت الإجازة 🌴", color = TextOnAccent)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddHolidayDialog = false },
                    enabled = !isSubmittingHoliday
                ) {
                    Text("إلغاء", color = TextSecondary)
                }
            },
            containerColor = MidnightSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 1. Content / Lesson Modal (TA-13)
    activeViewingContent?.let { content ->
        NotebookTranscriptionDialog(
            content = content,
            onDismiss = { activeViewingContent = null }
        )
    }

    // 2. Assignment Modal (HOMEWORK / TASK)
    activeViewingHomework?.let { hw ->
        val taskKind = HomeworkTaskTypePolicy.classify(hw.taskType)
        val typeLabel = when (taskKind) {
            HomeworkTaskKind.HOMEWORK -> R.string.homework_type_label_homework
            HomeworkTaskKind.TASK -> R.string.homework_type_label_project
            HomeworkTaskKind.UNKNOWN -> R.string.homework_type_label_other
        }
        val typeColor = when (taskKind) {
            HomeworkTaskKind.HOMEWORK -> WarmAmber
            HomeworkTaskKind.TASK -> PurpleAccent
            HomeworkTaskKind.UNKNOWN -> TextMuted
        }
        Dialog(onDismissRequest = { activeViewingHomework = null }) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                backgroundColor = MidnightSurface,
                borderColor = typeColor
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GlassPill(text = hw.subjectName, color = typeColor, bgColor = typeColor.copy(alpha = 0.15f))
                            GlassPill(text = stringResource(typeLabel), color = typeColor, bgColor = typeColor.copy(alpha = 0.15f))
                        }
                        IconButton(onClick = { activeViewingHomework = null }) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondary)
                        }
                    }

                    Text(text = hw.title, fontSize = tanweerTextSize(18f), fontWeight = FontWeight.Black, color = TextPrimary)
                    Text(text = "📅 تاريخ الطرح: ${hw.studyDate} • الاستحقاق: ${hw.dueDate}", fontSize = tanweerTextSize(11f), color = TextSecondary)

                    if (!hw.details.isNullOrBlank()) {
                        Text(text = hw.details, fontSize = tanweerTextSize(13f), color = TextPrimary, lineHeight = tanweerLineHeight(18f))
                    }

                    if (!hw.pageNumbers.isNullOrBlank() || !hw.questionNumbers.isNullOrBlank()) {
                        Text(text = "📖 الصفحات: ${hw.pageNumbers ?: "—"} • الأسئلة: ${hw.questionNumbers ?: "—"}", fontSize = tanweerTextSize(12f), color = CyanAccent)
                    }

                    if (hw.mediaUrls.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "المرفقات (${hw.mediaUrls.size}):", fontSize = tanweerTextSize(12f), fontWeight = FontWeight.Bold, color = TextPrimary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            hw.mediaUrls.take(3).forEach { url ->
                                AsyncImage(
                                    model = url,
                                    contentDescription = stringResource(R.string.calendar_assignment_attachment),
                                    modifier = Modifier
                                        .size(70.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (hw.isCompleted) {
                                stringResource(R.string.calendar_assignment_completed, stringResource(typeLabel))
                            } else {
                                stringResource(R.string.calendar_assignment_not_completed, stringResource(typeLabel))
                            },
                            fontSize = tanweerTextSize(12f),
                            color = if (hw.isCompleted) EmeraldGreen else typeColor,
                            fontWeight = FontWeight.Bold
                        )
                        GlassButton(
                            text = if (hw.isCompleted) "إلغاء الإنجاز" else "تحديد كمنجز ✓",
                            color = if (hw.isCompleted) TextMuted else EmeraldGreen,
                            onClick = {
                                viewModel.toggleHomework(hw.id, hw.isCompleted)
                                activeViewingHomework = hw.copy(isCompleted = !hw.isCompleted)
                            }
                        )
                    }
                }
            }
        }
    }

    // 3. Exam Modal (TA-13)
    activeViewingExam?.let { exam ->
        Dialog(onDismissRequest = { activeViewingExam = null }) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                backgroundColor = MidnightSurface,
                borderColor = RubyRed
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GlassPill(text = exam.subjectName, color = RubyRed, bgColor = RubyRed.copy(alpha = 0.15f))
                            GlassPill(text = localizedExamTypeLabel(exam.examType), color = CyanAccent, bgColor = CyanAccent.copy(alpha = 0.15f))
                        }
                        IconButton(onClick = { activeViewingExam = null }) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondary)
                        }
                    }

                    Text(text = exam.title, fontSize = tanweerTextSize(18f), fontWeight = FontWeight.Black, color = TextPrimary)
                    Text(text = "📅 موعد الاختبار: ${exam.examDate}", fontSize = tanweerTextSize(12f), color = WarmAmber, fontWeight = FontWeight.Bold)

                    if (!exam.requiredChapters.isNullOrBlank()) {
                        Text(text = "الفصول والمقرر: ${localizedChapterListLabel(exam.requiredChapters)}", fontSize = tanweerTextSize(13f), color = CyanAccent)
                    }
                    if (!exam.notes.isNullOrBlank()) {
                        Text(text = "ملاحظات: ${exam.notes}", fontSize = tanweerTextSize(12f), color = TextSecondary)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (exam.isCompleted) "تمت المذاكرة بالكامل ✓" else "مطلوب المذاكرة",
                            fontSize = tanweerTextSize(12f),
                            color = if (exam.isCompleted) EmeraldGreen else RubyRed,
                            fontWeight = FontWeight.Bold
                        )
                        GlassButton(
                            text = if (exam.isCompleted) "إلغاء الإنجاز" else "تمت المذاكرة ✓",
                            color = if (exam.isCompleted) TextMuted else EmeraldGreen,
                            onClick = {
                                viewModel.toggleExamCompletion(exam.id, !exam.isCompleted)
                                activeViewingExam = exam.copy(isCompleted = !exam.isCompleted)
                            }
                        )
                    }
                }
            }
        }
    }

    // 4. Event Modal (TA-13)
    activeViewingEvent?.let { event ->
        Dialog(onDismissRequest = { activeViewingEvent = null }) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                backgroundColor = MidnightSurface,
                borderColor = PurpleAccent
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GlassPill(text = localizedEventCategoryLabel(event.category), color = PurpleAccent, bgColor = PurpleAccent.copy(alpha = 0.15f))
                            if (event.groupId == "all") {
                                GlassPill(text = "كامل المدرسة 🏫", color = EmeraldGreen, bgColor = EmeraldGreen.copy(alpha = 0.15f))
                            }
                        }
                        IconButton(onClick = { activeViewingEvent = null }) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondary)
                        }
                    }

                    Text(text = event.title, fontSize = tanweerTextSize(18f), fontWeight = FontWeight.Black, color = TextPrimary)
                    Text(text = "📅 التاريخ: ${event.eventDate} ${event.timeStr?.let { "• $it" } ?: ""}", fontSize = tanweerTextSize(11f), color = TextSecondary)

                    if (!event.location.isNullOrBlank()) {
                        Text(text = "📍 المكان: ${event.location}", fontSize = tanweerTextSize(13f), color = CyanAccent, fontWeight = FontWeight.Bold)
                    }
                    if (!event.description.isNullOrBlank()) {
                        Text(text = event.description, fontSize = tanweerTextSize(13f), color = TextPrimary, lineHeight = tanweerLineHeight(18f))
                    }
                }
            }
        }
    }
}

data class CalendarDayItem(
    val dayNumber: Int,
    val dateStr: String,
    val isCurrentMonth: Boolean,
    val isToday: Boolean = false,
    val hasLesson: Boolean = false,
    val hasExam: Boolean = false,
    val hasHomework: Boolean = false,
    val hasTask: Boolean = false,
    val hasOtherAssignment: Boolean = false,
    val hasEvent: Boolean = false,
    val hasHoliday: Boolean = false
)
