package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Add
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
import com.example.data.model.SubjectItem
import com.example.ui.TanweerViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

data class RealTimelineItem(
    val date: String,
    val title: String,
    val description: String,
    val author: String,
    val type: String // "LESSON", "HOMEWORK", "EXAM"
)

@Composable
fun SubjectTimelineScreen(
    viewModel: TanweerViewModel
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId ?: "" }

    val gradeSubjects = remember(currentUser?.gradeId) {
        viewModel.getScheduleSubjectsForCurrentGrade()
    }

    var selectedSubject by remember {
        mutableStateOf(gradeSubjects.firstOrNull() ?: SubjectItem("math", "الرياضيات", "📐", "#00E5FF"))
    }

    // Real database flows
    val todayDate = remember { TanweerViewModel.getTodayDateString() }
    val dayContents by viewModel.repository.getDayContents(activeGroupId, todayDate)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val homeworks by viewModel.repository.getHomeworks(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val exams by viewModel.repository.getExams(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Combine ONLY real student data for this subject
    val realTimelineEvents = remember(selectedSubject, dayContents, homeworks, exams) {
        val list = mutableListOf<RealTimelineItem>()

        dayContents.filter { it.subjectId == selectedSubject.id }.forEach { c ->
            list.add(
                RealTimelineItem(
                    date = TanweerViewModel.getFormattedArabicDate(c.studyDate),
                    title = "درس: ${c.title}",
                    description = c.description ?: "توثيق درس رسمي للشعبة",
                    author = c.authorName,
                    type = "LESSON"
                )
            )
        }

        homeworks.filter { it.subjectId == selectedSubject.id }.forEach { h ->
            list.add(
                RealTimelineItem(
                    date = "تسليم: ${h.dueDate}",
                    title = "واجب: ${h.title}",
                    description = h.details ?: (h.pageNumbers?.let { "صفحات: $it" } ?: "واجب منزلي مقرر"),
                    author = "مهمة شعبة",
                    type = "HOMEWORK"
                )
            )
        }

        exams.filter { it.subjectId == selectedSubject.id }.forEach { e ->
            list.add(
                RealTimelineItem(
                    date = "موعد: ${e.examDate}",
                    title = "اختبار: ${e.title}",
                    description = e.requiredChapters ?: "اختبار رسمي معتمد",
                    author = e.notes ?: "إدارة الشعبة",
                    type = "EXAM"
                )
            )
        }

        list
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🗺️ رحلة المادة والخط الزمني",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = CyanAccent
                    )
                    Text(
                        text = "الدروس والواجبات والاختبارات الحقيقية الموثقة للمادة",
                        fontSize = 12.sp,
                        color = TextSecondary
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

        // Timeline Section
        if (realTimelineEvents.isEmpty()) {
            item {
                EmptyStateGlass(
                    title = "لا توجد دروس أو واجبات مسجلة لمادة ${selectedSubject.name}",
                    subtitle = "لم يقم أي طالب بتوثيق درس أو واجب لهذه المادة بعد. يمكنك المساهمة وتوثيق أول درس الآن.",
                    icon = selectedSubject.icon,
                    actionButtonText = "توثيق درس لمادة ${selectedSubject.name}",
                    onActionClick = { viewModel.openUploadDialog(selectedSubject.id) }
                )
            }
        } else {
            items(realTimelineEvents) { event ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // Node indicator
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(end = 12.dp, top = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(
                                    when (event.type) {
                                        "EXAM" -> RubyRed
                                        "HOMEWORK" -> WarmAmber
                                        else -> CyanAccent
                                    }
                                )
                        )
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(50.dp)
                                .background(GlassBorderSubtle)
                        )
                    }

                    // Event Details Card
                    GlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = 6.dp),
                        backgroundColor = GlassSurface
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
                                Text(
                                    text = event.date,
                                    fontSize = 11.sp,
                                    color = when (event.type) {
                                        "EXAM" -> RubyRed
                                        "HOMEWORK" -> WarmAmber
                                        else -> CyanAccent
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = event.description,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                text = "المساهم: ${event.author}",
                                fontSize = 10.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
