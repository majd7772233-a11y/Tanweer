package com.magd.tanweer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.ContentItem
import com.magd.tanweer.data.model.ScheduleSlot
import com.magd.tanweer.data.model.SchoolHierarchy
import com.magd.tanweer.ui.NavigationTab
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    viewModel: TanweerViewModel
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    var activeViewingContent by remember { mutableStateOf<ContentItem?>(null) }
    val todayDate = remember { TanweerViewModel.getTodayDateString() }
    val isWeekend = remember { TanweerViewModel.isWeekend() }
    val dayOfWeek = remember { TanweerViewModel.getDayOfWeekIndex() }
    val effectiveDayForSlots = if (isWeekend) -1 else dayOfWeek

    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId ?: "" }
    val shouldShowCatchUpCard by viewModel.shouldShowCatchUpCard.collectAsStateWithLifecycle()
    val lastOpenDate by viewModel.lastOpenDate.collectAsStateWithLifecycle()

    val slots: List<ScheduleSlot> by remember(activeGroupId, effectiveDayForSlots, isWeekend) {
        if (isWeekend) {
            kotlinx.coroutines.flow.flowOf(emptyList<ScheduleSlot>())
        } else {
            viewModel.repository.getScheduleSlots(activeGroupId, effectiveDayForSlots)
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val todayContents by viewModel.repository.getDayContents(activeGroupId, todayDate)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val homeworks by viewModel.repository.getHomeworks(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val exams by viewModel.repository.getExams(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val events by viewModel.repository.getEvents(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val dailyPlanItems by viewModel.getDailyPlanForDate(todayDate)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Group academic subjects scheduled for today by subjectId (distinct subjects)
    val scheduledAcademicSubjects = remember(slots) {
        slots.filter { isAcademicSubjectRequiringDocumentation(it) }
            .groupBy { it.subjectId }
    }

    val documentedSubjectIds = remember(todayContents) {
        todayContents.map { it.subjectId }.toSet()
    }

    val documentedSubjectsMap = remember(scheduledAcademicSubjects, documentedSubjectIds) {
        scheduledAcademicSubjects.filterKeys { documentedSubjectIds.contains(it) }
    }

    val pendingSubjectsMap = remember(scheduledAcademicSubjects, documentedSubjectIds) {
        scheduledAcademicSubjects.filterKeys { !documentedSubjectIds.contains(it) }
    }

    val totalSubjectsCount = scheduledAcademicSubjects.size.coerceAtLeast(1)
    val docCount = documentedSubjectsMap.size
    val progressPercent = if (scheduledAcademicSubjects.isEmpty()) 0f else (docCount.toFloat() / totalSubjectsCount.toFloat())

    val upcomingHwCount = homeworks.count { !it.isCompleted }
    val upcomingExamsCount = remember(exams, todayDate) {
        exams.count { it.examDate >= todayDate }
    }

    val nearestExam = remember(exams, todayDate) {
        exams.filter { it.examDate >= todayDate }.minByOrNull { it.examDate }
    }

    val nearestExamDaysLeft = remember(nearestExam, todayDate) {
        if (nearestExam == null) null
        else {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val d1 = sdf.parse(todayDate)
                val d2 = sdf.parse(nearestExam.examDate)
                if (d1 != null && d2 != null) {
                    val diff = (d2.time - d1.time) / (1000 * 60 * 60 * 24)
                    diff.toInt()
                } else null
            } catch (_: Exception) { null }
        }
    }

    val gradeName = currentUser?.gradeId?.let { SchoolHierarchy.getGradeName(it) } ?: "المرحلة الدراسية"
    val sectionAr = currentUser?.sectionId?.let { SchoolHierarchy.getSectionArabicName(it) } ?: ""
    val studentFirstName = currentUser?.fullName?.split(" ")?.firstOrNull() ?: "طالبنا العزيز"

    // Time of day dynamic greeting ticker
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            currentTimeMs = System.currentTimeMillis()
        }
    }

    val currentCalendar = remember(currentTimeMs) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = currentTimeMs
        cal
    }
    val currentHour = remember(currentCalendar) { currentCalendar.get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(currentHour) {
        when (currentHour) {
            in 5..11 -> "صباح الخير"
            in 12..16 -> "طاب يومك"
            else -> "مساء الخير"
        }
    }

    // Determine current & next period dynamically from single source of truth (AcademicTimeEngine)
    val academicStatus = remember(slots, currentCalendar, isWeekend) {
        com.magd.tanweer.util.AcademicTimeEngine.computeDayStatus(slots, currentCalendar, isWeekend)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Command Center Header
        item {
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
                        Column {
                            Text(
                                text = "$greeting $studentFirstName 👋",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = CyanAccent
                            )
                            Text(
                                text = "📍 $gradeName — شعبة ($sectionAr)",
                                fontSize = 13.sp,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        GlassPill(
                            text = TanweerViewModel.getFormattedArabicDate(todayDate),
                            color = WarmAmber,
                            bgColor = WarmAmber.copy(alpha = 0.12f)
                        )
                    }

                    // Active & Next Class Live Status
                    when (val status = academicStatus) {
                        is com.magd.tanweer.util.AcademicDayStatus.InSession -> {
                            Spacer(modifier = Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MidnightSurface)
                                    .border(1.dp, CyanGlow, RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Current Period
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("الآن:", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = "${status.activeSlot.subjectIcon} ${status.activeSlot.subjectName}",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Black,
                                                color = CyanAccent
                                            )
                                        }
                                        Text(
                                            text = "الحصة ${status.activeSlot.slotOrder} • ${status.startTime} — ${status.endTime}",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }

                                    // Next Period
                                    if (status.nextSlot != null) {
                                        Box(
                                            modifier = Modifier
                                                .width(1.dp)
                                                .height(32.dp)
                                                .background(GlassBorderSubtle)
                                        )
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(start = 12.dp)
                                        ) {
                                            val (nextStart, _) = com.magd.tanweer.util.AcademicTimeEngine.getSlotEffectiveTimes(status.nextSlot)
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text("التالي:", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                                Text(
                                                    text = "${status.nextSlot.subjectIcon} ${status.nextSlot.subjectName}",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                            }
                                            Text(
                                                text = "الحصة ${status.nextSlot.slotOrder} • $nextStart",
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        is com.magd.tanweer.util.AcademicDayStatus.BreakTime -> {
                            Spacer(modifier = Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MidnightSurface)
                                    .border(1.dp, WarmAmber.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("☕ استراحة بين الحصص", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WarmAmber)
                                        Text("استعد للحصة القادمة", fontSize = 11.sp, color = TextSecondary)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "الحصة ${status.nextSlot.slotOrder}: ${status.nextSlot.subjectName}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text("تبدأ الساعة ${status.nextStartTime}", fontSize = 11.sp, color = CyanAccent)
                                    }
                                }
                            }
                        }
                        is com.magd.tanweer.util.AcademicDayStatus.BeforeSchool -> {
                            Spacer(modifier = Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MidnightSurface)
                                    .border(1.dp, GlassBorderSubtle, RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("🌅 يبدأ الدوام قريبًا", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text("الحصة الأولى: ${status.firstSlot.subjectName}", fontSize = 11.sp, color = TextSecondary)
                                    }
                                    GlassPill(
                                        text = "تبدأ ${status.startTime}",
                                        color = CyanAccent,
                                        bgColor = CyanGlow
                                    )
                                }
                            }
                        }
                        is com.magd.tanweer.util.AcademicDayStatus.AfterSchool -> {
                            Spacer(modifier = Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MidnightSurface)
                                    .border(1.dp, GlassBorderSubtle, RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("🏁", fontSize = 20.sp)
                                    Column {
                                        Text("انتهى اليوم الدراسي لهذا اليوم ✓", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text("أحسنت! راجع واجباتك ومهامك المدرسية القادمة", fontSize = 11.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }
                        else -> { /* Weekend or NoSchedule: Clean, no misleading first period */ }
                    }
                }
            }
        }

        // Student Obligations 3-Card Command Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Homeworks
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(GlassSurface)
                        .border(1.dp, GlassBorderSubtle, RoundedCornerShape(16.dp))
                        .clickable { viewModel.setTab(NavigationTab.HOMEWORK) }
                        .padding(10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("📝", fontSize = 20.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (upcomingHwCount > 0) "$upcomingHwCount متبقية" else "منجزة ✓",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (upcomingHwCount > 0) WarmAmber else EmeraldGreen
                        )
                        Text(text = "الواجبات", fontSize = 10.sp, color = TextMuted)
                    }
                }

                // 2. Exams
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(GlassSurface)
                        .border(1.dp, GlassBorderSubtle, RoundedCornerShape(16.dp))
                        .clickable { viewModel.setTab(NavigationTab.EXAMS) }
                        .padding(10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("🎯", fontSize = 20.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (nearestExamDaysLeft) {
                                null -> "لا اختبارات"
                                0 -> "اختبار اليوم!"
                                1 -> "اختبار غداً"
                                else -> "بعد $nearestExamDaysLeft أيام"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RubyRed,
                            maxLines = 1
                        )
                        Text(text = "الاختبارات", fontSize = 10.sp, color = TextMuted)
                    }
                }

                // 3. Documentation
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(GlassSurface)
                        .border(1.dp, GlassBorderSubtle, RoundedCornerShape(16.dp))
                        .clickable { viewModel.openUploadDialog() }
                        .padding(10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("📷", fontSize = 20.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (pendingSubjectsMap.isNotEmpty()) "${pendingSubjectsMap.size} للتوثيق" else "مكتملة ✨",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent
                        )
                        Text(text = "التوثيق", fontSize = 10.sp, color = TextMuted)
                    }
                }
            }
        }

        // Daily Plan (خطة إنجاز اليوم)
        if (dailyPlanItems.isNotEmpty()) {
            item {
                val completedPlanCount = dailyPlanItems.count { it.isCompleted }
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = WarmAmber.copy(alpha = 0.5f),
                    backgroundColor = MidnightSurface
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("📌", fontSize = 18.sp)
                                Text(
                                    text = "خطة إنجاز اليوم (${completedPlanCount}/${dailyPlanItems.size})",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WarmAmber
                                )
                            }
                            Text(
                                text = "${((completedPlanCount.toFloat() / dailyPlanItems.size.toFloat()) * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (completedPlanCount == dailyPlanItems.size) EmeraldGreen else WarmAmber
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        dailyPlanItems.forEach { planItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = planItem.isCompleted,
                                        onCheckedChange = { viewModel.toggleDailyPlanItem(planItem.id, planItem.isCompleted) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = EmeraldGreen,
                                            uncheckedColor = TextSecondary
                                        )
                                    )
                                    Column {
                                        Text(
                                            text = planItem.title,
                                            fontSize = 13.sp,
                                            fontWeight = if (planItem.isCompleted) FontWeight.Normal else FontWeight.Medium,
                                            color = if (planItem.isCompleted) TextMuted else TextPrimary,
                                            style = if (planItem.isCompleted) androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle()
                                        )
                                        if (!planItem.subjectName.isNullOrBlank()) {
                                            Text(
                                                text = planItem.subjectName,
                                                fontSize = 11.sp,
                                                color = CyanAccent
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.removeDailyPlanItem(planItem.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "حذف من الخطة",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Today Study Day Progress & Schedule Summary
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = GlassSurface
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (isWeekend) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(text = "🌴", fontSize = 24.sp)
                            Column {
                                Text(
                                    text = "عطلة نهاية الأسبوع — لا توجد حصص اليوم",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanAccent
                                )
                                Text(
                                    text = "استعد لليوم الدراسي القادم: ${TanweerViewModel.getNextSchoolDayName()}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "📚", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "إنجاز توثيق مواد اليوم الأكاديمية",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "التوثيق معتمد حسب المادة ($docCount من $totalSubjectsCount مواد) لضمان عدم تكرار الحصص",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Text(
                                text = "${(progressPercent * 100).toInt()}% مكتمل",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (progressPercent >= 0.8f) EmeraldGreen else WarmAmber
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        GlassProgressBar(
                            progress = progressPercent,
                            primaryColor = if (progressPercent >= 0.8f) EmeraldGreen else CyanAccent
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${slots.size} حصص مجدولة • $totalSubjectsCount مواد أصلية",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "✅ $docCount مواد موثقة",
                                    fontSize = 11.sp,
                                    color = EmeraldGreen,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "🟡 ${pendingSubjectsMap.size} متبقية",
                                    fontSize = 11.sp,
                                    color = WarmAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Catch-up Banner (Displayed ONLY when student was absent on previous days)
        if (shouldShowCatchUpCard) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = CyanGlow.copy(alpha = 0.08f),
                    borderColor = CyanAccent.copy(alpha = 0.3f),
                    onClick = { viewModel.setSubScreen(SubScreen.WHAT_DID_I_MISS) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "🎒", fontSize = 22.sp)
                            Column {
                                Text(
                                    text = "ماذا فاتك أثناء الغياب؟",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanAccent
                                )
                                Text(
                                    text = "تصفح ما وثقه زملاؤك منذ $lastOpenDate وحتى اليوم",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.dismissCatchUpCard() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "إخفاء",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "فتح",
                                tint = CyanAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Section: Documented Subjects (المواد الموثقة)
        if (documentedSubjectsMap.isNotEmpty()) {
            item {
                Text(
                    text = "✅ المواد الموثقة اليوم (${documentedSubjectsMap.size} من $totalSubjectsCount مواد أصلية)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldGreen,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(documentedSubjectsMap.entries.toList(), key = { it.key }) { (subjectId, slotList) ->
                val consolidatedSlot = if (slotList.size > 1) {
                    val orders = slotList.map { it.slotOrder }.sorted().joinToString(" و ")
                    slotList.first().copy(subjectName = "${slotList.first().subjectName} (الحصص $orders • مادة واحدة)")
                } else {
                    slotList.first()
                }
                val lessons = todayContents.filter { it.subjectId == subjectId }
                DocumentedSubjectCard(
                    slot = consolidatedSlot,
                    lessons = lessons,
                    onAddMore = { viewModel.openUploadDialog(subjectId) },
                    onViewLesson = { activeViewingContent = it },
                    onRetryLesson = { lessonId -> viewModel.retrySyncContent(lessonId) }
                )
            }
        }

        // Section: Needs Contribution (تحتاج مساهمة)
        if (pendingSubjectsMap.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🟡 مواد تحتاج توثيق اليوم",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WarmAmber
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${pendingSubjectsMap.size} مواد)",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }

            items(pendingSubjectsMap.entries.toList(), key = { it.key }) { (subjectId, slotList) ->
                val consolidatedPendingSlot = if (slotList.size > 1) {
                    val orders = slotList.map { it.slotOrder }.sorted().joinToString(" و ")
                    slotList.first().copy(subjectName = "${slotList.first().subjectName} (الحصص $orders)")
                } else {
                    slotList.first()
                }
                NeedsContributionSubjectCard(
                    slot = consolidatedPendingSlot,
                    onContribute = { viewModel.openUploadDialog(subjectId) }
                )
            }
        }
    }

    activeViewingContent?.let { content ->
        NotebookTranscriptionDialog(
            content = content,
            onDismiss = { activeViewingContent = null }
        )
    }
}

@Composable
fun DocumentedSubjectCard(
    slot: ScheduleSlot,
    lessons: List<ContentItem>,
    onAddMore: () -> Unit,
    onViewLesson: (ContentItem) -> Unit,
    onRetryLesson: ((String) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        borderColor = EmeraldGreen.copy(alpha = 0.4f),
        backgroundColor = GlassSurface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreen.copy(alpha = 0.15f))
                            .border(1.dp, EmeraldGreen.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = slot.subjectIcon, fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = slot.subjectName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "الحصة ${slot.slotOrder} • ${lessons.size} دروس موثقة (اضغط ${if (expanded) "للإغلاق" else "لفتح الصور"})",
                            fontSize = 11.sp,
                            color = if (expanded) CyanAccent else TextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onAddMore) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "إضافة صفحة",
                            tint = CyanAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Text(
                            text = if (expanded) "▲" else "▼",
                            color = CyanAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = GlassBorderSubtle.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(10.dp))

                if (lessons.isEmpty()) {
                    Text(
                        text = "لا توجد صفحات مسجلة لهذه المادة بعد",
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        lessons.forEach { lesson ->
                            val photoCount = lesson.media.size
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onViewLesson(lesson) },
                                backgroundColor = MidnightSurface,
                                borderColor = CyanAccent.copy(alpha = 0.25f)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = lesson.title.ifBlank { "درس مادة ${slot.subjectName}" },
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary
                                            )
                                            val authorText = if (lesson.authorName.isNotBlank()) "بواسطة: ${lesson.authorName}" else ""
                                            val dateText = lesson.studyDate
                                            val metaText = listOf(authorText, dateText).filter { it.isNotBlank() }.joinToString(" • ")
                                            if (metaText.isNotBlank()) {
                                                Text(
                                                    text = metaText,
                                                    fontSize = 11.sp,
                                                    color = TextMuted
                                                )
                                            }
                                        }

                                        GlassPill(
                                            text = if (photoCount > 0) "📷 $photoCount صور" else "📝 ملخص",
                                            color = if (photoCount > 0) CyanAccent else EmeraldGreen
                                        )
                                    }

                                    if (!lesson.description.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = lesson.description,
                                            fontSize = 12.sp,
                                            color = TextSecondary,
                                            maxLines = 2
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        GlassButton(
                                            text = "📖 فتح صور السبورة ونقل الدرس للدفتر",
                                            onClick = { onViewLesson(lesson) },
                                            color = CyanAccent,
                                            textColor = TextOnAccent,
                                            modifier = Modifier.height(34.dp)
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
}

@Composable
fun NeedsContributionSubjectCard(
    slot: ScheduleSlot,
    onContribute: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = WarmAmber.copy(alpha = 0.35f),
        backgroundColor = GlassSurface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(WarmAmber.copy(alpha = 0.15f))
                        .border(1.dp, WarmAmber.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = slot.subjectIcon, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = slot.subjectName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "الحصة ${slot.slotOrder} • لم توثق بعد",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            GlassButton(
                text = "توثيق 📸",
                onClick = onContribute,
                modifier = Modifier.height(34.dp),
                color = CyanAccent
            )
        }
    }
}

private fun isAcademicSubjectRequiringDocumentation(slot: ScheduleSlot): Boolean {
    val nonAcademic = setOf("sports", "art", "activity", "break", "library")
    return !nonAcademic.contains(slot.subjectId.lowercase())
}
