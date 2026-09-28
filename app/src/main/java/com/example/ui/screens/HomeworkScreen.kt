package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QuestionMark
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
import com.example.data.model.DefaultSubjects
import com.example.data.model.HomeworkItem
import com.example.ui.NavigationTab
import com.example.ui.TanweerViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

enum class HomeworkFilter {
    ALL,
    TODAY,
    UPCOMING,
    COMPLETED
}

@Composable
fun HomeworkScreen(
    viewModel: TanweerViewModel
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId ?: "" }

    val homeworks by viewModel.repository.getHomeworks(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedFilter by remember { mutableStateOf(HomeworkFilter.ALL) }
    var isAddModalOpen by remember { mutableStateOf(false) }

    val todayDate = remember { TanweerViewModel.getTodayDateString() }

    val filteredHomeworks = remember(homeworks, selectedFilter) {
        when (selectedFilter) {
            HomeworkFilter.ALL -> homeworks
            HomeworkFilter.TODAY -> homeworks.filter { it.dueDate == todayDate }
            HomeworkFilter.UPCOMING -> homeworks.filter { !it.isCompleted && it.dueDate >= todayDate }
            HomeworkFilter.COMPLETED -> homeworks.filter { it.isCompleted }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header & Add Action
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "📝 الواجبات والمهام",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = CyanAccent
                        )
                        Text(
                            text = "متابعة وإنجاز التكاليف المدرسية اليومية",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    GlassButton(
                        text = "إضافة واجب",
                        icon = Icons.Default.Add,
                        onClick = { isAddModalOpen = true },
                        modifier = Modifier.height(40.dp)
                    )
                }
            }

            // Filter Tabs Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MidnightSurface)
                        .border(1.dp, GlassBorderSubtle, RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        HomeworkFilter.ALL to "الكل (${homeworks.size})",
                        HomeworkFilter.TODAY to "اليوم",
                        HomeworkFilter.UPCOMING to "القادمة",
                        HomeworkFilter.COMPLETED to "المكتملة"
                    ).forEach { (filter, label) ->
                        val isSelected = selectedFilter == filter
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) CyanAccent else Color.Transparent)
                                .clickable { selectedFilter = filter }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TextOnAccent else TextSecondary
                            )
                        }
                    }
                }
            }

            if (filteredHomeworks.isEmpty()) {
                item {
                    EmptyStateGlass(
                        title = "لا توجد واجبات مسجلة هنا",
                        subtitle = "يمكنك إضافة واجب جديد بالضغط على زر إضافة واجب أعلاه",
                        icon = "📝",
                        actionButtonText = "إضافة واجب جديد",
                        onActionClick = { isAddModalOpen = true }
                    )
                }
            } else {
                items(filteredHomeworks, key = { it.id }) { hw ->
                    HomeworkCard(
                        homework = hw,
                        onToggle = { viewModel.toggleHomework(hw.id, hw.isCompleted) },
                        onAskQuestion = {
                            viewModel.addIssue(
                                title = "استفسار حول واجب ${hw.subjectName}: ${hw.title}",
                                description = "ما هو المطلوب بالتحديد في هذا الواجب؟",
                                subjectId = hw.subjectId,
                                homeworkId = hw.id
                            )
                            viewModel.setTab(NavigationTab.ISSUES)
                        }
                    )
                }
            }
        }

        // Add Homework Dialog
        if (isAddModalOpen) {
            AddHomeworkDialog(
                onDismiss = { isAddModalOpen = false },
                onAdd = { subjId, title, details, pages, questions, dueDate ->
                    viewModel.addHomework(
                        subjectId = subjId,
                        title = title,
                        details = details,
                        pageNumbers = pages,
                        questionNumbers = questions,
                        dueDate = dueDate
                    )
                    isAddModalOpen = false
                }
            )
        }
    }
}

@Composable
fun HomeworkCard(
    homework: HomeworkItem,
    onToggle: () -> Unit,
    onAskQuestion: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (homework.isCompleted) EmeraldGreen.copy(alpha = 0.3f) else WarmAmber.copy(alpha = 0.4f),
        backgroundColor = GlassSurface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyanGlow),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = homework.subjectIcon, fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "${homework.subjectName}: ${homework.title}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "موعد التسليم: ${homework.dueDate}",
                            fontSize = 12.sp,
                            color = if (homework.isCompleted) EmeraldGreen else WarmAmber
                        )
                    }
                }

                Checkbox(
                    checked = homework.isCompleted,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = EmeraldGreen,
                        uncheckedColor = TextSecondary
                    )
                )
            }

            if (!homework.details.isNullOrBlank() || !homework.pageNumbers.isNullOrBlank() || !homework.questionNumbers.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MidnightSurface)
                        .padding(10.dp)
                ) {
                    if (!homework.details.isNullOrBlank()) {
                        Text(
                            text = homework.details,
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!homework.pageNumbers.isNullOrBlank()) {
                            Text(
                                text = "📖 صفحة: ${homework.pageNumbers}",
                                fontSize = 12.sp,
                                color = CyanAccent,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (!homework.questionNumbers.isNullOrBlank()) {
                            Text(
                                text = "✏️ الأسئلة: ${homework.questionNumbers}",
                                fontSize = 12.sp,
                                color = WarmAmber,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (homework.isCompleted) "تم إنجازه بنجاح ✅" else "لم يكتمل بعد ⏳",
                    fontSize = 11.sp,
                    color = if (homework.isCompleted) EmeraldGreen else TextMuted
                )

                GlassOutlinedButton(
                    text = "لدي سؤال حول الواجب",
                    icon = Icons.Default.QuestionMark,
                    onClick = onAskQuestion,
                    modifier = Modifier.height(34.dp)
                )
            }
        }
    }
}

@Composable
fun AddHomeworkDialog(
    onDismiss: () -> Unit,
    onAdd: (subjectId: String, title: String, details: String?, pages: String?, questions: String?, dueDate: String) -> Unit
) {
    var selectedSubjId by remember { mutableStateOf(DefaultSubjects[0].id) }
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var pageNumbers by remember { mutableStateOf("") }
    var questionNumbers by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(TanweerViewModel.getTodayDateString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "إضافة واجب جديد 📝",
                color = CyanAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("اختر المادة:", fontSize = 12.sp, color = TextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DefaultSubjects.take(4).forEach { subj ->
                        val isSelected = subj.id == selectedSubjId
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) CyanGlow else MidnightSurface)
                                .border(1.dp, if (isSelected) CyanAccent else GlassBorderSubtle, RoundedCornerShape(10.dp))
                                .clickable { selectedSubjId = subj.id }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = subj.name,
                                fontSize = 11.sp,
                                color = if (isSelected) CyanAccent else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                GlassTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "عنوان الواجب",
                    placeholder = "مثال: حل تمارين المتتاليات"
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassTextField(
                        value = pageNumbers,
                        onValueChange = { pageNumbers = it },
                        label = "رقم الصفحة",
                        placeholder = "42 - 43",
                        modifier = Modifier.weight(1f)
                    )
                    GlassTextField(
                        value = questionNumbers,
                        onValueChange = { questionNumbers = it },
                        label = "أرقام الأسئلة",
                        placeholder = "1 إلى 8",
                        modifier = Modifier.weight(1f)
                    )
                }

                GlassTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = "تفاصيل إضافية (اختياري)",
                    placeholder = "ملاحظات المعلم أو شروط الحل..."
                )

                GlassTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = "موعد التسليم (YYYY-MM-DD)",
                    placeholder = "2026-09-28"
                )
            }
        },
        confirmButton = {
            GlassButton(
                text = "حفظ ونشر الواجب",
                onClick = {
                    if (title.isNotBlank()) {
                        onAdd(selectedSubjId, title, details, pageNumbers, questionNumbers, dueDate)
                    }
                }
            )
        },
        dismissButton = {
            GlassOutlinedButton(text = "إلغاء", onClick = onDismiss)
        },
        containerColor = MidnightSurface,
        shape = RoundedCornerShape(20.dp)
    )
}
