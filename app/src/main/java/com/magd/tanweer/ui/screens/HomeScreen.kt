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
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*
import java.util.Calendar

@Composable
fun HomeScreen(
    viewModel: TanweerViewModel
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val todayDate = remember { TanweerViewModel.getTodayDateString() }
    val dayOfWeek = remember { TanweerViewModel.getDayOfWeekIndex() }

    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId ?: "" }

    val slots by viewModel.repository.getScheduleSlots(activeGroupId, dayOfWeek)
        .collectAsStateWithLifecycle(initialValue = emptyList())

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
    val upcomingExamsCount = exams.size
    val upcomingEventsCount = events.size

    val gradeName = currentUser?.gradeId?.let { SchoolHierarchy.getGradeName(it) } ?: "المرحلة الدراسية"
    val sectionAr = currentUser?.sectionId?.let { SchoolHierarchy.getSectionArabicName(it) } ?: ""

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header & Class Identity
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "تـنـويـر ✨",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = CyanAccent
                    )
                    Text(
                        text = TanweerViewModel.getFormattedArabicDate(todayDate),
                        fontSize = 13.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
                GlassPill(
                    text = "$gradeName — $sectionAr",
                    color = CyanAccent,
                    bgColor = CyanGlow
                )
            }
        }

        // Today Study Day Summary Glass Card
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "📚", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "يومك الدراسي",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "${(progressPercent * 100).toInt()}% مكتمل",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (progressPercent >= 0.8f) EmeraldGreen else WarmAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    GlassProgressBar(
                        progress = progressPercent,
                        primaryColor = if (progressPercent >= 0.8f) EmeraldGreen else CyanAccent
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${slots.size} حصص في الجدول",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "✅ $docCount موثقة",
                                fontSize = 13.sp,
                                color = EmeraldGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "🟡 ${pendingSlots.size} تحتاج مساهمة",
                                fontSize = 13.sp,
                                color = WarmAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Quick Obligation Stat Pills
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (upcomingHwCount > 0) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(GlassSurface)
                            .border(1.dp, GlassBorderSubtle, RoundedCornerShape(14.dp))
                            .clickable { viewModel.setTab(NavigationTab.HOMEWORK) }
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📝", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$upcomingHwCount واجبات",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WarmAmber,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
                if (upcomingExamsCount > 0) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(GlassSurface)
                            .border(1.dp, GlassBorderSubtle, RoundedCornerShape(14.dp))
                            .clickable { viewModel.setTab(NavigationTab.EXAMS) }
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔴", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$upcomingExamsCount اختبارات",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = RubyRed,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
                if (upcomingEventsCount > 0) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(GlassSurface)
                            .border(1.dp, GlassBorderSubtle, RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎉", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$upcomingEventsCount أحداث",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Empty State if no slots added yet
        if (slots.isEmpty()) {
            item {
                EmptyStateGlass(
                    title = "جدول اليوم الدراسي فارغ",
                    subtitle = "لم يتم إدخال حصص اليوم في جدول الشعبة بعد. يمكنك إعداد جدول الحصص الآن.",
                    icon = "📅",
                    actionButtonText = "إضافة حصص إلى الجدول",
                    onActionClick = { viewModel.setSubScreen(com.magd.tanweer.ui.SubScreen.SCHEDULE) }
                )
            }
        }

        // Section: Documented Subjects (المواد الموثقة)
        if (documentedSlots.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✅ المواد الموثقة اليوم",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen
                    )
                }
            }

            items(documentedSlots, key = { it.id }) { slot ->
                val lessons = todayContents.filter { it.subjectId == slot.subjectId }
                DocumentedSubjectCard(
                    slot = slot,
                    lessons = lessons,
                    onAddMore = { viewModel.openUploadDialog(slot.subjectId) }
                )
            }
        }

        // Section: Needs Contribution (تحتاج مساهمة)
        if (pendingSlots.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🟡 تحتاج مساهمة وتوثيق",
                        fontSize = 16.sp,
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

        // Next School Day preview
        item {
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MidnightSurface,
                borderColor = GlassBorderSubtle
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🗓️ ${TanweerViewModel.getNextSchoolDayName()}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent
                        )
                        Text(
                            text = "تصفح جدول وتحضير اليوم الدراسي القادم",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    IconButton(onClick = { viewModel.setTab(NavigationTab.CALENDAR) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "التقويم",
                            tint = CyanAccent
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentedSubjectCard(
    slot: ScheduleSlot,
    lessons: List<ContentItem>,
    onAddMore: () -> Unit
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
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreen.copy(alpha = 0.15f))
                            .border(1.dp, EmeraldGreen.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = slot.subjectIcon, fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = slot.subjectName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "✅", fontSize = 12.sp)
                        }
                        Text(
                            text = "${lessons.size} دروس موثقة • الحصة ${slot.slotOrder}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = onAddMore,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GlassSurfaceLight)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "إضافة مساهمة", tint = CyanAccent, modifier = Modifier.size(18.dp))
                    }
                    GlassOutlinedButton(
                        text = if (expanded) "إخفاء" else "فتح",
                        onClick = { expanded = !expanded },
                        modifier = Modifier.height(36.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = GlassBorderSubtle, modifier = Modifier.padding(bottom = 10.dp))
                    lessons.forEach { lesson ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MidnightSurface)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "📚 ${lesson.title}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (!lesson.description.isNullOrBlank()) {
                                Text(
                                    text = lesson.description,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "رفع بواسطة: ${lesson.authorName}",
                                    fontSize = 11.sp,
                                    color = CyanAccent
                                )
                                Text(
                                    text = "👍 ${lesson.usefulCount} مفيد",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
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
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(WarmAmber.copy(alpha = 0.15f))
                        .border(1.dp, WarmAmber.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = slot.subjectIcon, fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = slot.subjectName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "🟡", fontSize = 12.sp)
                    }
                    Text(
                        text = "الحصة ${slot.slotOrder} • لا توجد مساهمة بعد",
                        fontSize = 12.sp,
                        color = WarmAmber
                    )
                }
            }

            GlassButton(
                text = "أساهم الآن",
                icon = Icons.Default.Add,
                color = WarmAmber,
                textColor = TextOnAccent,
                onClick = onContribute,
                modifier = Modifier.height(38.dp)
            )
        }
    }
}

fun isAcademicSubjectRequiringDocumentation(slot: ScheduleSlot): Boolean {
    val sid = slot.subjectId.lowercase()
    val name = slot.subjectName.lowercase()
    if (sid == "pe" || sid == "sports" || (name.contains("رياض") && !name.contains("رياضيات"))) return false
    if (sid == "art" || sid == "activity" || name.contains("فني") || name.contains("رسم") || name.contains("نشاط")) return false
    if (sid == "computer" || name.contains("حاسوب")) return false
    return true
}
