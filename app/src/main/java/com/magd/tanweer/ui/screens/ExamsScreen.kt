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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.R
import com.magd.tanweer.data.model.ExamItem
import com.magd.tanweer.util.ApiDisplayValuePolicy
import com.magd.tanweer.data.model.Permission
import com.magd.tanweer.data.model.SubjectItem
import com.magd.tanweer.data.model.SyncState
import com.magd.tanweer.data.model.hasPermission
import com.magd.tanweer.data.notifications.TanweerNotificationManager
import com.magd.tanweer.ui.NavigationTab
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.GroupSyncSource
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*
import com.magd.tanweer.util.ExamReviewPolicy
import com.magd.tanweer.util.ExamBookNavigationPolicy
import com.magd.tanweer.util.ExamBookNavigationTarget
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
    val myGroups by viewModel.repository.getMyGroups().collectAsStateWithLifecycle(initialValue = emptyList())
    val activeGroupRole = remember(myGroups, activeGroupId) { myGroups.firstOrNull { it.id == activeGroupId }?.role }
    val groupSyncScopeIds by viewModel.groupSyncScopeIds.collectAsStateWithLifecycle()

    val exams by viewModel.repository.getExams(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val localPendingExams by viewModel.repository.getLocalPendingExams(activeGroupId).collectAsStateWithLifecycle(initialValue = emptyList())
    val failedExams by viewModel.repository.getFailedExams(activeGroupId).collectAsStateWithLifecycle(initialValue = emptyList())
    val examsSyncState by viewModel.examsSyncState.collectAsStateWithLifecycle()
    val scopedExamsSyncState = syncStateForScope(examsSyncState, groupSyncScopeIds[GroupSyncSource.EXAMS], activeGroupId)
    val hasExamSnapshot = exams.isNotEmpty() || hasAuthoritativeSnapshot(scopedExamsSyncState)
    val allBooks by viewModel.repository.getAllBooks().collectAsStateWithLifecycle(initialValue = emptyList())
    val librarySyncState by viewModel.librarySyncState.collectAsStateWithLifecycle()
    val librarySyncGradeId by viewModel.librarySyncGradeId.collectAsStateWithLifecycle()
    val examBookGradeId = currentUser?.gradeId ?: 10
    val hasAuthoritativeBookCatalog = librarySyncGradeId == examBookGradeId && librarySyncState is SyncState.Success

    val gradeSubjects = remember(currentUser?.gradeId) {
        viewModel.getScheduleSubjectsForCurrentGrade()
    }

    val isAddExamModalOpenFromVm by viewModel.isAddExamModalOpen.collectAsStateWithLifecycle()
    val initialExamDateFromVm by viewModel.initialExamDate.collectAsStateWithLifecycle()
    var isLocalAddExamModalOpen by remember { mutableStateOf(false) }
    val isAddExamModalOpen = isLocalAddExamModalOpen || isAddExamModalOpenFromVm
    var selectedExamForDetail by remember { mutableStateOf<ExamItem?>(null) }
    var examToDelete by remember { mutableStateOf<ExamItem?>(null) }
    var examToEdit by remember { mutableStateOf<ExamItem?>(null) }
    var isUpdatingExam by remember { mutableStateOf(false) }
    var failedExamToDiscard by remember { mutableStateOf<ExamItem?>(null) }

    val todayDateStr = remember { TanweerViewModel.getTodayDateString() }
    val canDeleteExams = remember(currentUser, activeGroupRole) {
        currentUser?.hasPermission(Permission.MODERATE_EXAM, groupRole = activeGroupRole) == true
    }

    var selectedTypeFilter by remember { mutableStateOf<String?>(null) }
    val filteredExams = remember(exams, selectedTypeFilter) {
        if (selectedTypeFilter != null) {
            exams.filter { it.examType == selectedTypeFilter }
        } else {
            exams
        }
    }

    // Grouping by Month (Point 17: Monthly structure & organization)
    // Oral tests are intentionally kept outside the monthly calendar.
    val oralExams = remember(filteredExams) {
        filteredExams.filter { it.examType == "ORAL" }.sortedByDescending { it.examDate }
    }
    val calendarExams = remember(filteredExams) {
        filteredExams.filter { it.examType != "ORAL" }
    }
    val examsByMonth = remember(calendarExams) {
        calendarExams.groupBy { getMonthKey(it.examDate) }.toSortedMap(compareByDescending { it })
    }
    val allMonthKeys = remember(examsByMonth) { examsByMonth.keys.toList() }
    var selectedMonthFilter by remember { mutableStateOf<String?>(null) } // null = "جميع الأشهر"
    var showMonthPicker by remember { mutableStateOf(false) }
    val monthPickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())

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
                    TanweerNotificationManager.ScheduleReminderResult.NOTIFICATIONS_DISABLED -> {
                        Toast.makeText(context, context.getString(R.string.notification_reminders_disabled), Toast.LENGTH_SHORT).show()
                    }
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
                            fontSize = tanweerTextSize(20f),
                            fontWeight = FontWeight.Black,
                            color = RubyRed
                        )
                        Text(
                            text = if (hasExamSnapshot) "${exams.size} اختبار مسجل • ${examsByMonth.size} شهر في التقويم • ${oralExams.size} شفهي مرن" else "جارٍ التحقق من جدول الاختبارات...",
                            fontSize = tanweerTextSize(12f),
                            color = TextSecondary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (selectedTypeFilter != "ORAL") {
                            IconButton(
                                onClick = { showMonthPicker = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(GlassSurface)
                                    .border(1.dp, GlassBorderSubtle, RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = "اختيار شهر جدول الاختبارات",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        GlassButton(
                            text = "إضافة اختبار ＋",
                            color = RubyRed,
                            textColor = Color.White,
                            onClick = { isLocalAddExamModalOpen = true },
                            modifier = Modifier.height(40.dp)
                        )
                    }
                }
            }

            if (localPendingExams.isNotEmpty()) {
                item(key = "local_pending_exams_header") {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MidnightSurface,
                        borderColor = WarmAmber.copy(alpha = 0.35f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.exam_local_pending_title, localPendingExams.size),
                                color = WarmAmber,
                                fontSize = tanweerTextSize(13f),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.exam_local_pending_description),
                                color = TextSecondary,
                                fontSize = tanweerTextSize(11f)
                            )
                        }
                    }
                }
                items(localPendingExams, key = { "local_pending_exam_${it.id}" }) { exam ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MidnightSurface.copy(alpha = 0.9f),
                        borderColor = WarmAmber.copy(alpha = 0.25f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = exam.title,
                                    color = TextPrimary,
                                    fontSize = tanweerTextSize(13f),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.exam_local_item_meta, exam.subjectName, exam.examDate),
                                    color = TextSecondary,
                                    fontSize = tanweerTextSize(11f)
                                )
                            }
                            Text(
                                text = stringResource(
                                    if (exam.syncStatus == "SYNCING") R.string.exam_local_status_syncing else R.string.exam_local_status_pending
                                ),
                                color = WarmAmber,
                                fontSize = tanweerTextSize(10f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (failedExams.isNotEmpty()) {
                item(key = "failed_local_exams_header") {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MidnightSurface,
                        borderColor = RubyRed.copy(alpha = 0.4f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.exam_failed_title, failedExams.size),
                                color = RubyRed,
                                fontSize = tanweerTextSize(13f),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.exam_failed_description),
                                color = TextSecondary,
                                fontSize = tanweerTextSize(11f)
                            )
                        }
                    }
                }
                items(failedExams, key = { "failed_local_exam_${it.id}" }) { exam ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MidnightSurface.copy(alpha = 0.95f),
                        borderColor = RubyRed.copy(alpha = 0.35f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = exam.title,
                                color = TextPrimary,
                                fontSize = tanweerTextSize(13f),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.exam_local_item_meta, exam.subjectName, exam.examDate),
                                color = TextSecondary,
                                fontSize = tanweerTextSize(11f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GlassOutlinedButton(
                                    text = stringResource(R.string.exam_failed_retry),
                                    onClick = {
                                        viewModel.retryFailedExam(exam.id) { result ->
                                            val message = if (result.isSuccess) {
                                                context.getString(R.string.exam_failed_retry_queued)
                                            } else {
                                                context.getString(
                                                    R.string.exam_failed_retry_error,
                                                    result.exceptionOrNull()?.message.orEmpty()
                                                )
                                            }
                                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(36.dp)
                                )
                                GlassButton(
                                    text = stringResource(R.string.exam_failed_discard),
                                    color = RubyRed,
                                    textColor = Color.White,
                                    onClick = { failedExamToDiscard = exam },
                                    modifier = Modifier.weight(1f).height(36.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (exams.isEmpty() && localPendingExams.isEmpty() && failedExams.isEmpty()) {
                item {
                    if (hasExamSnapshot) {
                        EmptyStateGlass(
                            title = "جدول الاختبارات فارغ",
                            subtitle = "يمكنك جدولة مواعيد الاختبارات الشهرية والنصفي والنهائي وتحديد الفصول المقررة",
                            icon = "📋",
                            actionButtonText = "إضافة أول اختبار",
                            onActionClick = { isLocalAddExamModalOpen = true }
                        )
                    } else {
                        PendingSyncEmptyState(dataLabel = "جدول الاختبارات", syncState = scopedExamsSyncState)
                    }
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
                                    text = "جميع الأشهر (${calendarExams.size})",
                                    fontSize = tanweerTextSize(12f),
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
                                        fontSize = tanweerTextSize(12f),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Exam Type Filter Tabs (شهري، شفهي، نصفي، نهائي)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            null to "جميع الأنواع",
                            "MONTHLY" to "شهري 📝",
                            "ORAL" to "شفهي 🗣️",
                            "MIDTERM" to "نصفي 📑",
                            "FINAL" to "نهائي 🏆"
                        ).forEach { (typeKey, typeLabel) ->
                            val isTypeSelected = selectedTypeFilter == typeKey
                            val typeCount = if (typeKey == null) exams.size else exams.count { it.examType == typeKey }
                            val displayLabel = "$typeLabel ($typeCount)"
                            val chipColor = when (typeKey) {
                                "ORAL" -> CyanAccent
                                "MIDTERM" -> EmeraldGreen
                                "FINAL" -> Color(0xFFFFB74D)
                                else -> RubyRed
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isTypeSelected) chipColor.copy(alpha = 0.25f) else MidnightSurface)
                                    .border(1.dp, if (isTypeSelected) chipColor else GlassBorderSubtle, RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedTypeFilter = typeKey
                                        if (typeKey == "ORAL") selectedMonthFilter = null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = displayLabel,
                                    fontSize = tanweerTextSize(11f),
                                    fontWeight = if (isTypeSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isTypeSelected) chipColor else TextSecondary
                                )
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

                if (selectedMonthFilter != null && examsByMonth[selectedMonthFilter].isNullOrEmpty()) {
                    item(key = "empty_month_${selectedMonthFilter}") {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = MidnightSurface,
                            borderColor = GlassBorderSubtle
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.EventBusy,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(30.dp)
                                )
                                Text(
                                    text = "لا توجد اختبارات مسجلة في ${getArabicMonthTitle(selectedMonthFilter.orEmpty())}",
                                    fontSize = tanweerTextSize(13f),
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "يمكنك الرجوع إلى شهر آخر من التقويم.",
                                    fontSize = tanweerTextSize(11f),
                                    color = TextSecondary
                                )
                                GlassOutlinedButton(
                                    text = "عرض جميع الأشهر",
                                    onClick = { selectedMonthFilter = null }
                                )
                            }
                        }
                    }
                }

                if (selectedTypeFilter == "ORAL" || selectedTypeFilter == null) {
                    if (oralExams.isNotEmpty()) {
                        item(key = "oral_exams_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "الاختبارات الشفوية المرنة",
                                        fontSize = tanweerTextSize(14f),
                                        fontWeight = FontWeight.Bold,
                                        color = CyanAccent
                                    )
                                    Text(
                                        text = "تُسجل عند الحاجة ولا تدخل في جدول الأشهر.",
                                        fontSize = tanweerTextSize(11f),
                                        color = TextSecondary
                                    )
                                }
                                GlassPill(
                                    text = "${oralExams.size} شفهي",
                                    color = CyanAccent,
                                    bgColor = CyanGlow
                                )
                            }
                        }

                        items(
                            items = oralExams,
                            key = { "oral_${it.id}" }
                        ) { oralExam ->
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedExamForDetail = oralExam },
                                backgroundColor = MidnightSurface,
                                borderColor = GlassBorderSubtle
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(CyanGlow),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🗣️", fontSize = tanweerTextSize(18f))
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = oralExam.subjectName,
                                            fontSize = tanweerTextSize(13f),
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = oralExam.title,
                                            fontSize = tanweerTextSize(12f),
                                            color = TextSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "مسجل في ${oralExam.examDate}",
                                            fontSize = tanweerTextSize(10f),
                                            color = TextMuted
                                        )
                                    }
                                    if (canDeleteExams) {
                                        IconButton(
                                            onClick = { examToDelete = oralExam },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "حذف الاختبار الشفهي",
                                                tint = RubyRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (selectedTypeFilter == "ORAL") {
                        item(key = "oral_exams_empty") {
                            EmptyStateGlass(
                                title = "لا توجد اختبارات شفوية مسجلة",
                                subtitle = "يمكن إضافة الاختبار الشفهي عند الحاجة دون ربطه بجدول شهري.",
                                icon = "🗣️",
                                actionButtonText = "إضافة اختبار شفهي",
                                onActionClick = { isLocalAddExamModalOpen = true }
                            )
                        }
                    }
                }

                displayedMonths.forEach { (monthKey, monthExams) ->
                    val reviewSummary = ExamReviewPolicy.summarize(monthExams)
                    val completedCount = reviewSummary.reviewedCount
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
                                        Text(text = "📅", fontSize = tanweerTextSize(16f))
                                    }
                                    Column {
                                        Text(
                                            text = fullMonthTitle,
                                            fontSize = tanweerTextSize(14f),
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = stringResource(R.string.exam_review_progress, completedCount, reviewSummary.totalCount),
                                            fontSize = tanweerTextSize(11f),
                                            color = if (reviewSummary.totalCount > 0 && completedCount == reviewSummary.totalCount) EmeraldGreen else TextSecondary
                                        )
                                    }
                                }

                                GlassPill(
                                    text = stringResource(R.string.exam_count_format, reviewSummary.totalCount),
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
                            canDelete = canDeleteExams,
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

        examToEdit?.let { exam ->
            AddExamSheet(
                gradeSubjects = gradeSubjects,
                initialExamDate = exam.examDate,
                onDismiss = {
                    examToEdit = null
                    isUpdatingExam = false
                },
                onAdd = { _, _, _, _, _, _ -> },
                initialExam = exam,
                isSaving = isUpdatingExam,
                onUpdate = { examId, subjectId, title, chapters, notes, date, examType ->
                    isUpdatingExam = true
                    viewModel.updateExam(
                        examId = examId,
                        subjectId = subjectId,
                        title = title,
                        requiredChapters = chapters,
                        notes = notes,
                        examDate = date,
                        examType = examType
                    ) { result ->
                        isUpdatingExam = false
                        result.fold(
                            onSuccess = {
                                Toast.makeText(context, context.getString(R.string.exam_edit_success), Toast.LENGTH_SHORT).show()
                                examToEdit = null
                            },
                            onFailure = { error ->
                                val message = error.message?.takeIf { it.isNotBlank() }
                                    ?: context.getString(R.string.exam_edit_failed)
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }
            )
        }
        // Exam Detail Modal (Point 17 & 21: Full details, completion toggle, and reminder scheduling)
        selectedExamForDetail?.let { exam ->
            val bookDestination = remember(exam, allBooks, examBookGradeId, hasAuthoritativeBookCatalog) {
                ExamBookNavigationPolicy.resolve(
                    examSubjectId = exam.subjectId,
                    gradeId = examBookGradeId,
                    books = allBooks,
                    hasAuthoritativeCatalog = hasAuthoritativeBookCatalog
                )
            }
            val bookActionLabel = stringResource(
                when (bookDestination) {
                    is ExamBookNavigationTarget.OpenBook -> R.string.exam_open_subject_book
                    is ExamBookNavigationTarget.OpenSubjectLibrary -> R.string.exam_view_subject_books
                    is ExamBookNavigationTarget.OpenGeneralLibrary -> R.string.exam_open_general_library
                }
            )
            ExamDetailModal(
                exam = exam,
                todayDateStr = todayDateStr,
                onDismiss = { selectedExamForDetail = null },
                canEdit = ExamOwnershipPolicy.canEdit(
                    createdBy = exam.createdBy,
                    syncStatus = exam.syncStatus,
                    currentUserId = currentUser?.id,
                    canModerate = canDeleteExams
                ),
                onEdit = {
                    examToEdit = exam
                    selectedExamForDetail = null
                },
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
                        TanweerNotificationManager.ScheduleReminderResult.NOTIFICATIONS_DISABLED -> {
                            Toast.makeText(context, context.getString(R.string.notification_reminders_disabled), Toast.LENGTH_SHORT).show()
                        }
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
                    when (val destination = bookDestination) {
                        is ExamBookNavigationTarget.OpenBook -> viewModel.openBookInPdfReader(destination.book)
                        is ExamBookNavigationTarget.OpenSubjectLibrary -> viewModel.openLibrary(
                            gradeId = destination.gradeId,
                            subjectId = destination.subjectId
                        )
                        is ExamBookNavigationTarget.OpenGeneralLibrary -> viewModel.openLibrary(
                            gradeId = destination.gradeId
                        )
                    }
                },
                bookActionLabel = bookActionLabel
            )
        }

        failedExamToDiscard?.let { exam ->
            AlertDialog(
                onDismissRequest = { failedExamToDiscard = null },
                title = { Text(stringResource(R.string.exam_failed_discard_title), color = RubyRed, fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.exam_failed_discard_message, exam.title), color = TextPrimary) },
                confirmButton = {
                    GlassButton(
                        text = stringResource(R.string.exam_failed_discard),
                        color = RubyRed,
                        textColor = Color.White,
                        onClick = {
                            viewModel.discardFailedExam(exam.id) { result ->
                                val message = if (result.isSuccess) {
                                    context.getString(R.string.exam_failed_discard_success)
                                } else {
                                    context.getString(
                                        R.string.exam_failed_discard_error,
                                        result.exceptionOrNull()?.message.orEmpty()
                                    )
                                }
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                            failedExamToDiscard = null
                        }
                    )
                },
                dismissButton = {
                    GlassOutlinedButton(
                        text = stringResource(R.string.exam_failed_cancel),
                        onClick = { failedExamToDiscard = null }
                    )
                },
                containerColor = MidnightSurface,
                shape = RoundedCornerShape(18.dp)
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

        if (showMonthPicker) {
            DatePickerDialog(
                onDismissRequest = { showMonthPicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            monthPickerState.selectedDateMillis?.let { selectedMillis ->
                                val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
                                    timeInMillis = selectedMillis
                                }
                                selectedMonthFilter = String.format(
                                    Locale.US,
                                    "%04d-%02d",
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH) + 1
                                )
                            }
                            showMonthPicker = false
                        }
                    ) {
                        Text("الانتقال إلى الشهر", color = CyanAccent)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMonthPicker = false }) {
                        Text("إلغاء", color = TextSecondary)
                    }
                }
            ) {
                DatePicker(
                    state = monthPickerState,
                    title = { Text("اختر شهراً من سجل الاختبارات", modifier = Modifier.padding(16.dp)) },
                    headline = { Text("يتم عرض جدول الشهر المختار", modifier = Modifier.padding(horizontal = 16.dp)) },
                    showModeToggle = false
                )
            }
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
                        fontSize = tanweerTextSize(13f)
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
                        fontSize = tanweerTextSize(13f)
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
    canDelete: Boolean = false,
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
                Text(text = "#", fontSize = tanweerTextSize(12f), fontWeight = FontWeight.Bold, color = CyanAccent, modifier = Modifier.width(32.dp))
                Text(text = "المادة والنوع", fontSize = tanweerTextSize(12f), fontWeight = FontWeight.Bold, color = CyanAccent, modifier = Modifier.width(145.dp))
                Text(text = "اليوم والتاريخ", fontSize = tanweerTextSize(12f), fontWeight = FontWeight.Bold, color = CyanAccent, modifier = Modifier.width(130.dp))
                Text(text = "الأستاذ / القاعة", fontSize = tanweerTextSize(12f), fontWeight = FontWeight.Bold, color = CyanAccent, modifier = Modifier.width(120.dp))
                Text(text = "الفصول المقررة", fontSize = tanweerTextSize(12f), fontWeight = FontWeight.Bold, color = CyanAccent, modifier = Modifier.width(140.dp))
                Text(text = "المراجعة", fontSize = tanweerTextSize(12f), fontWeight = FontWeight.Bold, color = CyanAccent, textAlign = TextAlign.Center, modifier = Modifier.width(85.dp))
                if (canDelete) {
                    Text(text = "حذف", fontSize = tanweerTextSize(12f), fontWeight = FontWeight.Bold, color = CyanAccent, textAlign = TextAlign.Center, modifier = Modifier.width(44.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Table Rows
            exams.forEachIndexed { index, exam ->
                val isReviewed = exam.isCompleted
                val isPassed = isExamDatePassed(exam.examDate, todayDateStr)
                val isToday = exam.examDate == todayDateStr
                val dayName = remember(exam.examDate) { getArabicDayOfWeek(exam.examDate) }
                val teacherOrHall = exam.notes?.takeIf { it.isNotBlank() } ?: "أستاذ المادة"
                val chapters = localizedChapterListLabel(exam.requiredChapters)

                val rowColor = when {
                    isReviewed -> Color(0x1800E676)
                    isToday -> Color(0x18FFD600)
                    isPassed -> Color(0x0CFFFFFF)
                    index % 2 == 0 -> GlassSurface
                    else -> GlassSurfaceLight
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(rowColor)
                        .border(
                            0.5.dp,
                            when {
                                isReviewed -> EmeraldGreen.copy(alpha = 0.5f)
                                isToday -> WarmAmber.copy(alpha = 0.5f)
                                else -> GlassBorderSubtle
                            },
                            RoundedCornerShape(10.dp)
                        )
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
                            fontSize = tanweerTextSize(12f),
                            fontWeight = FontWeight.Bold,
                            color = if (isReviewed) EmeraldGreen else TextSecondary,
                            modifier = Modifier.width(32.dp)
                        )

                        // Subject & Type Badge (Point 18)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.width(145.dp)
                        ) {
                            Text(text = exam.subjectIcon, fontSize = tanweerTextSize(16f))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = exam.subjectName,
                                        fontSize = tanweerTextSize(12f),
                                        fontWeight = FontWeight.Bold,
                                        color = if (isReviewed) EmeraldGreen else TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    // Exam type pill
                                    ExamTypePill(exam.examType)
                                }
                                Text(
                                    text = exam.title,
                                    fontSize = tanweerTextSize(10f),
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (exam.isOfficial) {
                                    Text(
                                        text = "اختبار رسمي • صادر من الأستاذ 🎓",
                                        fontSize = tanweerTextSize(8f),
                                        fontWeight = FontWeight.Bold,
                                        color = CyanAccent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (exam.syncStatus == "DELETE_PENDING") {
                                    Text(
                                        text = "طلب الحذف بانتظار الاتصال",
                                        fontSize = tanweerTextSize(8f),
                                        fontWeight = FontWeight.SemiBold,
                                        color = WarmAmber,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Day & Date with Schedule Status Indicator
                        Column(modifier = Modifier.width(130.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = dayName,
                                    fontSize = tanweerTextSize(12f),
                                    fontWeight = FontWeight.SemiBold,
                                    color = when {
                                        isToday -> WarmAmber
                                        isReviewed -> EmeraldGreen
                                        else -> TextPrimary
                                    }
                                )
                                val statusLabel = when {
                                    isToday -> "اليوم 📌"
                                    isPassed -> "منقضي ⌛"
                                    else -> "قادم ⏳"
                                }
                                val statusColor = when {
                                    isToday -> WarmAmber
                                    isPassed -> TextMuted
                                    else -> CyanAccent
                                }
                                Text(
                                    text = statusLabel,
                                    fontSize = tanweerTextSize(8f),
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                            }
                            Text(
                                text = exam.examDate,
                                fontSize = tanweerTextSize(10f),
                                color = TextMuted
                            )
                        }

                        // Teacher / Hall
                        Text(
                            text = teacherOrHall,
                            fontSize = tanweerTextSize(11f),
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(120.dp)
                        )

                        // Required Chapters
                        Text(
                            text = chapters,
                            fontSize = tanweerTextSize(11f),
                            color = CyanAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(140.dp)
                        )

                        // Smart Status Box (Personal Review Toggle)
                        Box(
                            modifier = Modifier.width(85.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (exam.syncStatus == "DELETE_PENDING") {
                                Text(
                                    text = "قيد الحذف",
                                    fontSize = tanweerTextSize(9f),
                                    fontWeight = FontWeight.Bold,
                                    color = WarmAmber
                                )
                            } else {
                                CanvasSmartStatusBadge(
                                    isDone = isReviewed,
                                    onClick = { onToggleDone(exam.id, isReviewed) }
                                )
                            }
                        }

                        // Delete Action (Only visible for teachers / admins)
                        if (canDelete) {
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
}

@Composable
fun ExamTypePill(examType: String) {
    val color = when (ApiDisplayValuePolicy.examTypeKey(examType)) {
        "ORAL" -> CyanAccent
        "MIDTERM" -> EmeraldGreen
        "FINAL" -> Color(0xFFFFB74D)
        "MONTHLY" -> RubyRed
        else -> TextMuted
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(text = localizedExamTypeLabel(examType), fontSize = tanweerTextSize(8f), fontWeight = FontWeight.Bold, color = color)
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
                fontSize = tanweerTextSize(10f),
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
    onAdd: (subjectId: String, title: String, chapters: String?, notes: String?, date: String, examType: String) -> Unit,
    initialExam: ExamItem? = null,
    isSaving: Boolean = false,
    onUpdate: ((String, String, String, String, String, String, String) -> Unit)? = null
) {
    val availableSubjects = remember(gradeSubjects, initialExam?.id) {
        if (initialExam != null && gradeSubjects.none { it.id == initialExam.subjectId }) {
            gradeSubjects + SubjectItem(
                id = initialExam.subjectId,
                name = initialExam.subjectName,
                icon = initialExam.subjectIcon,
                colorHex = initialExam.colorHex
            )
        } else {
            gradeSubjects
        }
    }
    var selectedSubj by remember(initialExam?.id, availableSubjects) {
        mutableStateOf(availableSubjects.firstOrNull { it.id == initialExam?.subjectId } ?: availableSubjects.firstOrNull())
    }
    var title by remember(initialExam?.id) { mutableStateOf(initialExam?.title.orEmpty()) }
    var examType by remember(initialExam?.id) { mutableStateOf(initialExam?.examType ?: "MONTHLY") }
    var chapters by remember(initialExam?.id) { mutableStateOf(initialExam?.requiredChapters.orEmpty()) }
    var teacherNotes by remember(initialExam?.id) { mutableStateOf(initialExam?.notes.orEmpty()) }
    var examDate by remember(initialExam?.id, initialExamDate) { mutableStateOf(initialExam?.examDate ?: initialExamDate ?: TanweerViewModel.getTodayDateString()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = remember(examDate) {
            try {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(examDate)?.time
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                        examDate = sdf.format(Date(millis))
                    }
                    showDatePicker = false
                }) {
                    Text("تأكيد", color = RubyRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("إلغاء", color = TextSecondary)
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = MidnightSurface
            )
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = MidnightSurface,
                    titleContentColor = RubyRed,
                    headlineContentColor = TextPrimary,
                    selectedDayContainerColor = RubyRed,
                    selectedDayContentColor = Color.White,
                    todayDateBorderColor = RubyRed
                )
            )
        }
    }

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
                    text = stringResource(if (initialExam == null) R.string.exam_add_title else R.string.exam_edit_title),
                    fontSize = tanweerTextSize(18f),
                    fontWeight = FontWeight.Black,
                    color = RubyRed
                )
                GlassPill(
                    text = when (examType) {
                        "ORAL" -> "اختبار شفهي 🗣️"
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
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "MONTHLY" to "شهري 📝",
                    "ORAL" to "شفهي 🗣️",
                    "MIDTERM" to "نصفي 📑",
                    "FINAL" to "نهائي 🏆"
                ).forEach { (key, label) ->
                    val isSelected = examType == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) RubyRed else GlassSurface)
                            .clickable { examType = key }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = tanweerTextSize(11f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else TextSecondary
                        )
                    }
                }
            }

            // Subject Selector Horizontal Row
            Column {
                Text("اختر المادة:", fontSize = tanweerTextSize(12f), color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableSubjects.forEach { subj ->
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
                                Text(text = subj.icon, fontSize = tanweerTextSize(16f))
                                Text(
                                    text = subj.name,
                                    fontSize = tanweerTextSize(12f),
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

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "تاريخ الاختبار (تاريخ تقويمي معتمد) *",
                    fontSize = tanweerTextSize(12f),
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassSurface)
                        .border(1.dp, GlassBorderSubtle, RoundedCornerShape(12.dp))
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = RubyRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = examDate,
                            fontSize = tanweerTextSize(14f),
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    GlassPill(
                        text = "تحديد التاريخ 📅",
                        color = RubyRed,
                        bgColor = RubyRed.copy(alpha = 0.15f)
                    )
                }
            }

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
                text = stringResource(if (initialExam == null) R.string.exam_add_submit else R.string.exam_edit_save),
                color = RubyRed,
                textColor = Color.White,
                enabled = title.isNotBlank() && selectedSubj != null && !isSaving && (initialExam == null || onUpdate != null),
                onClick = {
                    selectedSubj?.let { s ->
                        if (initialExam == null) {
                            onAdd(s.id, title.trim(), chapters.trim().ifEmpty { null }, teacherNotes.trim().ifEmpty { null }, examDate.trim(), examType)
                        } else {
                            onUpdate?.invoke(initialExam.id, s.id, title.trim(), chapters.trim(), teacherNotes.trim(), examDate.trim(), examType)
                        }
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
    onOpenLibraryBook: () -> Unit,
    bookActionLabel: String,
    canEdit: Boolean,
    onEdit: () -> Unit
) {
    val isDatePassed = isExamDatePassed(exam.examDate, todayDateStr)
    val isReviewed = ExamReviewPolicy.isReviewed(exam)
    val dayName = remember(exam.examDate) { getArabicDayOfWeek(exam.examDate) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MidnightSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = RubyRed) }
    ) {
        StickyActionSheetLayout(
            maxHeightFraction = 0.9f,
            bodyContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header and status remain part of the scrollable details area.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = exam.subjectIcon, fontSize = tanweerTextSize(26f))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(text = "?????? ${exam.subjectName}", fontSize = tanweerTextSize(16f), fontWeight = FontWeight.Black, color = TextPrimary)
                                    ExamTypePill(exam.examType)
                                }
                                Text(text = exam.title, fontSize = tanweerTextSize(13f), color = TextSecondary)
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val dateStatusText = when {
                                isDatePassed -> R.string.exam_schedule_status_passed
                                exam.examDate == todayDateStr -> R.string.exam_schedule_status_today
                                else -> R.string.exam_schedule_status_upcoming
                            }
                            val dateStatusColor = when {
                                isDatePassed -> WarmAmber
                                exam.examDate == todayDateStr -> EmeraldGreen
                                else -> CyanAccent
                            }
                            GlassPill(
                                text = stringResource(dateStatusText),
                                color = dateStatusColor,
                                bgColor = dateStatusColor.copy(alpha = 0.15f)
                            )
                            GlassPill(
                                text = stringResource(
                                    if (isReviewed) R.string.exam_review_status_reviewed else R.string.exam_review_status_not_reviewed
                                ),
                                color = if (isReviewed) EmeraldGreen else RubyRed,
                                bgColor = if (isReviewed) EmeraldGreen.copy(alpha = 0.15f) else RubyRed.copy(alpha = 0.15f)
                            )
                        }
                    }

                    HorizontalDivider(color = GlassBorderSubtle)

                    GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "???? ????????:", fontSize = tanweerTextSize(11f), color = TextSecondary)
                                Text(text = "$dayName ? ${exam.examDate}", fontSize = tanweerTextSize(14f), fontWeight = FontWeight.Bold, color = WarmAmber)
                            }
                            GlassButton(
                                text = if (exam.isCompleted) "????? ???????? ??" else "??? ???????? ?",
                                color = if (exam.isCompleted) EmeraldGreen else CyanAccent,
                                textColor = Color.White,
                                onClick = onToggleDone,
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }

                    if (!exam.requiredChapters.isNullOrBlank()) {
                        GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = "?????? ?????????? ???????:", fontSize = tanweerTextSize(12f), fontWeight = FontWeight.Bold, color = CyanAccent)
                                Text(text = localizedChapterListLabel(exam.requiredChapters), fontSize = tanweerTextSize(13f), color = TextPrimary, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }

                    if (!exam.notes.isNullOrBlank()) {
                        GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = "??????? ??????? ???????:", fontSize = tanweerTextSize(12f), fontWeight = FontWeight.Bold, color = WarmAmber)
                                Text(text = exam.notes, fontSize = tanweerTextSize(13f), color = TextPrimary, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }

                    if (canEdit) {
                        GlassOutlinedButton(
                            text = stringResource(R.string.exam_edit_action),
                            onClick = onEdit,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            },
            footerContent = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exam_detail_action_footer"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassButton(
                        text = "????? ??????? ??",
                        color = WarmAmber,
                        textColor = Color.White,
                        onClick = onScheduleReminder,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("exam_detail_reminder_action")
                    )

                    GlassOutlinedButton(
                        text = bookActionLabel,
                        onClick = onOpenLibraryBook,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("exam_detail_book_action")
                    )
                }
            }
        )
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
