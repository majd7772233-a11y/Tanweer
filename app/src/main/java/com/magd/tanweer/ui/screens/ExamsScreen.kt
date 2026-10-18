package com.magd.tanweer.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.ExamItem
import com.magd.tanweer.data.model.SubjectItem
import com.magd.tanweer.data.notifications.TanweerNotificationManager
import com.magd.tanweer.ui.NavigationTab
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    viewModel: TanweerViewModel
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId ?: "" }

    val exams by viewModel.repository.getExams(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val gradeSubjects = remember(currentUser?.gradeId) {
        viewModel.getScheduleSubjectsForCurrentGrade()
    }

    val isAddExamModalOpenFromVm by viewModel.isAddExamModalOpen.collectAsStateWithLifecycle()
    val initialExamDateFromVm by viewModel.initialExamDate.collectAsStateWithLifecycle()
    var isLocalAddExamModalOpen by remember { mutableStateOf(false) }
    val isAddExamModalOpen = isLocalAddExamModalOpen || isAddExamModalOpenFromVm
    var selectedExamForDetail by remember { mutableStateOf<ExamItem?>(null) }
    var examToDelete by remember { mutableStateOf<ExamItem?>(null) }

    val todayDateStr = remember { TanweerViewModel.getTodayDateString() }

    // Grouping by Month (Point 17: Monthly structure & organization)
    val examsByMonth = remember(exams) {
        exams.groupBy { getMonthKey(it.examDate) }.toSortedMap()
    }
    val allMonthKeys = remember(examsByMonth) { examsByMonth.keys.toList() }
    var selectedMonthFilter by remember { mutableStateOf<String?>(null) } // null = "جميع الأشهر"

    var pendingExamForReminder by remember { mutableStateOf<ExamItem?>(null) }
    var showExactAlarmDialog by remember { mutableStateOf(false) }
    var showNotificationSettingsDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingExamForReminder?.let { exam ->
                val res = TanweerNotificationManager.scheduleExamReminder(
                    context = context,
                    examId = exam.id,
                    title = exam.title,
                    subjectName = exam.subjectName,
                    examDate = exam.examDate
                )
                when (res) {
                    TanweerNotificationManager.ScheduleReminderResult.SUCCESS -> {
                        Toast.makeText(context, "تم جدولة تذكير لاختبار ${exam.subjectName} بنجاح 🔔", Toast.LENGTH_SHORT).show()
                    }
                    TanweerNotificationManager.ScheduleReminderResult.PERMISSION_EXACT_ALARM_DENIED -> {
                        showExactAlarmDialog = true
                    }
                    TanweerNotificationManager.ScheduleReminderResult.PERMISSION_NOTIFICATIONS_DENIED -> {
                        showNotificationSettingsDialog = true
                    }
                    TanweerNotificationManager.ScheduleReminderResult.ALARM_SERVICE_UNAVAILABLE -> {
                        Toast.makeText(context, "خدمة المنبهات غير متاحة على هذا الجهاز حالياً", Toast.LENGTH_SHORT).show()
                    }
                    TanweerNotificationManager.ScheduleReminderResult.GENERAL_ERROR -> {
                        Toast.makeText(context, "تعذر جدولة التنبيه. يرجى التحقق من إعدادات النظام والبطارية.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            showNotificationSettingsDialog = true
        }
        pendingExamForReminder = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
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
                            text = "🧪 جدول الاختبارات المركزي",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = RubyRed
                        )
                        Text(
                            text = "${exams.size} اختبارات مسجلة موزعة على ${examsByMonth.size} أشهر",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    // Single Add Button
                    GlassButton(
                        text = "إضافة اختبار ＋",
                        color = RubyRed,
                        textColor = Color.White,
                        onClick = { isLocalAddExamModalOpen = true },
                        modifier = Modifier.height(40.dp)
                    )
                }
            }

            if (exams.isEmpty()) {
                item {
                    EmptyStateGlass(
                        title = "جدول الاختبارات فارغ",
                        subtitle = "يمكنك جدولة مواعيد الاختبارات الشهرية والنصفي والنهائي وتحديد الفصول المقررة",
                        icon = "📋",
                        actionButtonText = "إضافة أول اختبار",
                        onActionClick = { isLocalAddExamModalOpen = true }
                    )
                }
            } else {
                // Month Filter Selector Tabs (Point 17)
                if (allMonthKeys.size > 1) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // "All Months" Tab
                            val isAllSelected = selectedMonthFilter == null
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isAllSelected) RubyRed else MidnightSurface)
                                    .border(1.dp, if (isAllSelected) RubyRed else GlassBorderSubtle, RoundedCornerShape(12.dp))
                                    .clickable { selectedMonthFilter = null }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "جميع الأشهر (${exams.size})",
                                    fontSize = 12.sp,
                                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isAllSelected) Color.White else TextSecondary
                                )
                            }

                            // Each Month Tab
                            allMonthKeys.forEach { monthKey ->
                                val monthExams = examsByMonth[monthKey] ?: emptyList()
                                val isSelected = selectedMonthFilter == monthKey
                                val monthTitle = getShortMonthTitle(monthKey)

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) RubyRed else MidnightSurface)
                                        .border(1.dp, if (isSelected) RubyRed else GlassBorderSubtle, RoundedCornerShape(12.dp))
                                        .clickable { selectedMonthFilter = monthKey }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "$monthTitle (${monthExams.size})",
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Render Monthly Sections (Point 17)
                val displayedMonths = if (selectedMonthFilter != null) {
                    examsByMonth.filterKeys { it == selectedMonthFilter }
                } else {
                    examsByMonth
                }

                displayedMonths.forEach { (monthKey, monthExams) ->
                    val completedCount = monthExams.count { it.isCompleted || isExamDatePassed(it.examDate, todayDateStr) }
                    val fullMonthTitle = getArabicMonthTitle(monthKey)

                    // Month Header Section Card
                    item(key = "header_$monthKey") {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = MidnightSurface,
                            borderColor = GlassBorderSubtle
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(RubyRed.copy(alpha = 0.15f))
                                            .border(1.dp, RubyRed.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "📅", fontSize = 16.sp)
                                    }
                                    Column {
                                        Text(
                                            text = fullMonthTitle,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "تمت مراجعة $completedCount من ${monthExams.size} اختبارات",
                                            fontSize = 11.sp,
                                            color = if (completedCount == monthExams.size) EmeraldGreen else TextSecondary
                                        )
                                    }
                                }

                                GlassPill(
                                    text = "${monthExams.size} اختبارات",
                                    color = RubyRed,
                                    bgColor = RubyRed.copy(alpha = 0.15f)
                                )
                            }
                        }
                    }

                    // Structured Table for this Month
                    item(key = "table_$monthKey") {
                        CanvasExamsTableBoard(
                            exams = monthExams,
                            todayDateStr = todayDateStr,
                            onToggleDone = { examId, currentDone ->
                                viewModel.toggleExamCompletion(examId, !currentDone)
                            },
                            onRowClick = { exam ->
                                selectedExamForDetail = exam
                            },
                            onDeleteClick = { exam ->
                                examToDelete = exam
                            }
                        )
                    }
                }
            }
        }

        // Add Exam Sheet (Point 18: Passes examType properly)
        if (isAddExamModalOpen) {
            AddExamSheet(
                gradeSubjects = gradeSubjects,
                initialExamDate = initialExamDateFromVm,
                onDismiss = {
                    isLocalAddExamModalOpen = false
                    viewModel.closeAddExamDialog()
                },
                onAdd = { subjId, title, chapters, notes, date, examType ->
                    viewModel.addExam(
                        subjectId = subjId,
                        title = title,
                        requiredChapters = chapters,
                        notes = notes,
                        examDate = date,
                        examType = examType,
                        onResult = { res ->
                            res.fold(
                                onSuccess = { addRes ->
                                    Toast.makeText(context, addRes.message, Toast.LENGTH_SHORT).show()
                                    isLocalAddExamModalOpen = false
                                    viewModel.closeAddExamDialog()
                                },
                                onFailure = { error ->
                                    val err = error.message ?: "حدث خطأ أثناء حفظ الاختبار"
                                    Toast.makeText(context, "تعذر حفظ الاختبار: $err", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    )
                }
            )
        }

        // Exam Detail Modal (Point 17 & 21: Full details, completion toggle, and reminder scheduling)
        selectedExamForDetail?.let { exam ->
            ExamDetailModal(
                exam = exam,
                todayDateStr = todayDateStr,
                onDismiss = { selectedExamForDetail = null },
                onToggleDone = {
                    viewModel.toggleExamCompletion(exam.id, !exam.isCompleted)
                    selectedExamForDetail = exam.copy(isCompleted = !exam.isCompleted)
                },
                onScheduleReminder = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!hasPerm) {
                            pendingExamForReminder = exam
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            return@ExamDetailModal
                        }
                    }
                    val res = TanweerNotificationManager.scheduleExamReminder(
                        context = context,
                        examId = exam.id,
                        title = exam.title,
                        subjectName = exam.subjectName,
                        examDate = exam.examDate
                    )
                    when (res) {
                        TanweerNotificationManager.ScheduleReminderResult.SUCCESS -> {
                            Toast.makeText(context, "تم ضبط تذكير لاختبار ${exam.subjectName} بنجاح 🔔", Toast.LENGTH_SHORT).show()
                        }
                        TanweerNotificationManager.ScheduleReminderResult.PERMISSION_EXACT_ALARM_DENIED -> {
                            showExactAlarmDialog = true
                        }
                        TanweerNotificationManager.ScheduleReminderResult.PERMISSION_NOTIFICATIONS_DENIED -> {
                            showNotificationSettingsDialog = true
                        }
                        TanweerNotificationManager.ScheduleReminderResult.ALARM_SERVICE_UNAVAILABLE -> {
                            Toast.makeText(context, "خدمة المنبهات والتذكيرات غير متوفرة على هذا الجهاز حالياً", Toast.LENGTH_SHORT).show()
                        }
                        TanweerNotificationManager.ScheduleReminderResult.GENERAL_ERROR -> {
                            Toast.makeText(context, "تعذر جدولة التذكير. تأكد من إعدادات النظام والبطارية في جهازك.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onOpenLibraryBook = {
                    selectedExamForDetail = null
                    viewModel.setTab(NavigationTab.MORE)
                    viewModel.setSubScreen(SubScreen.LIBRARY)
                }
            )
        }

        // Delete Confirm Dialog
        examToDelete?.let { exam ->
            AlertDialog(
                onDismissRequest = { examToDelete = null },
                title = { Text(text = "حذف الاختبار", color = RubyRed, fontWeight = FontWeight.Bold) },
                text = { Text(text = "هل تريد حذف اختبار ${exam.subjectName} (${exam.title}) من الجدول؟", color = TextPrimary) },
                confirmButton = {
                    GlassButton(
                        text = "حذف",
                        color = RubyRed,
                        textColor = Color.White,
                        onClick = {
                            viewModel.deleteExam(exam.id) { res ->
                                if (res.isSuccess) {
                                    val deleteResult = res.getOrNull()
                                    val msg = deleteResult?.message ?: "تم حذف الاختبار من الجدول بنجاح"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, res.exceptionOrNull()?.message ?: "❌ تعذر حذف نسخة السيرفر", Toast.LENGTH_SHORT).show()
                                }
                            }
                            examToDelete = null
                        }
                    )
                },
                dismissButton = {
                    GlassOutlinedButton(text = "إلغاء", onClick = { examToDelete = null })
                },
                containerColor = MidnightSurface,
                shape = RoundedCornerShape(18.dp)
            )
        }

        // Exact Alarm Permission Recovery Dialog
        if (showExactAlarmDialog) {
            AlertDialog(
                onDismissRequest = { showExactAlarmDialog = false },
                title = { Text("تفعيل التنبيهات الدقيقة ⏰", color = CyanAccent, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "يتطلب إرسال تذكير الاختبار في موعده الدقيق السماح للتطبيق بجدولة التنبيهات والمنبهات (Alarms & Reminders) من إعدادات الهاتف.",
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    GlassButton(
                        text = "فتح إعدادات النظام ⚙️",
                        color = CyanAccent,
                        onClick = {
                            showExactAlarmDialog = false
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val intent = android.content.Intent(
                                        android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } else {
                                    val intent = android.content.Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            } catch (_: Exception) {
                                try {
                                    val intent = android.content.Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "يرجى الانتقال لإعدادات الهاتف وتفعيل المنبهات الدقيقة", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                },
                dismissButton = {
                    GlassOutlinedButton(text = "إلغاء", onClick = { showExactAlarmDialog = false })
                },
                containerColor = MidnightSurface,
                shape = RoundedCornerShape(18.dp)
            )
        }

        // Notifications Permission Recovery Dialog
        if (showNotificationSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showNotificationSettingsDialog = false },
                title = { Text("إذن التنبيهات والإشعارات 🔔", color = WarmAmber, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "لتذكيرك بموعد الاختبار والمراجعة، يحتاج تنوير إلى تفعيل إذن الإشعارات من إعدادات الهاتف.",
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    GlassButton(
                        text = "إعدادات الإشعارات ⚙️",
                        color = WarmAmber,
                        onClick = {
                            showNotificationSettingsDialog = false
                            try {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                ).apply {
                                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val intent = android.content.Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "يرجى تفعيل الإشعارات من إعدادات التطبيق", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                },
                dismissButton = {
                    GlassOutlinedButton(text = "إلغاء", onClick = { showNotificationSettingsDialog = false })
                },
                containerColor = MidnightSurface,
                shape = RoundedCornerShape(18.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// CANVAS EXAMS TABLE BOARD (WITH EXAM TYPE & STATUS)
// -------------------------------------------------------------
@Composable
fun CanvasExamsTableBoard(
    exams: List<ExamItem>,
    todayDateStr: String,
    onToggleDone: (examId: String, currentDone: Boolean) -> Unit,
    onRowClick: (ExamItem) -> Unit,
    onDeleteClick: (ExamItem) -> Unit
) {
    val horizontalScroll = rememberScrollState()

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MidnightSurface,
        borderColor = GlassBorderSubtle
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalScroll)
                .padding(4.dp)
        ) {
            // Table Top Column Headers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x2200E5FF))
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "#", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanAccent, modifier = Modifier.width(32.dp))
                Text(text = "المادة والنوع", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanAccent, modifier = Modifier.width(145.dp))
                Text(text = "اليوم والتاريخ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanAccent, modifier = Modifier.width(130.dp))
                Text(text = "الأستاذ / القاعة", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanAccent, modifier = Modifier.width(120.dp))
                Text(text = "الفصول المقررة", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanAccent, modifier = Modifier.width(140.dp))
                Text(text = "المراجعة", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanAccent, textAlign = TextAlign.Center, modifier = Modifier.width(85.dp))
                Text(text = "حذف", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanAccent, textAlign = TextAlign.Center, modifier = Modifier.width(44.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Table Rows
            exams.forEachIndexed { index, exam ->
                val isDone = exam.isCompleted || isExamDatePassed(exam.examDate, todayDateStr)
                val dayName = remember(exam.examDate) { getArabicDayOfWeek(exam.examDate) }
                val teacherOrHall = remember(exam.notes) {
                    if (!exam.notes.isNullOrBlank()) exam.notes.take(18) else "أستاذ المادة"
                }
                val chapters = remember(exam.requiredChapters) {
                    if (!exam.requiredChapters.isNullOrBlank()) exam.requiredChapters.take(22) else "المقرر كامل"
                }

                val rowColor = if (isDone) Color(0x1500E676) else if (index % 2 == 0) GlassSurface else GlassSurfaceLight

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(rowColor)
                        .border(0.5.dp, if (isDone) EmeraldGreen.copy(alpha = 0.4f) else GlassBorderSubtle, RoundedCornerShape(10.dp))
                        .clickable { onRowClick(exam) }
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // # Order
                        Text(
                            text = "${index + 1}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDone) EmeraldGreen else TextSecondary,
                            modifier = Modifier.width(32.dp)
                        )

                        // Subject & Type Badge (Point 18)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.width(145.dp)
                        ) {
                            Text(text = exam.subjectIcon, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = exam.subjectName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDone) EmeraldGreen else TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    // Exam type pill
                                    ExamTypePill(exam.examType)
                                }
                                Text(
                                    text = exam.title,
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Day & Date
                        Column(modifier = Modifier.width(130.dp)) {
                            Text(
                                text = dayName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDone) TextSecondary else WarmAmber
                            )
                            Text(
                                text = exam.examDate,
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }

                        // Teacher / Hall
                        Text(
                            text = teacherOrHall,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(120.dp)
                        )

                        // Required Chapters
                        Text(
                            text = chapters,
                            fontSize = 11.sp,
                            color = CyanAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(140.dp)
                        )

                        // Smart Status Box (Point 19: real toggle)
                        Box(
                            modifier = Modifier.width(85.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CanvasSmartStatusBadge(
                                isDone = isDone,
                                onClick = { onToggleDone(exam.id, isDone) }
                            )
                        }

                        // Delete Action
                        IconButton(
                            onClick = { onDeleteClick(exam) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "حذف",
                                tint = RubyRed.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExamTypePill(examType: String) {
    val (label, color) = when (examType) {
        "QUIZ" -> "كويز ⚡" to WarmAmber
        "MIDTERM" -> "نصفي 📑" to EmeraldGreen
        "FINAL" -> "نهائي 🏆" to Color(0xFFFFB74D)
        else -> "شهري 📝" to RubyRed
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(text = label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// -------------------------------------------------------------
// CANVAS SMART STATUS BADGE
// -------------------------------------------------------------
@Composable
fun CanvasSmartStatusBadge(
    isDone: Boolean,
    onClick: () -> Unit
) {
    val checkProgress by animateFloatAsState(
        targetValue = if (isDone) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "examBadgeCheck"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier.size(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    if (isDone) {
                        drawRoundRect(
                            color = EmeraldGreen.copy(alpha = 0.2f),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                        drawRoundRect(
                            color = EmeraldGreen,
                            size = Size(w, h),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                            style = Stroke(width = 1.4f.dp.toPx())
                        )
                        if (checkProgress > 0.05f) {
                            val path = Path().apply {
                                val startX = w * 0.2f
                                val startY = h * 0.52f
                                val midX = w * 0.44f
                                val midY = h * 0.74f
                                val endX = w * 0.82f
                                val endY = h * 0.26f
                                moveTo(startX, startY)
                                if (checkProgress <= 0.5f) {
                                    val prog = checkProgress / 0.5f
                                    lineTo(startX + (midX - startX) * prog, startY + (midY - startY) * prog)
                                } else {
                                    lineTo(midX, midY)
                                    val prog = (checkProgress - 0.5f) / 0.5f
                                    lineTo(midX + (endX - midX) * prog, midY + (endY - midY) * prog)
                                }
                            }
                            drawPath(
                                path = path,
                                color = EmeraldGreen,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    } else {
                        drawRoundRect(
                            color = RubyRed.copy(alpha = 0.15f),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                            style = Stroke(width = 1.2f.dp.toPx())
                        )
                    }
                }
            }

            Text(
                text = if (isDone) "مكتمل ✓" else "قادم ⏳",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDone) EmeraldGreen else RubyRed,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

// -------------------------------------------------------------
// ADD EXAM SHEET (POINT 18: WITH EXAM TYPE SUPPORT)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExamSheet(
    gradeSubjects: List<SubjectItem>,
    initialExamDate: String? = null,
    onDismiss: () -> Unit,
    onAdd: (subjectId: String, title: String, chapters: String?, notes: String?, date: String, examType: String) -> Unit
) {
    var selectedSubj by remember { mutableStateOf(gradeSubjects.firstOrNull()) }
    var title by remember { mutableStateOf("") }
    var examType by remember { mutableStateOf("MONTHLY") } // MONTHLY, QUIZ, MIDTERM, FINAL
    var chapters by remember { mutableStateOf("") }
    var teacherNotes by remember { mutableStateOf("") }
    var examDate by remember(initialExamDate) { mutableStateOf(initialExamDate ?: TanweerViewModel.getTodayDateString()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MidnightSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = RubyRed) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "جدولة وتثبيت اختبار رسمي 🧪",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = RubyRed
                )
                GlassPill(
                    text = when (examType) {
                        "QUIZ" -> "تسميع / كويز ⚡"
                        "MIDTERM" -> "اختبار نصفي 📑"
                        "FINAL" -> "اختبار نهائي 🏆"
                        else -> "اختبار شهري 📝"
                    },
                    color = RubyRed,
                    bgColor = RubyRed.copy(alpha = 0.15f)
                )
            }

            // Exam Type Selection Chips (Point 18)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "MONTHLY" to "شهري 📝",
                    "QUIZ" to "كويز ⚡",
                    "MIDTERM" to "نصفي 📑",
                    "FINAL" to "نهائي 🏆"
                ).forEach { (key, label) ->
                    val isSelected = examType == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) RubyRed else GlassSurface)
                            .clickable { examType = key }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else TextSecondary
                        )
                    }
                }
            }

            // Subject Selector Horizontal Row
            Column {
                Text("اختر المادة:", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    gradeSubjects.forEach { subj ->
                        val isSelected = selectedSubj?.id == subj.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0x33FF3366) else GlassSurface)
                                .border(1.dp, if (isSelected) RubyRed else GlassBorderSubtle, RoundedCornerShape(12.dp))
                                .clickable { selectedSubj = subj }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = subj.icon, fontSize = 16.sp)
                                Text(
                                    text = subj.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) RubyRed else TextPrimary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            GlassTextField(
                value = title,
                onValueChange = { title = it },
                label = "عنوان الاختبار",
                placeholder = "مثال: اختبار الشهر الأول / نصفي"
            )

            GlassTextField(
                value = examDate,
                onValueChange = { examDate = it },
                label = "تاريخ الاختبار (YYYY-MM-DD)",
                placeholder = "2026-09-30"
            )

            GlassTextField(
                value = chapters,
                onValueChange = { chapters = it },
                label = "الفصول المقررة",
                placeholder = "مثال: الوحدة الأولى والثانية"
            )

            GlassTextField(
                value = teacherNotes,
                onValueChange = { teacherNotes = it },
                label = "الأستاذ / القاعة",
                placeholder = "مثال: أ. محمد • القاعة 2"
            )

            GlassButton(
                text = "تثبيت الاختبار في الجدول",
                color = RubyRed,
                textColor = Color.White,
                enabled = title.isNotBlank() && selectedSubj != null,
                onClick = {
                    selectedSubj?.let { s ->
                        onAdd(s.id, title.trim(), chapters.trim().ifEmpty { null }, teacherNotes.trim().ifEmpty { null }, examDate.trim(), examType)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// -------------------------------------------------------------
// EXAM DETAIL MODAL (POINTS 17, 18, 19, 21)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamDetailModal(
    exam: ExamItem,
    todayDateStr: String,
    onDismiss: () -> Unit,
    onToggleDone: () -> Unit,
    onScheduleReminder: () -> Unit,
    onOpenLibraryBook: () -> Unit
) {
    val isDone = exam.isCompleted || isExamDatePassed(exam.examDate, todayDateStr)
    val dayName = remember(exam.examDate) { getArabicDayOfWeek(exam.examDate) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MidnightSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = RubyRed) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = exam.subjectIcon, fontSize = 26.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = "اختبار ${exam.subjectName}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                            ExamTypePill(exam.examType)
                        }
                        Text(text = exam.title, fontSize = 13.sp, color = TextSecondary)
                    }
                }

                GlassPill(
                    text = if (isDone) "مكتمل ✓" else "قادم ⏳",
                    color = if (isDone) EmeraldGreen else RubyRed,
                    bgColor = if (isDone) EmeraldGreen.copy(alpha = 0.15f) else RubyRed.copy(alpha = 0.15f)
                )
            }

            HorizontalDivider(color = GlassBorderSubtle)

            // Date and Day Row
            GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "موعد الاختبار:", fontSize = 11.sp, color = TextSecondary)
                        Text(text = "$dayName • ${exam.examDate}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WarmAmber)
                    }

                    // Toggle review completion button (Point 19)
                    GlassButton(
                        text = if (exam.isCompleted) "إلغاء المراجعة ↩️" else "تمت المراجعة ✓",
                        color = if (exam.isCompleted) EmeraldGreen else CyanAccent,
                        textColor = Color.White,
                        onClick = onToggleDone,
                        modifier = Modifier.height(34.dp)
                    )
                }
            }

            if (!exam.requiredChapters.isNullOrBlank()) {
                GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "الفصول والموضوعات المقررة:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                        Text(text = exam.requiredChapters, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            if (!exam.notes.isNullOrBlank()) {
                GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "الأستاذ والقاعة المخصصة:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WarmAmber)
                        Text(text = exam.notes, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            // Actions Row: Set Reminder (Point 21) & Open Book
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassButton(
                    text = "تذكير بالموعد 🔔",
                    color = WarmAmber,
                    textColor = Color.White,
                    onClick = onScheduleReminder,
                    modifier = Modifier.weight(1f).height(46.dp)
                )

                GlassOutlinedButton(
                    text = "كتاب المادة 📖",
                    onClick = onOpenLibraryBook,
                    modifier = Modifier.weight(1f).height(46.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// -------------------------------------------------------------
// HELPER FUNCTIONS (POINTS 17 & 20)
// -------------------------------------------------------------
fun getMonthKey(dateStr: String): String {
    return if (dateStr.length >= 7) dateStr.substring(0, 7) else "OTHER"
}

fun getArabicMonthTitle(monthKey: String): String {
    return try {
        val parts = monthKey.split("-")
        if (parts.size >= 2) {
            val year = parts[0]
            val month = parts[1].toInt()
            val monthName = when (month) {
                1 -> "يناير / كانون الثاني"
                2 -> "فبراير / شباط"
                3 -> "مارس / آذار"
                4 -> "أبريل / نيسان"
                5 -> "مايو / أيار"
                6 -> "يونيو / حزيران"
                7 -> "يوليو / تموز"
                8 -> "أغسطس / آب"
                9 -> "سبتمبر / أيلول"
                10 -> "أكتوبر / تشرين الأول"
                11 -> "نوفمبر / تشرين الثاني"
                12 -> "ديسمبر / كانون الأول"
                else -> "شهر $month"
            }
            "شهر $monthName $year"
        } else {
            monthKey
        }
    } catch (_: Exception) {
        monthKey
    }
}

fun getShortMonthTitle(monthKey: String): String {
    return try {
        val parts = monthKey.split("-")
        if (parts.size >= 2) {
            val month = parts[1].toInt()
            when (month) {
                1 -> "يناير"
                2 -> "فبراير"
                3 -> "مارس"
                4 -> "أبريل"
                5 -> "مايو"
                6 -> "يونيو"
                7 -> "يوليو"
                8 -> "أغسطس"
                9 -> "سبتمبر"
                10 -> "أكتوبر"
                11 -> "نوفمبر"
                12 -> "ديسمبر"
                else -> "شهر $month"
            }
        } else {
            monthKey
        }
    } catch (_: Exception) {
        monthKey
    }
}

fun isExamDatePassed(examDateStr: String, todayDateStr: String): Boolean {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val examDate = sdf.parse(examDateStr)
        val today = sdf.parse(todayDateStr)
        if (examDate != null && today != null) {
            examDate.before(today)
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }
}

fun getArabicDayOfWeek(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val d = sdf.parse(dateStr) ?: return ""
        val cal = Calendar.getInstance().apply { time = d }
        when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "الأحد"
            Calendar.MONDAY -> "الإثنين"
            Calendar.TUESDAY -> "الثلاثاء"
            Calendar.WEDNESDAY -> "الأربعاء"
            Calendar.THURSDAY -> "الخميس"
            Calendar.FRIDAY -> "الجمعة"
            Calendar.SATURDAY -> "السبت"
            else -> ""
        }
    } catch (_: Exception) {
        ""
    }
}
