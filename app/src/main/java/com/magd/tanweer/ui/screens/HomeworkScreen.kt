package com.magd.tanweer.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import com.magd.tanweer.data.model.HomeworkItem
import com.magd.tanweer.data.model.SubjectItem
import com.magd.tanweer.ui.NavigationTab
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

enum class HomeworkFilter(val label: String, val icon: String) {
    ALL("الكل", "📋"),
    TODAY("اليوم", "⚡"),
    TOMORROW("غداً", "🌅"),
    THIS_WEEK("هذا الأسبوع", "🗓️"),
    OVERDUE("متأخر", "⚠️"),
    COMPLETED("المكتملة", "✅")
}

@Composable
fun HomeworkScreen(
    viewModel: TanweerViewModel
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId ?: "" }

    val homeworks by viewModel.repository.getHomeworks(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val gradeSubjects = remember(currentUser?.gradeId) {
        viewModel.getSubjectsForCurrentGrade()
    }

    var selectedFilter by remember { mutableStateOf(HomeworkFilter.ALL) }
    var selectedTaskTypeFilter by remember { mutableStateOf<String?>(null) } // null = All, "HOMEWORK", "TASK"
    var isAddModalOpen by remember { mutableStateOf(false) }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }
    val todayDate = remember { sdf.format(Date()) }

    val tomorrowDate = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        sdf.format(cal.time)
    }

    val endOfWeekDate = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 7)
        sdf.format(cal.time)
    }

    val filteredHomeworks = remember(homeworks, selectedFilter, selectedTaskTypeFilter) {
        val typeFiltered = if (selectedTaskTypeFilter == null) {
            homeworks
        } else {
            homeworks.filter { it.taskType == selectedTaskTypeFilter }
        }

        when (selectedFilter) {
            HomeworkFilter.ALL -> typeFiltered
            HomeworkFilter.TODAY -> typeFiltered.filter { it.dueDate == todayDate }
            HomeworkFilter.TOMORROW -> typeFiltered.filter { it.dueDate == tomorrowDate }
            HomeworkFilter.THIS_WEEK -> typeFiltered.filter { it.dueDate in todayDate..endOfWeekDate }
            HomeworkFilter.OVERDUE -> typeFiltered.filter { !it.isCompleted && it.dueDate < todayDate }
            HomeworkFilter.COMPLETED -> typeFiltered.filter { it.isCompleted }
        }
    }

    val overdueCount = remember(homeworks, todayDate) {
        homeworks.count { !it.isCompleted && it.dueDate < todayDate }
    }
    val todayCount = remember(homeworks, todayDate) {
        homeworks.count { it.dueDate == todayDate }
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
                            text = "📝 الواجبات والتكاليف",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = CyanAccent
                        )
                        Text(
                            text = "متابعة وإنجاز التكاليف والواجبات المدرسية",
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

            // Task Type Segmented Chips (All, Homework, Task/Project)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTaskTypeFilter == null,
                        onClick = { selectedTaskTypeFilter = null },
                        label = { Text("جميع التكاليف (${homeworks.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanAccent,
                            selectedLabelColor = TextOnAccent
                        )
                    )
                    FilterChip(
                        selected = selectedTaskTypeFilter == "HOMEWORK",
                        onClick = { selectedTaskTypeFilter = if (selectedTaskTypeFilter == "HOMEWORK") null else "HOMEWORK" },
                        label = { Text("واجبات مدرسية 📝") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanAccent,
                            selectedLabelColor = TextOnAccent
                        )
                    )
                    FilterChip(
                        selected = selectedTaskTypeFilter == "TASK",
                        onClick = { selectedTaskTypeFilter = if (selectedTaskTypeFilter == "TASK") null else "TASK" },
                        label = { Text("مشاريع وأبحاث 🎯") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanAccent,
                            selectedLabelColor = TextOnAccent
                        )
                    )
                }
            }

            // Filter Tabs Bar (Horizontal Scrollable for Clean Touch)
            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MidnightSurface)
                        .border(1.dp, GlassBorderSubtle, RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(HomeworkFilter.values()) { filter ->
                        val isSelected = selectedFilter == filter
                        val badgeCount = when (filter) {
                            HomeworkFilter.ALL -> homeworks.size
                            HomeworkFilter.TODAY -> todayCount
                            HomeworkFilter.OVERDUE -> overdueCount
                            HomeworkFilter.COMPLETED -> homeworks.count { it.isCompleted }
                            else -> null
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) CyanAccent else Color.Transparent)
                                .clickable { selectedFilter = filter }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = filter.icon, fontSize = 12.sp)
                                Text(
                                    text = filter.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) TextOnAccent else if (filter == HomeworkFilter.OVERDUE && overdueCount > 0) Color(0xFFFF5252) else TextSecondary
                                )
                                if (badgeCount != null && badgeCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isSelected) TextOnAccent.copy(alpha = 0.2f) else if (filter == HomeworkFilter.OVERDUE) Color(0xFFFF5252) else GlassSurfaceLight)
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "$badgeCount",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) TextOnAccent else if (filter == HomeworkFilter.OVERDUE) Color.White else TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (filteredHomeworks.isEmpty()) {
                item {
                    EmptyStateGlass(
                        title = "لا توجد واجبات مسجلة في هذا القسم",
                        subtitle = "يمكنك إضافة واجب جديد أو تغيير خيارات الفلترة أعلاه",
                        icon = "📝",
                        actionButtonText = "إضافة واجب جديد",
                        onActionClick = { isAddModalOpen = true }
                    )
                }
            } else {
                items(filteredHomeworks, key = { it.id }) { hw ->
                    val isOverdue = !hw.isCompleted && hw.dueDate < todayDate
                    val isDueToday = hw.dueDate == todayDate

                    HomeworkCard(
                        homework = hw,
                        isOverdue = isOverdue,
                        isDueToday = isDueToday,
                        onToggle = { viewModel.toggleHomework(hw.id, hw.isCompleted) },
                        onAskQuestion = {
                            viewModel.addIssue(
                                title = "استفسار حول واجب ${hw.subjectName}: ${hw.title}",
                                description = "ما هو المطلوب بالتحديد في هذا الواجب؟ صفحة: ${hw.pageNumbers ?: "-"}، أسئلة: ${hw.questionNumbers ?: "-"}",
                                subjectId = hw.subjectId,
                                homeworkId = hw.id
                            )
                            Toast.makeText(context, "تم فتح استفسار مرتبط بالواجب بنجاح ❓", Toast.LENGTH_SHORT).show()
                            viewModel.setTab(NavigationTab.ISSUES)
                        }
                    )
                }
            }
        }

        // Add Homework Dialog
        if (isAddModalOpen) {
            AddHomeworkDialog(
                subjects = gradeSubjects,
                onDismiss = { isAddModalOpen = false },
                onAdd = { subjId, title, details, pages, questions, dueDate, taskType ->
                    viewModel.addHomework(
                        subjectId = subjId,
                        title = title,
                        details = details,
                        pageNumbers = pages,
                        questionNumbers = questions,
                        dueDate = dueDate,
                        taskType = taskType
                    )
                    Toast.makeText(context, "تم حفظ ونشر الواجب بنجاح ✨", Toast.LENGTH_SHORT).show()
                    isAddModalOpen = false
                }
            )
        }
    }
}

@Composable
fun HomeworkCard(
    homework: HomeworkItem,
    isOverdue: Boolean = false,
    isDueToday: Boolean = false,
    onToggle: () -> Unit,
    onAskQuestion: () -> Unit
) {
    val borderColor = when {
        homework.isCompleted -> EmeraldGreen.copy(alpha = 0.4f)
        isOverdue -> Color(0xFFFF5252).copy(alpha = 0.5f)
        isDueToday -> WarmAmber.copy(alpha = 0.5f)
        else -> GlassBorderSubtle
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = borderColor,
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
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isOverdue) Color(0xFFFF5252).copy(alpha = 0.15f) else CyanGlow),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = homework.subjectIcon, fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "${homework.subjectName}: ${homework.title}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (homework.taskType == "TASK") {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(WarmAmber.copy(alpha = 0.15f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text("مشروع/بحث 🎯", fontSize = 9.sp, color = WarmAmber, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = "موعد التسليم: ${homework.dueDate}",
                                fontSize = 12.sp,
                                color = when {
                                    homework.isCompleted -> EmeraldGreen
                                    isOverdue -> Color(0xFFFF5252)
                                    isDueToday -> WarmAmber
                                    else -> TextSecondary
                                },
                                fontWeight = if (isOverdue || isDueToday) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isOverdue) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFFF5252).copy(alpha = 0.2f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text("متأخر ⚠️", fontSize = 9.sp, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                                }
                            } else if (isDueToday && !homework.isCompleted) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(WarmAmber.copy(alpha = 0.2f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text("مستحق اليوم ⚡", fontSize = 9.sp, color = WarmAmber, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
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
                            .padding(top = if (!homework.details.isNullOrBlank()) 6.dp else 0.dp),
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

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (homework.isCompleted) "تم إنجازه بنجاح ✅" else if (isOverdue) "فات موعد التسليم ⚠️" else "لم يكتمل بعد ⏳",
                    fontSize = 11.sp,
                    color = if (homework.isCompleted) EmeraldGreen else if (isOverdue) Color(0xFFFF5252) else TextMuted
                )

                // Ask Question Action linked with homework
                OutlinedButton(
                    onClick = onAskQuestion,
                    modifier = Modifier.height(34.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.QuestionMark, contentDescription = "اسأل عن الواجب", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("❓ اسأل عن الواجب", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AddHomeworkDialog(
    subjects: List<SubjectItem>,
    onDismiss: () -> Unit,
    onAdd: (subjectId: String, title: String, details: String?, pages: String?, questions: String?, dueDate: String, taskType: String) -> Unit
) {
    var selectedSubjId by remember { mutableStateOf(subjects.firstOrNull()?.id ?: "math") }
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var pageNumbers by remember { mutableStateOf("") }
    var questionNumbers by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(TanweerViewModel.getTodayDateString()) }
    var taskType by remember { mutableStateOf("HOMEWORK") } // 'HOMEWORK' or 'TASK'

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "إضافة واجب أو تكليف جديد 📝",
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
                // Task Type Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (taskType == "HOMEWORK") CyanAccent else MidnightSurface)
                            .border(1.dp, if (taskType == "HOMEWORK") CyanAccent else GlassBorderSubtle, RoundedCornerShape(10.dp))
                            .clickable { taskType = "HOMEWORK" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "واجب مدرسي 📝",
                            fontSize = 12.sp,
                            fontWeight = if (taskType == "HOMEWORK") FontWeight.Bold else FontWeight.Normal,
                            color = if (taskType == "HOMEWORK") TextOnAccent else TextPrimary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (taskType == "TASK") CyanAccent else MidnightSurface)
                            .border(1.dp, if (taskType == "TASK") CyanAccent else GlassBorderSubtle, RoundedCornerShape(10.dp))
                            .clickable { taskType = "TASK" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "مشروع / تكليف 🎯",
                            fontSize = 12.sp,
                            fontWeight = if (taskType == "TASK") FontWeight.Bold else FontWeight.Normal,
                            color = if (taskType == "TASK") TextOnAccent else TextPrimary
                        )
                    }
                }

                Text("اختر المادة الدراسية:", fontSize = 12.sp, color = TextSecondary)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(subjects) { subj ->
                        val isSelected = subj.id == selectedSubjId
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) CyanGlow else MidnightSurface)
                                .border(1.dp, if (isSelected) CyanAccent else GlassBorderSubtle, RoundedCornerShape(10.dp))
                                .clickable { selectedSubjId = subj.id }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${subj.icon} ${subj.name}",
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
                    label = "عنوان الواجب أو التكليف *",
                    placeholder = "مثال: حل تمارين المتتاليات الحسابية"
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
                    placeholder = "ملاحظات المعلم أو شروط ومواصفات الحل..."
                )

                GlassTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = "موعد التسليم (YYYY-MM-DD)",
                    placeholder = "2026-09-30"
                )
            }
        },
        confirmButton = {
            GlassButton(
                text = "حفظ ونشر الواجب ✨",
                enabled = title.isNotBlank(),
                onClick = {
                    if (title.isNotBlank()) {
                        onAdd(selectedSubjId, title, details, pageNumbers, questionNumbers, dueDate, taskType)
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
