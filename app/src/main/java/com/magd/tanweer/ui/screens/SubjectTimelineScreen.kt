package com.magd.tanweer.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.SubjectItem
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*

data class RealTimelineItem(
    val rawDate: String,
    val formattedDate: String,
    val title: String,
    val description: String,
    val author: String,
    val type: String, // "LESSON", "HOMEWORK", "EXAM"
    val isCompleted: Boolean = false
)

@Composable
fun SubjectTimelineScreen(
    viewModel: TanweerViewModel
) {
    BackHandler {
        viewModel.setSubScreen(SubScreen.NONE)
    }

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId ?: "" }

    val gradeSubjects = remember(currentUser?.gradeId) {
        viewModel.getScheduleSubjectsForCurrentGrade()
    }

    var selectedSubject by remember {
        mutableStateOf(gradeSubjects.firstOrNull() ?: SubjectItem("math", "الرياضيات", "📐", "#00E5FF"))
    }

    var sortDescending by remember { mutableStateOf(true) }

    // Fetch ALL lessons for the selected subject
    val subjectContents by viewModel.repository.getContentsBySubject(activeGroupId, selectedSubject.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val homeworks by viewModel.repository.getHomeworks(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val exams by viewModel.repository.getExams(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val todayStr = remember { TanweerViewModel.getTodayDateString() }

    // Build real chronological timeline across all historical and upcoming events of this subject
    val realTimelineEvents = remember(selectedSubject, subjectContents, homeworks, exams, sortDescending) {
        val list = mutableListOf<RealTimelineItem>()

        subjectContents.forEach { c ->
            list.add(
                RealTimelineItem(
                    rawDate = c.studyDate,
                    formattedDate = TanweerViewModel.getFormattedArabicDate(c.studyDate),
                    title = "درس: ${c.title}",
                    description = c.description ?: "توثيق درس رسمي للشعبة",
                    author = c.authorName.ifBlank { "طالب مساهم" },
                    type = "LESSON"
                )
            )
        }

        homeworks.filter { it.subjectId == selectedSubject.id }.forEach { h ->
            list.add(
                RealTimelineItem(
                    rawDate = h.dueDate.ifBlank { h.studyDate },
                    formattedDate = "تسليم: ${h.dueDate}",
                    title = "واجب: ${h.title}",
                    description = h.details ?: (h.pageNumbers?.let { "صفحات: $it" } ?: "واجب منزلي مقرر"),
                    author = if (h.isCompleted) "منجز بواسطةك ✓" else "مهمة شعبة",
                    type = "HOMEWORK",
                    isCompleted = h.isCompleted
                )
            )
        }

        exams.filter { it.subjectId == selectedSubject.id }.forEach { e ->
            list.add(
                RealTimelineItem(
                    rawDate = e.examDate,
                    formattedDate = "موعد: ${e.examDate}",
                    title = "اختبار: ${e.title}",
                    description = e.requiredChapters ?: "اختبار رسمي معتمد",
                    author = e.notes ?: "إدارة الشعبة",
                    type = "EXAM"
                )
            )
        }

        if (sortDescending) {
            list.sortedByDescending { it.rawDate }
        } else {
            list.sortedBy { it.rawDate }
        }
    }

    val subjectThemeColor = try {
        Color(android.graphics.Color.parseColor(selectedSubject.colorHex))
    } catch (_: Exception) {
        CyanAccent
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.setSubScreen(SubScreen.NONE) },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(GlassSurface)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = CyanAccent
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "🗺️ رحلة المادة والخط الزمني",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = CyanAccent
                        )
                        Text(
                            text = "التسلسل الزمني الكامل للدروس والواجبات والاختبارات",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                // Sort toggle button
                IconButton(
                    onClick = { sortDescending = !sortDescending },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(GlassSurface)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "ترتيب زمني",
                        tint = CyanAccent
                    )
                }
            }
        }

        // Horizontal Subject Selector
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(gradeSubjects, key = { it.id }) { subj ->
                    val isSelected = subj.id == selectedSubject.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) CyanGlow else MidnightSurface)
                            .border(1.dp, if (isSelected) CyanAccent else GlassBorderSubtle, RoundedCornerShape(12.dp))
                            .clickable { selectedSubject = subj }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = subj.icon, fontSize = 16.sp)
                            Text(
                                text = subj.name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) CyanAccent else TextPrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Subject Overview Card
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = subjectThemeColor.copy(alpha = 0.5f),
                backgroundColor = GlassSurface
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedSubject.icon, fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = selectedSubject.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${subjectContents.size} دروس • ${homeworks.filter { it.subjectId == selectedSubject.id }.size} واجبات • ${exams.filter { it.subjectId == selectedSubject.id }.size} اختبارات",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                    GlassPill(
                        text = if (sortDescending) "الأحدث أولاً ⏳" else "من البداية ⏳",
                        color = subjectThemeColor,
                        bgColor = subjectThemeColor.copy(alpha = 0.15f)
                    )
                }
            }
        }

        // Timeline Section
        if (realTimelineEvents.isEmpty()) {
            item {
                EmptyStateGlass(
                    title = "لا توجد محطات مسجلة لمادة ${selectedSubject.name}",
                    subtitle = "لم يقم أي طالب بتوثيق دروس أو واجبات لهذه المادة بعد. بادر بتوثيق أول درس للشعبة الآن.",
                    icon = selectedSubject.icon,
                    actionButtonText = "توثيق درس لمادة ${selectedSubject.name}",
                    onActionClick = { viewModel.openUploadDialog(selectedSubject.id) }
                )
            }
        } else {
            items(realTimelineEvents) { event ->
                val isToday = event.rawDate == todayStr
                val isFuture = event.rawDate > todayStr

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // Node indicator with vertical line
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(end = 12.dp, top = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    when (event.type) {
                                        "EXAM" -> RubyRed
                                        "HOMEWORK" -> if (event.isCompleted) EmeraldGreen else WarmAmber
                                        else -> subjectThemeColor
                                    }
                                )
                                .border(
                                    2.dp,
                                    if (isToday) Color.White else Color.Transparent,
                                    CircleShape
                                )
                        )
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(56.dp)
                                .background(GlassBorderSubtle)
                        )
                    }

                    // Event Details Card
                    GlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = 6.dp),
                        backgroundColor = GlassSurface,
                        borderColor = if (isToday) CyanAccent else GlassBorderSubtle
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = event.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                GlassPill(
                                    text = when (event.type) {
                                        "EXAM" -> "اختبار 🔴"
                                        "HOMEWORK" -> if (event.isCompleted) "واجب منجز ✓" else "واجب 📝"
                                        else -> "درس 📖"
                                    },
                                    color = when (event.type) {
                                        "EXAM" -> RubyRed
                                        "HOMEWORK" -> if (event.isCompleted) EmeraldGreen else WarmAmber
                                        else -> subjectThemeColor
                                    },
                                    bgColor = when (event.type) {
                                        "EXAM" -> RubyRed.copy(alpha = 0.15f)
                                        "HOMEWORK" -> if (event.isCompleted) EmeraldGreen.copy(alpha = 0.15f) else WarmAmber.copy(alpha = 0.15f)
                                        else -> subjectThemeColor.copy(alpha = 0.15f)
                                    }
                                )
                            }
                            Text(
                                text = event.description,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "بواسطة: ${event.author}",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                                Text(
                                    text = event.formattedDate,
                                    fontSize = 11.sp,
                                    color = if (isToday) CyanAccent else TextSecondary,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
