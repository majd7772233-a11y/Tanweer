package com.magd.tanweer.ui.screens

import com.magd.tanweer.R


import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.magd.tanweer.data.model.HomeworkItem
import com.magd.tanweer.data.model.SubjectItem
import com.magd.tanweer.data.remote.NetworkModule
import com.magd.tanweer.data.notifications.TanweerNotificationManager
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.NavigationTab
import com.magd.tanweer.ui.GroupSyncSource
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*
import com.magd.tanweer.util.HomeworkTaskKind
import com.magd.tanweer.util.HomeworkTaskTypePolicy
import com.magd.tanweer.util.HomeworkStudyDatePolicy
import com.magd.tanweer.util.ImageProcessingUtils
import com.magd.tanweer.util.ProcessedPageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class HomeworkFilter(val label: String, val icon: String) {
    ALL("الكل", "📋"),
    TODAY("اليوم", "⚡"),
    TOMORROW("غداً", "🌅"),
    THIS_WEEK("الأسبوع", "🗓️"),
    OVERDUE("متأخر", "⚠️"),
    COMPLETED("المكتملة", "✅")
}

data class HomeworkScanPage(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri? = null,
    val rawBitmap: Bitmap? = null,
    val processedResult: ProcessedPageResult? = null,
    val displayBitmap: Bitmap? = null,
    val rotation: Float = 0f,
    val autoEnhance: Boolean = true,
    val isProcessing: Boolean = false
)

@Composable
fun HomeworkScreen(
    viewModel: TanweerViewModel
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId ?: "" }
    val groupSyncScopeIds by viewModel.groupSyncScopeIds.collectAsStateWithLifecycle()

    val homeworks by viewModel.repository.getHomeworks(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val homeworkSyncState by viewModel.homeworkSyncState.collectAsStateWithLifecycle()
    val scopedHomeworkSyncState = syncStateForScope(homeworkSyncState, groupSyncScopeIds[GroupSyncSource.HOMEWORKS], activeGroupId)
    val hasHomeworkSnapshot = homeworks.isNotEmpty() || hasAuthoritativeSnapshot(scopedHomeworkSyncState)

    val plannedHwIds by viewModel.todayPlannedHomeworkIds
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val books by viewModel.repository.getBooks(currentUser?.gradeId ?: 10)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val gradeSubjects = remember(currentUser?.gradeId) {
        viewModel.getSubjectsForCurrentGrade()
    }

    var selectedFilter by remember { mutableStateOf(HomeworkFilter.ALL) }
    val homeworkFilterListState = rememberLazyListState()
    var selectedTaskTypeFilter by remember { mutableStateOf<String?>(null) }
    val isAddModalOpenFromVm by viewModel.isAddHomeworkModalOpen.collectAsStateWithLifecycle()
    val initialDueDateFromVm by viewModel.initialHomeworkDueDate.collectAsStateWithLifecycle()
    val selectedStudyDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    var isLocalAddModalOpen by rememberSaveable { mutableStateOf(false) }
    val isAddModalOpen = isLocalAddModalOpen || isAddModalOpenFromVm
    var fullScreenImagePreviewUrl by remember { mutableStateOf<String?>(null) }
    var pendingReminderHw by remember { mutableStateOf<com.magd.tanweer.data.model.HomeworkItem?>(null) }
    var homeworkToEdit by remember { mutableStateOf<HomeworkItem?>(null) }
    var homeworkToDelete by remember { mutableStateOf<HomeworkItem?>(null) }
    var isSavingHomeworkEdit by remember { mutableStateOf(false) }
    var isDeletingHomework by remember { mutableStateOf(false) }
    var showExactAlarmDialog by remember { mutableStateOf(false) }
    var showNotificationSettingsDialog by remember { mutableStateOf(false) }

    val notificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingReminderHw?.let { hw ->
                val res = TanweerNotificationManager.scheduleHomeworkReminder(
                    context = context,
                    homeworkId = hw.id,
                    title = hw.title,
                    subjectName = hw.subjectName,
                    dueDate = hw.dueDate
                )
                when (res) {
                    TanweerNotificationManager.ScheduleReminderResult.NOTIFICATIONS_DISABLED -> {
                        Toast.makeText(context, context.getString(R.string.notification_reminders_disabled), Toast.LENGTH_SHORT).show()
                    }
                    TanweerNotificationManager.ScheduleReminderResult.SUCCESS -> {
                        Toast.makeText(context, "🔔 تم تفعيل تذكير دراسي دقيق على جهازك للواجب: ${hw.title}", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, "تعذر جدولة التذكير. يرجى التحقق من إعدادات النظام والبطارية في جهازك.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            showNotificationSettingsDialog = true
        }
        pendingReminderHw = null
    }

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

    val typeFilteredHomeworks = remember(homeworks, selectedTaskTypeFilter) {
        when (selectedTaskTypeFilter) {
            "HOMEWORK" -> homeworks.filter { HomeworkTaskTypePolicy.isHomework(it.taskType) }
            "TASK" -> homeworks.filter { HomeworkTaskTypePolicy.isTask(it.taskType) }
            else -> homeworks
        }
    }

    val filteredHomeworks = remember(typeFilteredHomeworks, selectedFilter) {
        when (selectedFilter) {
            HomeworkFilter.ALL -> typeFilteredHomeworks
            HomeworkFilter.TODAY -> typeFilteredHomeworks.filter { it.dueDate == todayDate }
            HomeworkFilter.TOMORROW -> typeFilteredHomeworks.filter { it.dueDate == tomorrowDate }
            HomeworkFilter.THIS_WEEK -> typeFilteredHomeworks.filter { it.dueDate in todayDate..endOfWeekDate }
            HomeworkFilter.OVERDUE -> typeFilteredHomeworks.filter { !it.isCompleted && it.dueDate < todayDate }
            HomeworkFilter.COMPLETED -> typeFilteredHomeworks.filter { it.isCompleted }
        }
    }

    val homeworkCount = remember(homeworks) {
        homeworks.count { HomeworkTaskTypePolicy.isHomework(it.taskType) }
    }
    val projectCount = remember(homeworks) {
        homeworks.count { HomeworkTaskTypePolicy.isTask(it.taskType) }
    }
    val otherAssignmentCount = remember(homeworks) {
        homeworks.count { HomeworkTaskTypePolicy.classify(it.taskType) == HomeworkTaskKind.UNKNOWN }
    }
    val todayHomeworkCount = remember(homeworks, todayDate) {
        homeworks.count { HomeworkTaskTypePolicy.isHomework(it.taskType) && it.dueDate == todayDate }
    }
    val todayProjectCount = remember(homeworks, todayDate) {
        homeworks.count { HomeworkTaskTypePolicy.isTask(it.taskType) && it.dueDate == todayDate }
    }
    val overdueCount = remember(typeFilteredHomeworks, todayDate) {
        typeFilteredHomeworks.count { !it.isCompleted && it.dueDate < todayDate }
    }
    val todayCount = remember(typeFilteredHomeworks, todayDate) {
        typeFilteredHomeworks.count { it.dueDate == todayDate }
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
                            style = AppTypography.SectionHeader.copy(color = CyanAccent)
                        )
                        Text(
                            text = if (hasHomeworkSnapshot) {
                                stringResource(R.string.homework_screen_summary, homeworkCount, projectCount, otherAssignmentCount)
                            } else {
                                stringResource(R.string.homework_screen_summary_loading)
                            },
                            style = AppTypography.Body.copy(color = TextSecondary)
                        )
                        if (hasHomeworkSnapshot) {
                            Text(
                                text = stringResource(R.string.homework_screen_due_today, todayHomeworkCount, todayProjectCount),
                                style = AppTypography.Meta.copy(color = TextMuted)
                            )
                        }
                    }
                    GlassButton(
                        text = "إضافة واجب",
                        icon = Icons.Default.Add,
                        onClick = { isLocalAddModalOpen = true },
                        modifier = Modifier.height(38.dp)
                    )
                }
            }

            // Task Type Segmented Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTaskTypeFilter == null,
                        onClick = { selectedTaskTypeFilter = null },
                        label = { Text(if (hasHomeworkSnapshot) stringResource(R.string.homework_type_filter_all, homeworks.size) else stringResource(R.string.homework_type_filter_all_loading)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanAccent,
                            selectedLabelColor = TextOnAccent
                        )
                    )
                    FilterChip(
                        selected = selectedTaskTypeFilter == "HOMEWORK",
                        onClick = { selectedTaskTypeFilter = if (selectedTaskTypeFilter == "HOMEWORK") null else "HOMEWORK" },
                        label = {
                            Text(
                                if (hasHomeworkSnapshot) stringResource(R.string.homework_type_filter_homework, homeworkCount)
                                else stringResource(R.string.homework_type_filter_homework_loading)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanAccent,
                            selectedLabelColor = TextOnAccent
                        )
                    )
                    FilterChip(
                        selected = selectedTaskTypeFilter == "TASK",
                        onClick = { selectedTaskTypeFilter = if (selectedTaskTypeFilter == "TASK") null else "TASK" },
                        label = {
                            Text(
                                if (hasHomeworkSnapshot) stringResource(R.string.homework_type_filter_projects, projectCount)
                                else stringResource(R.string.homework_type_filter_projects_loading)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanAccent,
                            selectedLabelColor = TextOnAccent
                        )
                    )
                }
            }

            // Filter Tabs Bar
            item {
                HorizontalFilterStrip(
                    state = homeworkFilterListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MidnightSurface)
                        .border(1.dp, GlassBorderSubtle, RoundedCornerShape(14.dp)),
                    edgeColor = MidnightSurface,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    itemSpacing = 6.dp
                ) {
                    items(HomeworkFilter.values()) { filter ->
                        val isSelected = selectedFilter == filter
                        val badgeCount = if (!hasHomeworkSnapshot) null else when (filter) {
                            HomeworkFilter.ALL -> typeFilteredHomeworks.size
                            HomeworkFilter.TODAY -> todayCount
                            HomeworkFilter.OVERDUE -> overdueCount
                            HomeworkFilter.COMPLETED -> typeFilteredHomeworks.count { it.isCompleted }
                            else -> null
                        }
                        GlassFilterChip(
                            selected = isSelected,
                            onClick = { selectedFilter = filter },
                            modifier = Modifier,
                            containerColor = Color.Transparent,
                            selectedContainerColor = CyanAccent,
                            labelColor = TextSecondary,
                            selectedLabelColor = TextOnAccent
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = filter.icon,
                                    modifier = Modifier.clearAndSetSemantics {},
                                    style = AppTypography.Chip
                                )
                                Text(
                                    text = filter.label,
                                    style = AppTypography.Chip.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) TextOnAccent
                                            else if (filter == HomeworkFilter.OVERDUE && overdueCount > 0) Color(0xFFFF5252)
                                            else TextSecondary
                                    )
                                )
                                if (isSelected) {
                                    Text(
                                        text = "✓",
                                        modifier = Modifier.clearAndSetSemantics {},
                                        style = AppTypography.Chip.copy(
                                            color = TextOnAccent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                if (badgeCount != null && badgeCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) TextOnAccent.copy(alpha = 0.2f)
                                                else if (filter == HomeworkFilter.OVERDUE) Color(0xFFFF5252)
                                                else GlassSurfaceLight
                                            )
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "$badgeCount",
                                            style = AppTypography.Badge.copy(
                                                color = if (isSelected) TextOnAccent
                                                    else if (filter == HomeworkFilter.OVERDUE) Color.White
                                                    else TextSecondary
                                            )
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
                    if (hasHomeworkSnapshot) {
                        EmptyStateGlass(
                            title = when (selectedTaskTypeFilter) {
                                "HOMEWORK" -> stringResource(R.string.homework_empty_homeworks_title)
                                "TASK" -> stringResource(R.string.homework_empty_projects_title)
                                else -> if (homeworks.isEmpty()) stringResource(R.string.homework_empty_all_title)
                                    else stringResource(R.string.homework_empty_filtered_title)
                            },
                            subtitle = stringResource(R.string.homework_empty_description),
                            icon = "📝",
                            actionButtonText = if (homeworks.isEmpty()) stringResource(R.string.homework_empty_add_action)
                                else stringResource(R.string.homework_empty_show_all_action),
                            onActionClick = {
                                if (homeworks.isEmpty()) isLocalAddModalOpen = true
                                else {
                                    selectedFilter = HomeworkFilter.ALL
                                    selectedTaskTypeFilter = null
                                }
                            }
                        )
                    } else {
                        PendingSyncEmptyState(dataLabel = "الواجبات", syncState = scopedHomeworkSyncState)
                    }
                }
            } else {
                items(filteredHomeworks, key = { it.id }) { hw ->
                    val isOverdue = !hw.isCompleted && hw.dueDate < todayDate
                    val isDueToday = hw.dueDate == todayDate

                    HomeworkCard(
                        homework = hw,
                        isOverdue = isOverdue,
                        isDueToday = isDueToday,
                        isPlanned = plannedHwIds.contains(hw.id),
                        canEdit = HomeworkOwnershipPolicy.canEdit(
                            createdBy = hw.createdBy,
                            syncStatus = hw.syncStatus,
                            currentUserId = currentUser?.id
                        ),
                        canDelete = HomeworkOwnershipPolicy.canDelete(
                            createdBy = hw.createdBy,
                            syncStatus = hw.syncStatus,
                            currentUserId = currentUser?.id
                        ),
                        onEdit = { homeworkToEdit = hw },
                        onDelete = { homeworkToDelete = hw },
                        onToggle = {
                            val willBeCompleted = !hw.isCompleted
                            if (willBeCompleted) {
                                TanweerNotificationManager.cancelReminder(context, hw.id)
                            }
                            viewModel.toggleHomework(hw.id, hw.isCompleted) { res ->
                                when (res) {
                                    is com.magd.tanweer.data.local.HomeworkToggleResult.SyncedWithServer -> {
                                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                    }
                                    is com.magd.tanweer.data.local.HomeworkToggleResult.QueuedOffline -> {
                                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                    }
                                    is com.magd.tanweer.data.local.HomeworkToggleResult.Failed -> {
                                        Toast.makeText(context, res.error, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        onRetrySync = { viewModel.retrySyncHomework(hw.id) },
                        onImageClick = { url -> fullScreenImagePreviewUrl = url },
                        onOpenBookPage = if (!hw.pageNumbers.isNullOrBlank()) {
                            {
                                val matchingBook = books.find { it.subjectId == hw.subjectId }
                                val pageNum = Regex("""\d+""")
                                    .find(hw.pageNumbers.orEmpty())
                                    ?.value
                                    ?.toIntOrNull()
                                    ?: 1
                                if (matchingBook != null) {
                                    viewModel.openBookInPdfReader(matchingBook, targetPage = (pageNum - 1).coerceAtLeast(0))
                                } else {
                                    Toast.makeText(context, "جاري فتح مكتبة الكتب للمادة...", Toast.LENGTH_SHORT).show()
                                    viewModel.setSubScreen(SubScreen.LIBRARY)
                                }
                            }
                        } else null,
                        onSetReminder = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                val hasNotif = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (!hasNotif) {
                                    pendingReminderHw = hw
                                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    return@HomeworkCard
                                }
                            }

                            val result = TanweerNotificationManager.scheduleHomeworkReminder(
                                context = context,
                                homeworkId = hw.id,
                                title = hw.title,
                                subjectName = hw.subjectName,
                                dueDate = hw.dueDate
                            )
                            when (result) {
                                TanweerNotificationManager.ScheduleReminderResult.NOTIFICATIONS_DISABLED -> {
                                    Toast.makeText(context, context.getString(R.string.notification_reminders_disabled), Toast.LENGTH_SHORT).show()
                                }
                                TanweerNotificationManager.ScheduleReminderResult.SUCCESS -> {
                                    Toast.makeText(context, "🔔 تم تفعيل تذكير دراسي دقيق على جهازك للواجب: ${hw.title}", Toast.LENGTH_SHORT).show()
                                }
                                TanweerNotificationManager.ScheduleReminderResult.PERMISSION_NOTIFICATIONS_DENIED -> {
                                    pendingReminderHw = hw
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        Toast.makeText(context, "تعذر إرسال التنبيه. يرجى منح تنوير إذن الإشعارات من إعدادات الهاتف.", Toast.LENGTH_LONG).show()
                                    }
                                }
                                TanweerNotificationManager.ScheduleReminderResult.PERMISSION_EXACT_ALARM_DENIED -> {
                                    showExactAlarmDialog = true
                                }
                                TanweerNotificationManager.ScheduleReminderResult.ALARM_SERVICE_UNAVAILABLE -> {
                                    Toast.makeText(context, "خدمة التنبيهات غير متوفرة على هذا الجهاز حالياً.", Toast.LENGTH_SHORT).show()
                                }
                                TanweerNotificationManager.ScheduleReminderResult.GENERAL_ERROR -> {
                                    Toast.makeText(context, "تعذر جدولة التذكير. تأكد من إعدادات النظام والبطارية في جهازك.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onAddToDailyPlan = {
                            viewModel.togglePlannedHomework(
                                homeworkId = hw.id,
                                title = hw.title,
                                subjectName = hw.subjectName
                            ) { isNowPlanned ->
                                if (isNowPlanned) {
                                    Toast.makeText(context, "📌 تمت إضافة الواجب إلى خطة إنجاز اليوم", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "تمت إزالة الواجب من خطة إنجاز اليوم", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onAskQuestion = {
                            viewModel.addIssue(
                                title = "استفسار حول واجب ${hw.subjectName}: ${hw.title}",
                                description = "ما هو المطلوب في هذا الواجب؟ صفحة: ${hw.pageNumbers ?: "-"}، أسئلة: ${hw.questionNumbers ?: "-"}",
                                subjectId = hw.subjectId,
                                homeworkId = hw.id
                            ) { res ->
                                if (res.isSuccess) {
                                    val outcome = res.getOrNull()
                                    val msg = outcome?.message ?: "تم فتح استفسار مرتبط بالواجب بنجاح ❓"
                                    Toast.makeText(context, msg, if (outcome?.isSynced == true) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                                    viewModel.setTab(NavigationTab.ISSUES)
                                } else {
                                    Toast.makeText(context, res.exceptionOrNull()?.message ?: "تعذر فتح الاستفسار", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }
        }

        // Add Homework Dialog with Image Attachment
        if (isAddModalOpen) {
            AddHomeworkDialog(
                subjects = gradeSubjects,
                initialDueDate = initialDueDateFromVm,
                initialStudyDate = selectedStudyDate,
                onDismiss = {
                    isLocalAddModalOpen = false
                    viewModel.closeAddHomeworkDialog()
                },
                onAddWithPages = { subjId, title, details, pages, questions, dueDate, studyDate, taskType, scanPages, finishSubmission ->
                    viewModel.addHomework(
                        subjectId = subjId,
                        title = title,
                        details = details,
                        pageNumbers = pages,
                        questionNumbers = questions,
                        dueDate = dueDate,
                        studyDate = studyDate,
                        taskType = taskType,
                        pages = scanPages.mapNotNull { it.processedResult },
                        isOfficial = false,
                        onResult = { result ->
                            finishSubmission()
                            if (result.isSuccess) {
                                scanPages.forEach { page ->
                                    ImageProcessingUtils.deleteDraftSource(context, page.uri)
                                }
                                val outcome = result.getOrNull()
                                if (outcome?.isSynced == true) {
                                    Toast.makeText(context, "تم حفظ ونشر الواجب على الخادم بنجاح ✨", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, outcome?.message ?: "تم حفظ الواجب محلياً بنجاح، وسيتم رفعه للخادم فور توفر الاتصال ⏳", Toast.LENGTH_LONG).show()
                                }
                                isLocalAddModalOpen = false
                                viewModel.closeAddHomeworkDialog()
                            } else {
                                val err = result.exceptionOrNull()?.message ?: "حدث خطأ أثناء حفظ الواجب"
                                Toast.makeText(context, "تعذر النشر: $err", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            )
        }

        homeworkToEdit?.let { homework ->
            EditHomeworkDialog(
                homework = homework,
                isSaving = isSavingHomeworkEdit,
                onDismiss = {
                    if (!isSavingHomeworkEdit) homeworkToEdit = null
                },
                onSave = { title, details, pages, questions, dueDate ->
                    if (!isSavingHomeworkEdit) {
                        isSavingHomeworkEdit = true
                        viewModel.updateHomework(
                            homeworkId = homework.id,
                            dueDate = dueDate,
                            title = title,
                            details = details,
                            pageNumbers = pages,
                            questionNumbers = questions
                        ) { result ->
                            isSavingHomeworkEdit = false
                            result.fold(
                                onSuccess = {
                                    Toast.makeText(context, context.getString(R.string.homework_edit_success), Toast.LENGTH_SHORT).show()
                                    homeworkToEdit = null
                                },
                                onFailure = { error ->
                                    Toast.makeText(
                                        context,
                                        error.message?.takeIf { it.isNotBlank() }
                                            ?: context.getString(R.string.homework_edit_failed),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        }
                    }
                }
            )
        }

        homeworkToDelete?.let { homework ->
            AlertDialog(
                onDismissRequest = { if (!isDeletingHomework) homeworkToDelete = null },
                containerColor = MidnightSurface,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary,
                title = { Text(stringResource(R.string.homework_delete_title), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.homework_delete_message)) },
                confirmButton = {
                    TextButton(
                        enabled = !isDeletingHomework,
                        onClick = {
                            if (!isDeletingHomework) {
                                isDeletingHomework = true
                                viewModel.deleteHomework(homework.id) { result ->
                                    isDeletingHomework = false
                                    result.fold(
                                        onSuccess = {
                                            TanweerNotificationManager.cancelReminder(context, homework.id)
                                            Toast.makeText(context, context.getString(R.string.homework_delete_success), Toast.LENGTH_SHORT).show()
                                            homeworkToDelete = null
                                        },
                                        onFailure = { error ->
                                            Toast.makeText(
                                                context,
                                                error.message?.takeIf { it.isNotBlank() }
                                                    ?: context.getString(R.string.homework_delete_failed),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    )
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.homework_delete_confirm), color = Color(0xFFFF6B6B))
                    }
                },
                dismissButton = {
                    TextButton(enabled = !isDeletingHomework, onClick = { homeworkToDelete = null }) {
                        Text(stringResource(R.string.homework_edit_cancel), color = TextSecondary)
                    }
                }
            )
        }

        // Full Screen Image Preview Modal
        if (fullScreenImagePreviewUrl != null) {
            Dialog(
                onDismissRequest = { fullScreenImagePreviewUrl = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.95f))
                        .clickable { fullScreenImagePreviewUrl = null },
                    contentAlignment = Alignment.Center
                ) {
                    val imgUrl = fullScreenImagePreviewUrl!!
                    val isLocalImage = imgUrl.startsWith("file://") ||
                        (!imgUrl.startsWith("http://", true) && !imgUrl.startsWith("https://", true) &&
                            !imgUrl.startsWith("/api/", true) && !imgUrl.startsWith("api/", true))
                    if (isLocalImage) {
                        val file = File(imgUrl.removePrefix("file://"))
                        if (file.exists()) {
                            val bmp = BitmapFactory.decodeFile(file.absolutePath)
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "معاينة صورة الواجب",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    } else {
                        AsyncImage(
                            model = NetworkModule.resolveApiUrl(imgUrl),
                            contentDescription = "معاينة صورة الواجب",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }

                    IconButton(
                        onClick = { fullScreenImagePreviewUrl = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(24.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                    }
                }
            }
        }

        if (showExactAlarmDialog) {
            AlertDialog(
                onDismissRequest = { showExactAlarmDialog = false },
                title = { Text("تفعيل التنبيهات الدقيقة ⏰", color = CyanAccent, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "يتطلب إرسال تذكير الواجب في موعده الدقيق السماح للتطبيق بجدولة التنبيهات والمنبهات (Alarms & Reminders) من إعدادات الهاتف.",
                        style = AppTypography.BodyLarge.copy(color = TextPrimary)
                    )
                },
                confirmButton = {
                    GlassButton(
                        text = "فتح إعدادات النظام ⚙️",
                        color = CyanAccent,
                        onClick = {
                            showExactAlarmDialog = false
                            try {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
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
                                Toast.makeText(context, "يرجى الانتقال لإعدادات التطبيق وتفعيل المنبهات الدقيقة", Toast.LENGTH_SHORT).show()
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

        if (showNotificationSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showNotificationSettingsDialog = false },
                title = { Text("إذن التنبيهات والإشعارات 🔔", color = WarmAmber, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "لتذكيرك بموعد تسليم الواجب والمذاكرة، يحتاج تنوير إلى تفعيل إذن الإشعارات من إعدادات جهازك.",
                        style = AppTypography.BodyLarge.copy(color = TextPrimary)
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
                                    Toast.makeText(context, "يرجى تفعيل الإشعارات من إعدادات الهاتف", Toast.LENGTH_SHORT).show()
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EditHomeworkDialog(
    homework: HomeworkItem,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, details: String, pages: String, questions: String, dueDate: String) -> Unit
) {
    var title by rememberSaveable(homework.id) { mutableStateOf(homework.title) }
    var details by rememberSaveable(homework.id) { mutableStateOf(homework.details.orEmpty()) }
    var pages by rememberSaveable(homework.id) { mutableStateOf(homework.pageNumbers.orEmpty()) }
    var questions by rememberSaveable(homework.id) { mutableStateOf(homework.questionNumbers.orEmpty()) }
    var dueDate by rememberSaveable(homework.id) { mutableStateOf(homework.dueDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    val initialDateMillis = remember(homework.dueDate) {
        runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(homework.dueDate)?.time
        }.getOrNull() ?: System.currentTimeMillis()
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        dueDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }.format(Date(millis))
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.homework_edit_date_confirm), color = CyanAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.homework_edit_cancel), color = TextSecondary)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = MidnightSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text(stringResource(R.string.homework_edit_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    enabled = !isSaving,
                    label = { Text(stringResource(R.string.homework_edit_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    enabled = !isSaving,
                    label = { Text(stringResource(R.string.homework_edit_details_label)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pages,
                    onValueChange = { pages = it },
                    enabled = !isSaving,
                    label = { Text(stringResource(R.string.homework_edit_pages_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = questions,
                    onValueChange = { questions = it },
                    enabled = !isSaving,
                    label = { Text(stringResource(R.string.homework_edit_questions_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dueDate,
                    onValueChange = {},
                    readOnly = true,
                    enabled = !isSaving,
                    label = { Text(stringResource(R.string.homework_edit_due_date_label)) },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }, enabled = !isSaving) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = stringResource(R.string.homework_edit_due_date_label), tint = CyanAccent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving && title.isNotBlank() && dueDate.isNotBlank(),
                onClick = { onSave(title, details, pages, questions, dueDate) }
            ) {
                Text(stringResource(R.string.homework_edit_save), color = CyanAccent)
            }
        },
        dismissButton = {
            TextButton(enabled = !isSaving, onClick = onDismiss) {
                Text(stringResource(R.string.homework_edit_cancel), color = TextSecondary)
            }
        }
    )
}

@Composable
fun HomeworkCard(
    homework: HomeworkItem,
    isOverdue: Boolean = false,
    isDueToday: Boolean = false,
    isPlanned: Boolean = false,
    canEdit: Boolean = false,
    canDelete: Boolean = false,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onToggle: () -> Unit,
    onRetrySync: (() -> Unit)? = null,
    onImageClick: (String) -> Unit,
    onOpenBookPage: (() -> Unit)? = null,
    onSetReminder: (() -> Unit)? = null,
    onAddToDailyPlan: (() -> Unit)? = null,
    onAskQuestion: () -> Unit
) {
    val borderColor = when {
        homework.isCompleted -> EmeraldGreen.copy(alpha = 0.4f)
        isOverdue -> Color(0xFFFF5252).copy(alpha = 0.5f)
        isDueToday -> WarmAmber.copy(alpha = 0.5f)
        else -> GlassBorderSubtle
    }

    val countdownText = remember(homework.dueDate) {
        getRemainingTimeText(homework.dueDate)
    }
    var actionsExpanded by remember(homework.id) { mutableStateOf(false) }

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
                        Text(text = homework.subjectIcon, fontSize = TanweerTypographyTokens.SizeHeader)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        HomeworkCardHeading(homework = homework)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = if (countdownText.isNotBlank()) countdownText else "التسليم: ${homework.dueDate}",
                                style = AppTypography.Body.copy(
                                    color = when {
                                        homework.isCompleted -> EmeraldGreen
                                        isOverdue -> Color(0xFFFF5252)
                                        isDueToday -> WarmAmber
                                        else -> TextSecondary
                                    },
                                    fontWeight = if (isOverdue || isDueToday) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = homework.isCompleted,
                        onCheckedChange = { onToggle() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = EmeraldGreen,
                            uncheckedColor = TextSecondary
                        )
                    )
                    if (canEdit || canDelete) {
                        Box {
                            IconButton(onClick = { actionsExpanded = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.homework_action_more),
                                    tint = TextSecondary
                                )
                            }
                            DropdownMenu(
                                expanded = actionsExpanded,
                                onDismissRequest = { actionsExpanded = false },
                                containerColor = MidnightSurface
                            ) {
                                if (canEdit) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.homework_action_edit), color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = CyanAccent) },
                                        onClick = {
                                            actionsExpanded = false
                                            onEdit()
                                        }
                                    )
                                }
                                if (canDelete) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.homework_action_delete), color = Color(0xFFFF6B6B)) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF6B6B)) },
                                        onClick = {
                                            actionsExpanded = false
                                            onDelete()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
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
                            style = AppTypography.Body.copy(color = TextSecondary)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (!homework.details.isNullOrBlank()) 6.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!homework.pageNumbers.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CyanGlow)
                                    .clickable(enabled = onOpenBookPage != null) { onOpenBookPage?.invoke() }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "📖 صفحة: ${homework.pageNumbers} ↗",
                                    style = AppTypography.BodyBold.copy(color = CyanAccent)
                                )
                            }
                        }
                        if (!homework.questionNumbers.isNullOrBlank()) {
                            Text(
                                text = "✏️ الأسئلة: ${homework.questionNumbers}",
                                style = AppTypography.Body.copy(color = WarmAmber, fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }
            }

            // Attached Images Solution Preview Strip
            if (homework.mediaUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MidnightSurface.copy(alpha = 0.7f))
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📷 صور وتوثيق الحل (${homework.mediaUrls.size}):",
                            style = AppTypography.Caption.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "اضغط للمعاينة 🔍",
                            style = AppTypography.Meta.copy(color = TextMuted)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(homework.mediaUrls) { imgUrl ->
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, CyanGlow, RoundedCornerShape(8.dp))
                                    .clickable { onImageClick(imgUrl) }
                            ) {
                                val isLocalImage = imgUrl.startsWith("file://") ||
                                    (!imgUrl.startsWith("http://", true) && !imgUrl.startsWith("https://", true) &&
                                        !imgUrl.startsWith("/api/", true) && !imgUrl.startsWith("api/", true))
                                if (isLocalImage) {
                                    val file = File(imgUrl.removePrefix("file://"))
                                    if (file.exists()) {
                                        val bmp = BitmapFactory.decodeFile(file.absolutePath)
                                        if (bmp != null) {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = "صورة الحل",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                } else {
                                    AsyncImage(
                                        model = NetworkModule.resolveApiUrl(imgUrl),
                                        contentDescription = "صورة الحل",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (homework.syncStatus != "SYNCED") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (homework.syncStatus == "FAILED") Color(0xFF3E1F24)
                            else Color(0xFF1E2D3D)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (homework.syncStatus == "FAILED") "⚠️ لم يُرفع للسيرفر (محلي)" else "🔄 جاري المزامنة مع السيرفر...",
                        style = AppTypography.CaptionNormal.copy(color = if (homework.syncStatus == "FAILED") Color(0xFFFF8A80) else CyanAccent)
                    )
                    if (homework.syncStatus == "FAILED" && onRetrySync != null) {
                        Text(
                            text = "إعادة المحاولة 🔁",
                            style = AppTypography.Caption.copy(color = CyanAccent, fontWeight = FontWeight.Bold),
                            modifier = Modifier.clickable { onRetrySync() }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Action Buttons Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (onAddToDailyPlan != null) {
                        IconButton(
                            onClick = onAddToDailyPlan,
                            modifier = Modifier
                                .size(32.dp)
                                .then(
                                    if (isPlanned) Modifier.background(WarmAmber.copy(alpha = 0.25f), CircleShape)
                                    else Modifier
                                )
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = if (isPlanned) "إزالة من الخطة" else "إضافة للخطة",
                                tint = if (isPlanned) WarmAmber else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (onSetReminder != null) {
                        IconButton(
                            onClick = onSetReminder,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.NotificationsNone, contentDescription = "تذكير", tint = CyanAccent, modifier = Modifier.size(16.dp))
                        }
                    }
                }

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
                    Text("اسأل عن الواجب", style = AppTypography.Caption.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

fun getRemainingTimeText(dueDateStr: String): String {
    try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val dueDate = sdf.parse(dueDateStr) ?: return ""
        val now = Date()
        val diffMs = dueDate.time + (24 * 60 * 60 * 1000L - 1) - now.time
        if (diffMs < 0) return "متأخر ⚠️"
        val hours = (diffMs / (1000 * 60 * 60)).toInt()
        return when {
            hours < 24 -> "متبقي $hours ساعة ⏳"
            hours < 48 -> "متبقي يوم واحد 🌅"
            else -> "متبقي ${hours / 24} أيام 🗓️"
        }
    } catch (_: Exception) {
        return ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHomeworkDialog(
    subjects: List<SubjectItem>,
    initialDueDate: String? = null,
    initialStudyDate: String? = null,
    isOfficial: Boolean = false,
    onDismiss: () -> Unit,
    onAddWithPages: (subjectId: String, title: String, details: String?, pages: String?, questions: String?, dueDate: String, studyDate: String, taskType: String, scanPages: List<HomeworkScanPage>, onFinished: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedSubjId by rememberSaveable { mutableStateOf(subjects.firstOrNull()?.id ?: "math") }
    var title by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }
    var pageNumbers by rememberSaveable { mutableStateOf("") }
    var questionNumbers by rememberSaveable { mutableStateOf("") }
    var dueDate by rememberSaveable(initialDueDate) { mutableStateOf(initialDueDate ?: TanweerViewModel.getTodayDateString()) }
    var studyDate by rememberSaveable(initialStudyDate) {
        mutableStateOf(HomeworkStudyDatePolicy.initial(initialStudyDate, TanweerViewModel.getTodayDateString()))
    }
    var taskType by rememberSaveable { mutableStateOf("HOMEWORK") }
    val scannedPagesSaver = remember(context) {
        scanPageListSaver<HomeworkScanPage>(
            toRecord = { page ->
                ScanPageDraftRecord(
                    id = page.id,
                    sourceUri = page.uri?.toString(),
                    processedFilePath = page.processedResult?.file?.absolutePath,
                    rotation = page.rotation,
                    autoEnhance = page.autoEnhance,
                    blackAndWhite = false,
                    isProcessing = page.isProcessing
                )
            },
            fromRecord = { record, processed, preview, shouldResume ->
                HomeworkScanPage(
                    id = record.id,
                    uri = record.sourceUri?.let(Uri::parse),
                    rawBitmap = null,
                    processedResult = processed,
                    displayBitmap = preview,
                    rotation = record.rotation,
                    autoEnhance = record.autoEnhance,
                    isProcessing = shouldResume
                )
            }
        )
    }
    var scannedPages by rememberSaveable(stateSaver = scannedPagesSaver) {
        mutableStateOf<List<HomeworkScanPage>>(emptyList())
    }
    val processingPageIds = remember { mutableSetOf<String>() }
    var showDatePicker by remember { mutableStateOf(false) }
    var showStudyDatePicker by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    val studyDatePickerState = key(studyDate) {
        rememberDatePickerState(
            initialSelectedDateMillis = remember(studyDate) {
                try {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.parse(studyDate)?.time
                } catch (_: Exception) {
                    System.currentTimeMillis()
                }
            }
        )
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = remember(dueDate) {
            try {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(dueDate)?.time
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
    )

    if (showStudyDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showStudyDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    studyDatePickerState.selectedDateMillis?.let { millis ->
                        studyDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }.format(Date(millis))
                    }
                    showStudyDatePicker = false
                }) {
                    Text(stringResource(R.string.homework_study_date_confirm), color = CyanAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStudyDatePicker = false }) {
                    Text(stringResource(R.string.homework_study_date_cancel), color = TextSecondary)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = MidnightSurface)
        ) {
            DatePicker(
                state = studyDatePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = MidnightSurface,
                    titleContentColor = CyanAccent,
                    headlineContentColor = TextPrimary,
                    selectedDayContainerColor = CyanAccent,
                    selectedDayContentColor = TextOnAccent,
                    todayDateBorderColor = CyanAccent
                )
            )
        }
    }
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                        dueDate = sdf.format(Date(millis))
                    }
                    showDatePicker = false
                }) {
                    Text("تأكيد", color = CyanAccent, fontWeight = FontWeight.Bold)
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
                    titleContentColor = CyanAccent,
                    headlineContentColor = TextPrimary,
                    selectedDayContainerColor = CyanAccent,
                    selectedDayContentColor = TextOnAccent,
                    todayDateBorderColor = CyanAccent
                )
            )
        }
    }

    fun processPageItem(page: HomeworkScanPage, onUpdated: (HomeworkScanPage) -> Unit) {
        scope.launch {
            val sourceUri = ImageProcessingUtils.persistDraftSource(
                context = context,
                pageId = page.id,
                sourceUri = page.uri,
                rawBitmap = page.rawBitmap
            )
            if (sourceUri == null) {
                onUpdated(page.copy(isProcessing = false))
                Toast.makeText(context, context.getString(R.string.scan_draft_save_failed), Toast.LENGTH_LONG).show()
                return@launch
            }

            val sourcePage = page.copy(uri = sourceUri, rawBitmap = null, isProcessing = true)
            onUpdated(sourcePage)

            val result = ImageProcessingUtils.loadAndProcessImage(
                context = context,
                uri = sourceUri,
                rawBitmap = null,
                autoEnhance = page.autoEnhance,
                blackAndWhite = false,
                rotationDegrees = page.rotation
            )
            val preview = result?.file?.let {
                ImageProcessingUtils.decodeProcessedPreview(it.absolutePath)
            }
            onUpdated(
                sourcePage.copy(
                    processedResult = result,
                    displayBitmap = preview,
                    isProcessing = false
                )
            )
        }
    }

    LaunchedEffect(scannedPages.filter {
        it.isProcessing && it.processedResult == null
    }.map { it.id }) {
        scannedPages.filter { it.isProcessing && it.processedResult == null }.forEach { page ->
            if (processingPageIds.add(page.id)) {
                processPageItem(page) { updated ->
                    if (scannedPages.any { it.id == updated.id }) {
                        scannedPages = scannedPages.map { if (it.id == updated.id) updated else it }
                    } else {
                        // The user removed this page while its background processing was
                        // still running. Delete any output produced after the removal.
                        ImageProcessingUtils.deleteDraftPage(
                            context = context,
                            sourceUri = updated.uri,
                            processedFilePath = updated.processedResult?.file?.absolutePath
                        )
                    }
                    if (!updated.isProcessing) processingPageIds.remove(page.id)
                }
            }
        }
    }

    fun discardDraftPages() {
        scannedPages.forEach { page ->
            ImageProcessingUtils.deleteDraftPage(
                context = context,
                sourceUri = page.uri,
                processedFilePath = page.processedResult?.file?.absolutePath
            )
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            val newPages = uris.map { uri ->
                HomeworkScanPage(uri = uri, isProcessing = true)
            }
            scannedPages = scannedPages + newPages
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val pageId = java.util.UUID.randomUUID().toString()
            val sourceUri = ImageProcessingUtils.persistCameraPreview(context, pageId, bitmap)
            if (sourceUri == null) {
                Toast.makeText(context, context.getString(R.string.scan_draft_save_failed), Toast.LENGTH_LONG).show()
            } else {
                val newPage = HomeworkScanPage(
                    id = pageId,
                    uri = sourceUri,
                    rawBitmap = null,
                    isProcessing = true
                )
                scannedPages = scannedPages + newPage
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(
                context,
                "يلزم السماح لتنوير باستخدام الكاميرا حتى تتمكن من تصوير مرفقات الواجب.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Dialog(
        onDismissRequest = { if (!isSubmitting) { discardDraftPages(); onDismiss() } },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MidnightSurface)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { if (!isSubmitting) { discardDraftPages(); onDismiss() } },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(GlassSurface)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextPrimary)
                            }
                            Text(
                                text = if (isOfficial) "إضافة واجب رسمي معتمد 📝" else "إضافة وتوثيق واجب مدرسي 📝",
                                style = AppTypography.CardTitle.copy(color = CyanAccent)
                            )
                        }

                        GlassPill(
                            text = if (isOfficial) "رسمي من الأستاذ 🎓" else if (taskType == "HOMEWORK") "واجب منزلي" else "مشروع / بحث",
                            color = CyanAccent,
                            bgColor = CyanGlow
                        )
                    }
                }
            },
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MidnightSurface)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassOutlinedButton(
                            text = "إلغاء",
                            onClick = { if (!isSubmitting) { discardDraftPages(); onDismiss() } },
                            modifier = Modifier.weight(1f)
                        )
                        GlassButton(
                            text = if (isSubmitting) "جارٍ النشر..." else if (isOfficial) "نشر الواجب الرسمي ⭐" else "حفظ ونشر الواجب ✨",
                            enabled = title.isNotBlank() && !isSubmitting,
                            onClick = {
                                if (title.isNotBlank() && !isSubmitting) {
                                    isSubmitting = true
                                    onAddWithPages(
                                        selectedSubjId,
                                        title,
                                        details,
                                        pageNumbers,
                                        questionNumbers,
                                        dueDate,
                                        studyDate,
                                        taskType,
                                        scannedPages
                                    ) {
                                        isSubmitting = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(2f)
                        )
                    }
                }
            },
            containerColor = MidnightBackground
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

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
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "واجب مدرسي 📝",
                            style = AppTypography.Button,
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
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "مشروع / بحث 🎯",
                            style = AppTypography.Button,
                            fontWeight = if (taskType == "TASK") FontWeight.Bold else FontWeight.Normal,
                            color = if (taskType == "TASK") TextOnAccent else TextPrimary
                        )
                    }
                }

                Text("اختر المادة الدراسية:", style = AppTypography.FieldLabel, color = TextSecondary)
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
                                style = AppTypography.Chip,
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

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MidnightSurface,
                    borderColor = CyanGlow
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.homework_study_date_title),
                            style = AppTypography.FieldLabel,
                            color = CyanAccent
                        )
                        Text(
                            text = TanweerViewModel.getFormattedArabicDate(studyDate),
                            style = AppTypography.Button,
                            color = TextPrimary
                        )
                        Text(
                            text = studyDate,
                            style = AppTypography.Hint,
                            color = TextSecondary
                        )
                        Text(
                            text = stringResource(R.string.homework_study_date_description),
                            style = AppTypography.Hint,
                            color = TextMuted
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showStudyDatePicker = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = CyanAccent)
                            ) {
                                Text(stringResource(R.string.homework_study_date_choose))
                            }
                            TextButton(
                                onClick = { studyDate = TanweerViewModel.getTodayDateString() },
                                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                            ) {
                                Text(stringResource(R.string.homework_study_date_today))
                            }
                        }
                    }
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "موعد التسليم (تاريخ تقويمي معتمد) *",
                        style = AppTypography.FieldLabel,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MidnightSurface)
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
                                tint = CyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = dueDate,
                                style = AppTypography.BodyBold,
                                color = TextPrimary
                            )
                        }
                        GlassPill(
                            text = "تحديد التاريخ 📅",
                            color = CyanAccent,
                            bgColor = CyanGlow
                        )
                    }
                }

                // IMAGE ATTACHMENT SECTION
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MidnightSurface)
                        .border(1.dp, GlassBorderSubtle, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "📷 إدراج صور الواجب / الحل (اختياري)",
                        style = AppTypography.BodyBold.copy(color = CyanAccent)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("الاستوديو", style = AppTypography.CaptionNormal)
                        }

                        OutlinedButton(
                            onClick = {
                                val hasCameraPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasCameraPermission) {
                                    cameraLauncher.launch(null)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = WarmAmber),
                            border = androidx.compose.foundation.BorderStroke(1.dp, WarmAmber.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("الكاميرا", style = AppTypography.CaptionNormal)
                        }
                    }

                    // Scanned / Selected Pages Thumbnails
                    if (scannedPages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(scannedPages) { index, page ->
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, CyanAccent, RoundedCornerShape(10.dp))
                                ) {
                                    if (page.displayBitmap != null) {
                                        Image(
                                            bitmap = page.displayBitmap.asImageBitmap(),
                                            contentDescription = "صفحة ${index + 1}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else if (page.isProcessing) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CyanAccent)
                                        }
                                    }

                                    // Remove Page Button
                                    IconButton(
                                        onClick = {
                                            ImageProcessingUtils.deleteDraftPage(
                                                context = context,
                                                sourceUri = page.uri,
                                                processedFilePath = page.processedResult?.file?.absolutePath
                                            )
                                            scannedPages = scannedPages.filter { it.id != page.id }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.7f))
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "حذف", tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
