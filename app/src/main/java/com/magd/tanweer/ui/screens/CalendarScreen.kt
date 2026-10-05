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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.GlassButton
import com.magd.tanweer.ui.components.GlassCard
import com.magd.tanweer.ui.components.GlassPill
import com.magd.tanweer.ui.theme.*
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

    var currentCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDateStr by remember { mutableStateOf(TanweerViewModel.getTodayDateString()) }
    var showAddActionSheet by remember { mutableStateOf(false) }

    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale("ar")) }
    val dayFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    // Day content queries
    val dayContents by viewModel.repository.getDayContents(activeGroupId, selectedDateStr)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val homeworks by viewModel.repository.getHomeworks(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val dayHomeworks = remember(homeworks, selectedDateStr) {
        homeworks.filter { it.studyDate == selectedDateStr || it.dueDate == selectedDateStr }
    }

    val exams by viewModel.repository.getExams(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val dayExams = remember(exams, selectedDateStr) {
        exams.filter { it.examDate == selectedDateStr }
    }

    val events by viewModel.repository.getEvents(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val dayEvents = remember(events, selectedDateStr) {
        events.filter { it.eventDate == selectedDateStr }
    }

    // Days in current month
    val daysInMonth = remember(currentCalendar.timeInMillis, exams, homeworks, events) {
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
                    hasExam = exams.any { it.examDate == dStr },
                    hasHomework = homeworks.any { it.dueDate == dStr },
                    hasEvent = events.any { it.eventDate == dStr }
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
                    IconButton(onClick = {
                        val next = currentCalendar.clone() as Calendar
                        next.add(Calendar.MONTH, -1)
                        currentCalendar = next
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "الشهر السابق",
                            tint = CyanAccent
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = monthYearFormat.format(currentCalendar.time),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "التقويم الدراسي الزجاجي",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    IconButton(onClick = {
                        val next = currentCalendar.clone() as Calendar
                        next.add(Calendar.MONTH, 1)
                        currentCalendar = next
                    }) {
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(RubyRed.copy(alpha = 0.12f))
                        .border(1.dp, RubyRed.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🔴 ${exams.size} اختبارات", fontSize = 11.sp, color = RubyRed, fontWeight = FontWeight.Bold)
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
                    Text("📝 ${homeworks.size} واجبات", fontSize = 11.sp, color = WarmAmber, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PurpleAccent.copy(alpha = 0.15f))
                        .border(1.dp, PurpleAccent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .clickable { viewModel.setSubScreen(SubScreen.EVENTS) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🎪 ${events.size} فعاليات", fontSize = 11.sp, color = PurpleAccent, fontWeight = FontWeight.Bold)
                        Text("‹", fontSize = 13.sp, color = PurpleAccent)
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
                        fontSize = 12.sp,
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
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        when {
                                            isSelected -> CyanAccent
                                            item.isToday -> CyanAccent
                                            else -> Color.Transparent
                                        },
                                        CircleShape
                                    )
                                    .clickable {
                                        selectedDateStr = item.dateStr
                                        viewModel.setSelectedDate(item.dateStr)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = item.dayNumber.toString(),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected || item.isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) TextOnAccent else TextPrimary
                                    )
                                    // Activity indicators
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
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
                                        if (item.hasEvent) {
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
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                    Text(
                        text = "يمكنك توثيق الدروس والواجبات لهذا التاريخ في أي وقت",
                        fontSize = 11.sp,
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

        // Selected Day Exams
        if (dayExams.isNotEmpty()) {
            items(dayExams) { exam ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = RubyRed.copy(alpha = 0.5f),
                    backgroundColor = GlassSurface
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔴", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "اختبار ${exam.subjectName}: ${exam.title}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            if (!exam.requiredChapters.isNullOrBlank()) {
                                Text(
                                    text = "الفصول المقررة: ${exam.requiredChapters}",
                                    fontSize = 12.sp,
                                    color = RubyRed,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        GlassPill(text = "اختبار", color = RubyRed, bgColor = Color(0x33FF3366))
                    }
                }
            }
        }

        // Selected Day Homeworks
        if (dayHomeworks.isNotEmpty()) {
            items(dayHomeworks) { hw ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = WarmAmber.copy(alpha = 0.4f),
                    backgroundColor = GlassSurface
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "📝 واجب ${hw.subjectName}: ${hw.title}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "موعد التسليم: ${hw.dueDate}",
                                fontSize = 12.sp,
                                color = WarmAmber
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
                    modifier = Modifier.fillMaxWidth(),
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
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "فعالية: ${ev.title}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            if (!ev.description.isNullOrBlank()) {
                                Text(
                                    text = ev.description,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            if (!ev.location.isNullOrBlank() || !ev.timeStr.isNullOrBlank()) {
                                Text(
                                    text = "${ev.timeStr?.let { "⏰ $it " } ?: ""}${ev.location?.let { "📍 $it" } ?: ""}",
                                    fontSize = 11.sp,
                                    color = PurpleAccent,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
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
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurface
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${content.subjectIcon} درس ${content.subjectName}: ${content.title}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            GlassPill(text = "درس موثق", color = EmeraldGreen, bgColor = Color(0x2200E676))
                        }
                        if (!content.description.isNullOrBlank()) {
                            Text(
                                text = content.description,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Text(
                            text = "بواسطة: ${content.authorName} (${content.authorGradeSection})",
                            fontSize = 11.sp,
                            color = CyanAccent,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        } else if (dayExams.isEmpty() && dayHomeworks.isEmpty() && dayEvents.isEmpty()) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurface.copy(alpha = 0.4f)
                ) {
                    Text(
                        text = "لا توجد منشورات أو واجبات أو فعاليات مسجلة لهذا اليوم حتى الآن. يمكنك إضافة وتوثيق الحصة بالضغط على زر الإضافة أعلاه.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                }
            }
        }
    }

    if (showAddActionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddActionSheet = false },
            containerColor = MidnightSurface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = CyanAccent) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "➕ إضافة وتوثيق ليوم: ${TanweerViewModel.getFormattedArabicDate(selectedDateStr)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanAccent
                )
                Text(
                    text = "اختر نوع النشاط الذي ترغب في إضافته أو توثيقه لهذا التاريخ:",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(4.dp))

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurface,
                    onClick = {
                        showAddActionSheet = false
                        viewModel.setSelectedDate(selectedDateStr)
                        viewModel.openUploadDialog()
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("📷", fontSize = 24.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("توثيق درس للشعبة", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("التقاط صور السبورة ومشاركتها مع زملاء الصف", fontSize = 11.sp, color = TextSecondary)
                        }
                        GlassPill(text = "درس", color = CyanAccent, bgColor = CyanGlow)
                    }
                }

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurface,
                    onClick = {
                        showAddActionSheet = false
                        viewModel.setSelectedDate(selectedDateStr)
                        viewModel.setTab(com.magd.tanweer.ui.NavigationTab.HOMEWORK)
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("📝", fontSize = 24.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("إضافة واجب أو تكليف مدرسي", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("تسجيل الواجب المطلوب وتاريخ التسليم المعتمد", fontSize = 11.sp, color = TextSecondary)
                        }
                        GlassPill(text = "واجب", color = WarmAmber, bgColor = WarmAmber.copy(alpha = 0.15f))
                    }
                }

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurface,
                    onClick = {
                        showAddActionSheet = false
                        viewModel.setSelectedDate(selectedDateStr)
                        viewModel.setTab(com.magd.tanweer.ui.NavigationTab.EXAMS)
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("📑", fontSize = 24.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("إضافة اختبار أو تسميع دوري", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("تحديد موعد الاختبار والفصول المقررة", fontSize = 11.sp, color = TextSecondary)
                        }
                        GlassPill(text = "اختبار", color = RubyRed, bgColor = RubyRed.copy(alpha = 0.15f))
                    }
                }

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurface,
                    onClick = {
                        showAddActionSheet = false
                        viewModel.setSubScreen(SubScreen.EVENTS)
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🎪", fontSize = 24.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("إضافة فعالية أو مناسبة مدرسية", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("تسجيل مسابقة، عطلة، أو نشاط صفي تفاعلي", fontSize = 11.sp, color = TextSecondary)
                        }
                        GlassPill(text = "فعالية", color = PurpleAccent, bgColor = PurpleAccent.copy(alpha = 0.15f))
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
    val hasExam: Boolean = false,
    val hasHomework: Boolean = false,
    val hasEvent: Boolean = false
)
