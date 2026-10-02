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
    val todayDate = remember { TanweerViewModel.getTodayDateString() }
    val isWeekend = remember { TanweerViewModel.isWeekend() }
    val dayOfWeek = remember { TanweerViewModel.getDayOfWeekIndex() }
    val effectiveDayForSlots = if (isWeekend) -1 else dayOfWeek

    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId ?: "" }

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

    // Group subjects into Documented vs Needs Contribution
    val documentedSubjectIds = remember(todayContents) {
        todayContents.map { it.subjectId }.toSet()
    }

    val documentedSlots = remember(slots, documentedSubjectIds) {
        slots.filter { documentedSubjectIds.contains(it.subjectId) }
    }

    val pendingSlots = remember(slots, documentedSubjectIds) {
        slots.filter { slot ->
            !documentedSubjectIds.contains(slot.subjectId) && isAcademicSubjectRequiringDocumentation(slot)
        }
    }

    val totalCount = slots.size.coerceAtLeast(1)
    val docCount = documentedSlots.size
    val progressPercent = if (slots.isEmpty()) 0f else (docCount.toFloat() / slots.size.toFloat())

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

    // Time of day greeting
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (currentHour) {
        in 5..11 -> "صباح الخير"
        in 12..16 -> "طاب يومك"
        else -> "مساء الخير"
    }

    // Determine current & next period dynamically
    val sortedSlots = remember(slots) { slots.sortedBy { it.slotOrder } }
    val periodTimes = listOf(
        1 to ("08:00" to "08:45"),
        2 to ("08:50" to "09:35"),
        3 to ("09:40" to "10:25"),
        4 to ("10:45" to "11:30"),
        5 to ("11:35" to "12:20"),
        6 to ("12:25" to "01:10")
    )

    val currentSlotInfo = remember(sortedSlots, currentHour) {
        if (isWeekend || sortedSlots.isEmpty()) null
        else {
            // Check based on hour / order
            val cal = Calendar.getInstance()
            val totalMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val currentOrder = when (totalMinutes) {
                in (8 * 60)..(8 * 60 + 45) -> 1
                in (8 * 60 + 50)..(9 * 60 + 35) -> 2
                in (9 * 60 + 40)..(10 * 60 + 25) -> 3
                in (10 * 60 + 45)..(11 * 60 + 30) -> 4
                in (11 * 60 + 35)..(12 * 60 + 20) -> 5
                in (12 * 60 + 25)..(13 * 60 + 10) -> 6
                else -> if (totalMinutes < 8 * 60) 1 else null
            }
            val active = sortedSlots.find { it.slotOrder == currentOrder } ?: sortedSlots.firstOrNull()
            val next = sortedSlots.find { it.slotOrder == (active?.slotOrder ?: 0) + 1 }
            Pair(active, next)
        }
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
                    if (!isWeekend && currentSlotInfo?.first != null) {
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
                                            text = "${currentSlotInfo.first?.subjectIcon} ${currentSlotInfo.first?.subjectName}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black,
                                            color = CyanAccent
                                        )
                                    }
                                    val time = periodTimes.find { it.first == currentSlotInfo.first?.slotOrder }?.second
                                    if (time != null) {
                                        Text(
                                            text = "الحصة ${currentSlotInfo.first?.slotOrder} • ${time.first} — ${time.second}",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                // Next Period
                                if (currentSlotInfo.second != null) {
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
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("التالي:", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = "${currentSlotInfo.second?.subjectIcon} ${currentSlotInfo.second?.subjectName}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                        }
                                        val nextTime = periodTimes.find { it.first == currentSlotInfo.second?.slotOrder }?.second
                                        if (nextTime != null) {
                                            Text(
                                                text = "الحصة ${currentSlotInfo.second?.slotOrder} • ${nextTime.first}",
                                                fontSize = 11.sp,
                                                color = TextSecondary
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
                            text = if (pendingSlots.isNotEmpty()) "${pendingSlots.size} للتوثيق" else "مكتملة ✨",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent
                        )
                        Text(text = "التوثيق", fontSize = 10.sp, color = TextMuted)
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
                                Text(
                                    text = "إنجاز توثيق اليوم",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
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
                                text = "${slots.size} حصص مجدولة",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "✅ $docCount موثقة",
                                    fontSize = 12.sp,
                                    color = EmeraldGreen,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "🟡 ${pendingSlots.size} متبقية",
                                    fontSize = 12.sp,
                                    color = WarmAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Catch-up Banner
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
                                text = "ماذا فاتك مؤخراً؟",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent
                            )
                            Text(
                                text = "تصفح الواجبات والاختبارات والدروس السابقة أثناء الغياب",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
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

        // Section: Documented Subjects (المواد الموثقة)
        if (documentedSlots.isNotEmpty()) {
            item {
                Text(
                    text = "✅ المواد الموثقة اليوم",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldGreen,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(documentedSlots, key = { it.id }) { slot ->
                val lessons = todayContents.filter { it.subjectId == slot.subjectId }
                DocumentedSubjectCard(
                    slot = slot,
                    lessons = lessons,
                    onAddMore = { viewModel.openUploadDialog(slot.subjectId) },
                    onRetryLesson = { lessonId -> viewModel.retrySyncContent(lessonId) }
                )
            }
        }

        // Section: Needs Contribution (تحتاج مساهمة)
        if (pendingSlots.isNotEmpty()) {
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
                        text = "(${pendingSlots.size} مواد)",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }

            items(pendingSlots, key = { it.id }) { slot ->
                NeedsContributionSubjectCard(
                    slot = slot,
                    onContribute = { viewModel.openUploadDialog(slot.subjectId) }
                )
            }
        }
    }
}

@Composable
fun DocumentedSubjectCard(
    slot: ScheduleSlot,
    lessons: List<ContentItem>,
    onAddMore: () -> Unit,
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            text = "الحصة ${slot.slotOrder} • ${lessons.size} دروس موثقة",
                            fontSize = 11.sp,
                            color = TextSecondary
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
