package com.magd.tanweer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.ExamItem
import com.magd.tanweer.data.model.HomeworkItem
import com.magd.tanweer.ui.NavigationTab
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*

@Composable
fun TeacherDashboardScreen(
    viewModel: TanweerViewModel
) {
    val dashboardData by viewModel.teacherDashboard.collectAsStateWithLifecycle()
    val isLoading by viewModel.isDashboardLoading.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Overview & Stats, 1: Official Homeworks, 2: Official Exams
    var showCreateOfficialHomeworkSheet by remember { mutableStateOf(false) }
    var showCreateOfficialExamSheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        // Teacher Hero Header
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MidnightSurface,
                borderColor = CyanAccent.copy(alpha = 0.6f)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(listOf(CyanAccent.copy(alpha = 0.25f), EmeraldGreen.copy(alpha = 0.25f)))
                                    )
                                    .border(1.5.dp, CyanAccent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🎓", fontSize = tanweerTextSize(26f))
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = dashboardData?.teacher?.fullName ?: currentUser?.fullName ?: "الأستاذ المعتمد",
                                        fontSize = tanweerTextSize(17f),
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    GlassPill(
                                        text = "أستاذ معتمد ✅",
                                        color = EmeraldGreen,
                                        bgColor = EmeraldGreen.copy(alpha = 0.15f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "مركز التعليم وإدارة المحتوى الأكاديمي الرسمي",
                                    fontSize = tanweerTextSize(12f),
                                    color = TextSecondary
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.loadTeacherDashboard() },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GlassSurface)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = CyanAccent, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Stats Grid
                    val stats = dashboardData?.stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TeacherStatCard(
                            title = "واجبات رسمية",
                            count = "${stats?.officialHomeworksCount ?: 0}",
                            icon = "📝",
                            color = CyanAccent,
                            modifier = Modifier.weight(1f)
                        )
                        TeacherStatCard(
                            title = "اختبارات رسمية",
                            count = "${stats?.officialExamsCount ?: 0}",
                            icon = "📑",
                            color = WarmAmber,
                            modifier = Modifier.weight(1f)
                        )
                        TeacherStatCard(
                            title = "إجابات معتمدة",
                            count = "${stats?.teacherAnswersCount ?: 0}",
                            icon = "⭐",
                            color = EmeraldGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Action Buttons Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassButton(
                    text = "إضافة واجب رسمي 📝",
                    color = CyanAccent,
                    textColor = TextOnAccent,
                    onClick = { showCreateOfficialHomeworkSheet = true },
                    modifier = Modifier.weight(1f)
                )
                GlassButton(
                    text = "إضافة اختبار رسمي 📑",
                    color = WarmAmber,
                    textColor = TextOnAccent,
                    onClick = { showCreateOfficialExamSheet = true },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Section Tabs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassSurface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("نظرة عامة 📊", "الواجبات الرسمية 📝", "الاختبارات الرسمية 📑").forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) CyanAccent else Color.Transparent)
                            .clickable { selectedTab = index }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = tanweerTextSize(12f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) TextOnAccent else TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Content based on selected Tab
        when (selectedTab) {
            0 -> {
                // Overview & Guidance
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MidnightSurface
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "💡 سلطات وصلاحيات الأستاذ في المنظومة:",
                                fontSize = tanweerTextSize(14f),
                                fontWeight = FontWeight.Bold,
                                color = WarmAmber
                            )
                            TeacherGuidelineItem(
                                icon = "📌",
                                title = "الواجبات والاختبارات الرسمية",
                                desc = "أي واجب أو اختبار تنشئه يحمل تلقائياً وسام (معتمد من الأستاذ) وتكون له الأولوية في جداول الطلاب وتنبيهاتهم."
                            )
                            TeacherGuidelineItem(
                                icon = "💬",
                                title = "إجابات الأسئلة المعتمدة",
                                desc = "إجابتك على أي استفسار في قسم الاستفسارات تُعتمد فورياً كـ (Teacher Answer) وتُثبت في أعلى النقاش."
                            )
                            TeacherGuidelineItem(
                                icon = "📅",
                                title = "إدارة وتعديل الجدول المدرسي",
                                desc = "يمكنك تعديل وتثبيت الجدول مباشرة دون الحاجة لفتح تصويت عام، أو اعتماد مقترحات الطلاب بضغطة زر."
                            )
                            TeacherGuidelineItem(
                                icon = "✍️",
                                title = "مراجعة وتصويب الدروس",
                                desc = "لديك الصلاحية الكاملة لاعتماد وتصويب شروحات وتلاخيص الطلاب المرفوعة في المنظومة."
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "آخر الواجبات الرسمية المسجلة:",
                        fontSize = tanweerTextSize(14f),
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                val recentHw = dashboardData?.recentHomeworks ?: emptyList()
                if (recentHw.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                            Text(
                                text = "لم تقم بإضافة واجبات بعد، اضغط على زر إضافة واجب للبدء.",
                                fontSize = tanweerTextSize(12f),
                                color = TextMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(12.dp)
                            )
                        }
                    }
                } else {
                    items(recentHw) { hw ->
                        OfficialHomeworkCard(hw = hw)
                    }
                }
            }

            1 -> {
                // Official Homeworks
                val recentHw = dashboardData?.recentHomeworks ?: emptyList()
                if (recentHw.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                            Text(
                                text = "لا توجد واجبات رسمية مضافة حالياً.",
                                fontSize = tanweerTextSize(13f),
                                color = TextMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(recentHw) { hw ->
                        OfficialHomeworkCard(hw = hw)
                    }
                }
            }

            2 -> {
                // Official Exams
                val recentExams = dashboardData?.recentExams ?: emptyList()
                if (recentExams.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                            Text(
                                text = "لا توجد اختبارات رسمية مسجلة حالياً.",
                                fontSize = tanweerTextSize(13f),
                                color = TextMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(recentExams) { exam ->
                        OfficialExamCard(exam = exam)
                    }
                }
            }
        }
    }

    if (showCreateOfficialHomeworkSheet) {
        CreateOfficialHomeworkSheet(
            onDismiss = { showCreateOfficialHomeworkSheet = false },
            onCreated = {
                viewModel.loadTeacherDashboard()
                showCreateOfficialHomeworkSheet = false
            },
            viewModel = viewModel
        )
    }

    if (showCreateOfficialExamSheet) {
        CreateOfficialExamSheet(
            onDismiss = { showCreateOfficialExamSheet = false },
            onCreated = {
                viewModel.loadTeacherDashboard()
                showCreateOfficialExamSheet = false
            },
            viewModel = viewModel
        )
    }
}

@Composable
fun TeacherStatCard(
    title: String,
    count: String,
    icon: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MidnightBackground.copy(alpha = 0.6f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(icon, fontSize = tanweerTextSize(20f))
            Text(
                text = count,
                fontSize = tanweerTextSize(18f),
                fontWeight = FontWeight.Black,
                color = color
            )
            Text(
                text = title,
                fontSize = tanweerTextSize(10f),
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TeacherGuidelineItem(
    icon: String,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(icon, fontSize = tanweerTextSize(16f), modifier = Modifier.padding(top = 2.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = title, fontSize = tanweerTextSize(13f), fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = desc, fontSize = tanweerTextSize(11f), color = TextSecondary, lineHeight = tanweerLineHeight(15f))
        }
    }
}

@Composable
fun OfficialHomeworkCard(hw: HomeworkItem) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MidnightSurface,
        borderColor = CyanAccent.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(hw.subjectIcon, fontSize = tanweerTextSize(18f))
                    Text(
                        text = hw.subjectName.ifBlank { "واجب مدرسي" },
                        fontSize = tanweerTextSize(14f),
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                }
                GlassPill(
                    text = "واجب رسمي معتمد 🎓",
                    color = EmeraldGreen,
                    bgColor = EmeraldGreen.copy(alpha = 0.15f)
                )
            }

            Text(
                text = hw.title,
                fontSize = tanweerTextSize(13f),
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            if (!hw.details.isNullOrBlank()) {
                Text(
                    text = hw.details,
                    fontSize = tanweerTextSize(11f),
                    color = TextSecondary,
                    lineHeight = tanweerLineHeight(15f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "📅 التسليم: ${hw.dueDate}",
                    fontSize = tanweerTextSize(10f),
                    color = WarmAmber
                )
                if (!hw.pageNumbers.isNullOrBlank()) {
                    Text(
                        text = "📖 ص: ${hw.pageNumbers}",
                        fontSize = tanweerTextSize(10f),
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
fun OfficialExamCard(exam: ExamItem) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MidnightSurface,
        borderColor = WarmAmber.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(exam.subjectIcon, fontSize = tanweerTextSize(18f))
                    Text(
                        text = exam.subjectName.ifBlank { "اختبار رسمي" },
                        fontSize = tanweerTextSize(14f),
                        fontWeight = FontWeight.Bold,
                        color = WarmAmber
                    )
                }
                GlassPill(
                    text = "اختبار رسمي معتمد 🎓",
                    color = EmeraldGreen,
                    bgColor = EmeraldGreen.copy(alpha = 0.15f)
                )
            }

            Text(
                text = exam.title,
                fontSize = tanweerTextSize(13f),
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            if (!exam.requiredChapters.isNullOrBlank()) {
                Text(
                    text = "📚 الفصول المطلوبة: ${exam.requiredChapters}",
                    fontSize = tanweerTextSize(11f),
                    color = TextSecondary,
                    lineHeight = tanweerLineHeight(15f)
                )
            }

            Text(
                text = "📅 الموعد: ${exam.examDate}",
                fontSize = tanweerTextSize(10f),
                color = CyanAccent
            )
        }
    }
}

@Composable
fun CreateOfficialHomeworkSheet(
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
    viewModel: TanweerViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val gradeSubjects = remember(currentUser?.gradeId) {
        com.magd.tanweer.data.model.SchoolHierarchy.getSubjectsForGrade(currentUser?.gradeId ?: 11)
    }

    AddHomeworkDialog(
        subjects = gradeSubjects,
        initialDueDate = TanweerViewModel.getTodayDateString(),
        isOfficial = true,
        onDismiss = onDismiss,
        onAddWithPages = { subjectId, title, details, pages, questions, dueDate, taskType, scanPages, finishSubmission ->
            viewModel.addHomework(
                subjectId = subjectId,
                title = title,
                details = details,
                pageNumbers = pages,
                questionNumbers = questions,
                dueDate = dueDate,
                taskType = taskType,
                pages = scanPages.mapNotNull { it.processedResult },
                isOfficial = true,
                onResult = { result ->
                    finishSubmission()
                    if (result.isSuccess) {
                        android.widget.Toast.makeText(
                            context,
                            "تم نشر الواجب الرسمي مع مرفقاته بنجاح 🎓",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        onCreated()
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            result.exceptionOrNull()?.message ?: "تعذر نشر الواجب الرسمي",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateOfficialExamSheet(
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
    viewModel: TanweerViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val gradeSubjects = remember(currentUser?.gradeId) {
        com.magd.tanweer.data.model.SchoolHierarchy.getSubjectsForGrade(currentUser?.gradeId ?: 11)
    }

    var selectedSubjectId by remember { mutableStateOf(gradeSubjects.firstOrNull()?.id ?: "math") }
    var title by remember { mutableStateOf("") }
    var requiredChapters by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val todayStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) }
    var examDate by remember { mutableStateOf(todayStr) }
    var examType by remember { mutableStateOf("MONTHLY") }
    var isSubmitting by remember { mutableStateOf(false) }

    val examTypes = listOf(
        Pair("MONTHLY", "اختبار شهري 📑"),
        Pair("ORAL", "اختبار شفهي 🗣️"),
        Pair("MIDTERM", "اختبار نصفي 🏛️"),
        Pair("FINAL", "اختبار نهائي 🎓")
    )

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        android.widget.Toast.makeText(context, "يرجى كتابة عنوان الاختبار الرسمي", android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isSubmitting = true
                    viewModel.addExam(
                        subjectId = selectedSubjectId,
                        title = title.trim(),
                        requiredChapters = requiredChapters.trim().ifEmpty { null },
                        notes = notes.trim().ifEmpty { null },
                        examDate = examDate,
                        examType = examType,
                        isOfficial = true,
                        onResult = { res ->
                            isSubmitting = false
                            if (res.isSuccess) {
                                android.widget.Toast.makeText(context, "تم إدراج الاختبار الرسمي بنجاح 📑", android.widget.Toast.LENGTH_LONG).show()
                                onCreated()
                            } else {
                                android.widget.Toast.makeText(context, res.exceptionOrNull()?.message ?: "تعذر حفظ الاختبار", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = WarmAmber),
                enabled = !isSubmitting
            ) {
                Text(if (isSubmitting) "جاري الإدراج..." else "إدراج كاختبار رسمي 📑", color = TextOnAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("إلغاء", color = TextSecondary)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📑", fontSize = tanweerTextSize(22f))
                Column {
                    Text("إدراج اختبار رسمي معتمد", fontSize = tanweerTextSize(16f), fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("تثبيت الاختبار في جدول التقييمات للشعبة", fontSize = tanweerTextSize(11f), color = WarmAmber)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Subject selector
                Text("اختر المادة:", fontSize = tanweerTextSize(12f), color = TextMuted)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    gradeSubjects.forEach { subj ->
                        val isSelected = subj.id == selectedSubjectId
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) WarmAmber else GlassSurface)
                                .clickable { selectedSubjectId = subj.id }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${subj.icon} ${subj.name}",
                                fontSize = tanweerTextSize(12f),
                                color = if (isSelected) TextOnAccent else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Exam Type Selector
                Text("نوع الاختبار:", fontSize = tanweerTextSize(12f), color = TextMuted)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    examTypes.forEach { (typeKey, typeLabel) ->
                        val isSelected = examType == typeKey
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) RubyRed else GlassSurface)
                                .clickable { examType = typeKey }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = typeLabel,
                                fontSize = tanweerTextSize(11f),
                                color = if (isSelected) TextOnAccent else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان الاختبار الرسمي") },
                    placeholder = { Text("مثال: الاختبار الشهري الأول") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = examDate,
                    onValueChange = { examDate = it },
                    label = { Text("تاريخ الاختبار (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = requiredChapters,
                    onValueChange = { requiredChapters = it },
                    label = { Text("المنهج والفصول المطلوبة") },
                    placeholder = { Text("مثال: الباب الأول كاملاً ص 1-35") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("تعليمات وملاحظات للأستاذ") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        containerColor = MidnightSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

