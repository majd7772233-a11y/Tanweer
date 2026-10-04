package com.magd.tanweer.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.Permission
import com.magd.tanweer.data.model.ScheduleSlot
import com.magd.tanweer.data.model.SchoolHierarchy
import com.magd.tanweer.data.model.SubjectItem
import com.magd.tanweer.data.model.getRoleEnum
import com.magd.tanweer.data.model.hasPermission
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*

val SCHEDULE_DAYS = listOf(
    0 to "الأحد",
    1 to "الإثنين",
    2 to "الثلاثاء",
    3 to "الأربعاء",
    4 to "الخميس"
)

val PERIOD_DEFAULT_TIMES = listOf(
    1 to ("08:00" to "08:45"),
    2 to ("08:50" to "09:35"),
    3 to ("09:40" to "10:25"),
    4 to ("10:45" to "11:30"),
    5 to ("11:35" to "12:20"),
    6 to ("12:25" to "01:10")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: TanweerViewModel
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId ?: "" }

    val allSlots by viewModel.repository.getAllScheduleSlots(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val scheduleProposals by viewModel.scheduleProposals.collectAsStateWithLifecycle()
    val scheduleVersion by viewModel.scheduleVersion.collectAsStateWithLifecycle()
    val scheduleMeta by viewModel.scheduleMeta.collectAsStateWithLifecycle()

    val gradeSubjects = remember(currentUser?.gradeId) {
        viewModel.getScheduleSubjectsForCurrentGrade()
    }

    var selectedDayForList by remember {
        val todayIdx = TanweerViewModel.getDayOfWeekIndex()
        mutableIntStateOf(if (todayIdx < 0) 0 else todayIdx)
    }
    var isFullWeekView by remember { mutableStateOf(true) }

    // Selected cell for subject selection modal: (dayOfWeek, slotOrder)
    var activeEditingCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var activeProposalCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var isCopyDayDialogOpen by remember { mutableStateOf(false) }
    var isProposalsReviewDialogOpen by remember { mutableStateOf(false) }
    var sourceDayToCopy by remember { mutableIntStateOf(0) }

    val canManageSchedule = remember(currentUser, allSlots.size, scheduleMeta) {
        (scheduleMeta?.isInitialSetup == true) ||
        (scheduleMeta?.canEditDirectly == true) ||
        (currentUser?.hasPermission(Permission.MANAGE_SCHEDULE) == true) ||
        allSlots.isEmpty()
    }
    var isDirectEditMode by remember { mutableStateOf(false) }

    val filledSlotsCount = allSlots.size
    val currentGradeName = SchoolHierarchy.getGradeName(currentUser?.gradeId ?: 10)
    val pendingProposalsCount = scheduleProposals.count { it.status == "PENDING" }

    LaunchedEffect(activeGroupId) {
        if (activeGroupId.isNotBlank()) {
            viewModel.syncSchedule(activeGroupId, isRefresh = false)
            viewModel.loadScheduleData()
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
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Clean Header (Zero clutter, no squished pills)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "📅 جدول الحصص الأسبوعي",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = CyanAccent
                        )
                        Text(
                            text = "$currentGradeName • $filledSlotsCount من 30 حصة",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Refresh/Sync from server
                        IconButton(
                            onClick = {
                                viewModel.syncSchedule(isRefresh = true)
                                Toast.makeText(context, "جاري مزامنة الجدول من الخادم 🔄", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MidnightSurface)
                                .border(1.dp, GlassBorderSubtle, RoundedCornerShape(12.dp))
                                .size(38.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث الجدول", tint = CyanAccent, modifier = Modifier.size(18.dp))
                        }

                        // View Mode Switcher
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MidnightSurface)
                                .border(1.dp, GlassBorderSubtle, RoundedCornerShape(12.dp))
                                .clickable { isFullWeekView = !isFullWeekView }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (isFullWeekView) "عرض الجدول" else "عرض القائمة",
                                    fontSize = 12.sp,
                                    color = CyanAccent,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Icon(
                                    if (isFullWeekView) Icons.Default.GridView else Icons.Default.ViewAgenda,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Permission & Mode Status Banner
            item {
                val userRole = currentUser?.getRoleEnum() ?: com.magd.tanweer.data.model.Role.STUDENT
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MidnightSurface,
                    borderColor = if (canManageSchedule && isDirectEditMode) CyanAccent else GlassBorderSubtle
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (allSlots.isEmpty()) "🌟 المساهمة في الجدول الأول"
                                           else if (canManageSchedule && isDirectEditMode) "🛠️ وضع التحرير المباشر"
                                           else if (canManageSchedule) "👁️ وضع العرض"
                                           else "👁️ وضع العرض الرسمي",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (canManageSchedule && isDirectEditMode) CyanAccent else TextPrimary
                                )
                                GlassPill(
                                    text = "${userRole.badgeIcon} ${userRole.displayNameAr}",
                                    color = CyanAccent,
                                    bgColor = CyanGlow
                                )
                            }
                            Text(
                                text = if (allSlots.isEmpty()) "بصفتك عضواً في الشعبة يمكنك إدخال الحصص الأولى مباشرة لبناء الجدول!"
                                       else if (canManageSchedule && isDirectEditMode) "اضغط على أي خانة لتعديل أو تغيير المادة مباشرة وتحديث الجدول."
                                       else if (canManageSchedule) "اضغط على زر التحرير لتعديل الجدول، أو تصفح الحصص بحرية."
                                       else "الجدول معتمد ومثبت. لتعديل أي حصة اضغط عليها لتقديم مقترح تعديل ليصوت عليه الزملاء.",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 15.sp
                            )
                        }

                        if (canManageSchedule && allSlots.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isDirectEditMode) CyanAccent else GlassSurface)
                                    .border(1.dp, if (isDirectEditMode) CyanAccent else GlassBorderSubtle, RoundedCornerShape(10.dp))
                                    .clickable { isDirectEditMode = !isDirectEditMode }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (isDirectEditMode) "إنهاء التحرير ✓" else "تفعيل التحرير ✏️",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDirectEditMode) TextOnAccent else CyanAccent
                                )
                            }
                        }
                    }
                }
            }

            // Proposals Banner if any pending proposals exist
            if (pendingProposalsCount > 0 || scheduleProposals.isNotEmpty()) {
                item {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isProposalsReviewDialogOpen = true },
                        borderColor = WarmAmber,
                        backgroundColor = WarmAmber.copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🗳️", fontSize = 18.sp)
                                Column {
                                    Text(
                                        text = "مقترحات تعديل الجدول المطروحة للزملاء",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "يوجد $pendingProposalsCount مقترح قيد التصويت والمراجعة",
                                        fontSize = 10.sp,
                                        color = WarmAmber
                                    )
                                }
                            }
                            GlassPill(text = "عرض وتصويت 👁️", color = WarmAmber, bgColor = WarmAmber.copy(alpha = 0.2f))
                        }
                    }
                }
            }

            // Timetable Content
            if (isFullWeekView) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (canManageSchedule && (isDirectEditMode || allSlots.isEmpty()))
                                "اضغط على أي خانة لاختيار المادة أو تعديلها مباشرة:"
                            else
                                "اضغط على أي حصة لمعاينتها أو تقديم مقترح تعديل عليها:",
                            fontSize = 12.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        // The Main Canvas Timetable Board
                        CanvasSchoolTimetableBoard(
                            allSlots = allSlots,
                            onCellClick = { dayIndex, slotOrder ->
                                if (canManageSchedule && (isDirectEditMode || allSlots.isEmpty())) {
                                    activeEditingCell = Pair(dayIndex, slotOrder)
                                } else {
                                    activeProposalCell = Pair(dayIndex, slotOrder)
                                }
                            }
                        )
                    }
                }
            } else {
                // Day Tabs Selector
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
                        SCHEDULE_DAYS.forEach { (dayIndex, dayName) ->
                            val isSelected = selectedDayForList == dayIndex
                            val daySlotsCount = allSlots.count { it.dayOfWeek == dayIndex }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) CyanAccent else Color.Transparent)
                                    .clickable { selectedDayForList = dayIndex }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = dayName,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) TextOnAccent else TextSecondary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = "$daySlotsCount/6",
                                        fontSize = 9.sp,
                                        color = if (isSelected) TextOnAccent.copy(alpha = 0.8f) else TextMuted
                                    )
                                }
                            }
                        }
                    }
                }

                // Day Periods Header
                item {
                    val dayName = SCHEDULE_DAYS.find { it.first == selectedDayForList }?.second ?: "اليوم"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "حصص يوم $dayName:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent
                        )
                        if (canManageSchedule) {
                            TextButton(
                                onClick = {
                                    sourceDayToCopy = selectedDayForList
                                    isCopyDayDialogOpen = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("نسخ اليوم", fontSize = 11.sp, color = WarmAmber)
                            }
                        }
                    }
                }

                // 6 Periods Cards for the selected day
                items((1..6).toList()) { periodOrder ->
                    val slot = allSlots.find { it.dayOfWeek == selectedDayForList && it.slotOrder == periodOrder }
                    val defaultTime = PERIOD_DEFAULT_TIMES.find { it.first == periodOrder }?.second ?: ("08:00" to "08:45")

                    SinglePeriodSlotCard(
                        dayOfWeek = selectedDayForList,
                        periodOrder = periodOrder,
                        slot = slot,
                        defaultTime = defaultTime,
                        canDirectEdit = canManageSchedule && (isDirectEditMode || allSlots.isEmpty()),
                        onClick = {
                            if (canManageSchedule && (isDirectEditMode || allSlots.isEmpty())) {
                                activeEditingCell = Pair(selectedDayForList, periodOrder)
                            } else {
                                activeProposalCell = Pair(selectedDayForList, periodOrder)
                            }
                        }
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // SUBJECT SELECTION BOTTOM SHEET ("اختيار مادة")
        // -------------------------------------------------------------
        activeEditingCell?.let { (dayIndex, slotOrder) ->
            val existingSlot = allSlots.find { it.dayOfWeek == dayIndex && it.slotOrder == slotOrder }
            val dayName = SCHEDULE_DAYS.find { it.first == dayIndex }?.second ?: "اليوم"
            val defaultTime = PERIOD_DEFAULT_TIMES.find { it.first == slotOrder }?.second ?: ("08:00" to "08:45")

            SubjectPickerSheet(
                dayName = dayName,
                dayIndex = dayIndex,
                slotOrder = slotOrder,
                existingSlot = existingSlot,
                defaultTime = defaultTime,
                gradeSubjects = gradeSubjects,
                onDismiss = { activeEditingCell = null },
                onSave = { subjId, subjName, subjIcon, color, startTime, endTime ->
                    viewModel.addScheduleSlot(
                        dayOfWeek = dayIndex,
                        slotOrder = slotOrder,
                        subjectId = subjId,
                        subjectName = subjName,
                        subjectIcon = subjIcon,
                        colorHex = color,
                        startTime = startTime,
                        endTime = endTime
                    )
                    Toast.makeText(context, "تم حفظ حصة $subjName", Toast.LENGTH_SHORT).show()
                    activeEditingCell = null
                },
                onDelete = {
                    viewModel.deleteScheduleSlot(dayOfWeek = dayIndex, slotOrder = slotOrder)
                    Toast.makeText(context, "تم تفريغ الخانة", Toast.LENGTH_SHORT).show()
                    activeEditingCell = null
                }
            )
        }

        // -------------------------------------------------------------
        // SCHEDULE PROPOSAL DIALOG ("اقتراح تعديل حصة")
        // -------------------------------------------------------------
        activeProposalCell?.let { (dayIndex, slotOrder) ->
            val existingSlot = allSlots.find { it.dayOfWeek == dayIndex && it.slotOrder == slotOrder }
            ScheduleProposalDialog(
                dayIndex = dayIndex,
                slotOrder = slotOrder,
                existingSlot = existingSlot,
                gradeSubjects = gradeSubjects,
                onDismiss = { activeProposalCell = null },
                onSubmit = { oldSubjectId, newSubjectId, reason ->
                    viewModel.proposeScheduleSlotChange(
                        dayOfWeek = dayIndex,
                        slotOrder = slotOrder,
                        oldSubjectId = oldSubjectId,
                        newSubjectId = newSubjectId,
                        reason = reason
                    ) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (success) {
                            activeProposalCell = null
                        }
                    }
                }
            )
        }

        // Copy Day Dialog
        if (isCopyDayDialogOpen) {
            CopyScheduleDayDialog(
                sourceDay = sourceDayToCopy,
                allSlots = allSlots,
                onDismiss = { isCopyDayDialogOpen = false },
                onCopy = { targetDay ->
                    val sourceSlots = allSlots.filter { it.dayOfWeek == sourceDayToCopy }
                    sourceSlots.forEach { s ->
                        viewModel.addScheduleSlot(
                            dayOfWeek = targetDay,
                            slotOrder = s.slotOrder,
                            subjectId = s.subjectId,
                            subjectName = s.subjectName,
                            subjectIcon = s.subjectIcon,
                            colorHex = s.colorHex,
                            startTime = s.startTime,
                            endTime = s.endTime
                        )
                    }
                    Toast.makeText(context, "تم نسخ الحصص بنجاح", Toast.LENGTH_SHORT).show()
                    isCopyDayDialogOpen = false
                }
            )
        }

        // Proposals Review Dialog
        if (isProposalsReviewDialogOpen) {
            ScheduleProposalsReviewDialog(
                proposals = scheduleProposals,
                canManageSchedule = canManageSchedule,
                onDismiss = { isProposalsReviewDialogOpen = false },
                onVote = { proposalId, voteType ->
                    viewModel.voteScheduleProposal(proposalId, voteType) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                onApprove = { proposalId ->
                    viewModel.approveScheduleProposal(proposalId) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                },
                onReject = { proposalId ->
                    viewModel.rejectScheduleProposal(proposalId) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

// -------------------------------------------------------------
// CANVAS SCHOOL TIMETABLE BOARD
// -------------------------------------------------------------
@Composable
fun CanvasSchoolTimetableBoard(
    allSlots: List<ScheduleSlot>,
    onCellClick: (dayIndex: Int, slotOrder: Int) -> Unit
) {
    val scrollState = rememberScrollState()

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MidnightSurface,
        borderColor = GlassBorderSubtle
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(6.dp)
        ) {
            // Header Row: Days Header + 6 Periods
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Days Header Cell
                Box(
                    modifier = Modifier
                        .width(70.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x2200E5FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "اليوم", fontSize = 12.sp, fontWeight = FontWeight.Black, color = CyanAccent)
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 6 Period Headers
                for (p in 1..6) {
                    val periodTitle = when (p) {
                        1 -> "الأولى ❶"
                        2 -> "الثانية ❷"
                        3 -> "الثالثة ❸"
                        4 -> "الرابعة ❹"
                        5 -> "الخامسة ❺"
                        6 -> "السادسة ❻"
                        else -> "حصة $p"
                    }
                    val timeHint = PERIOD_DEFAULT_TIMES.find { it.first == p }?.second?.first ?: ""

                    Box(
                        modifier = Modifier
                            .width(96.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(GlassSurfaceLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = periodTitle, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(text = timeHint, fontSize = 9.sp, color = TextMuted)
                        }
                    }

                    if (p == 3) {
                        // Break Separator Column
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🥪", fontSize = 9.sp)
                        }
                    } else if (p < 6) {
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 5 Day Rows (Sunday to Thursday)
            SCHEDULE_DAYS.forEach { (dayIndex, dayName) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Day Badge
                    Box(
                        modifier = Modifier
                            .width(70.dp)
                            .height(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GlassSurface)
                            .border(1.dp, CyanGlow, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = dayName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            val dayCount = allSlots.count { it.dayOfWeek == dayIndex }
                            Text(text = "$dayCount/6", fontSize = 9.sp, color = if (dayCount == 6) EmeraldGreen else TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 6 Period Cells for this Day
                    for (p in 1..6) {
                        val slot = allSlots.find { it.dayOfWeek == dayIndex && it.slotOrder == p }

                        CanvasTimetableCell(
                            slot = slot,
                            dayIndex = dayIndex,
                            slotOrder = p,
                            onClick = { onCellClick(dayIndex, p) },
                            modifier = Modifier
                                .width(96.dp)
                                .height(64.dp)
                        )

                        if (p == 3) {
                            // Break Separator Vertical Line
                            Box(
                                modifier = Modifier
                                    .width(16.dp)
                                    .height(64.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawLine(
                                        color = WarmAmber.copy(alpha = 0.4f),
                                        start = Offset(size.width / 2f, 0f),
                                        end = Offset(size.width / 2f, size.height),
                                        strokeWidth = 1.5f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                                    )
                                }
                            }
                        } else if (p < 6) {
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CanvasTimetableCell(
    slot: ScheduleSlot?,
    dayIndex: Int,
    slotOrder: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOccupied = slot != null
    val cellColor = if (isOccupied) {
        val rawHex = slot!!.colorHex ?: "#00E5FF"
        try {
            Color(android.graphics.Color.parseColor(rawHex))
        } catch (_: Exception) {
            CyanAccent
        }
    } else {
        Color.Transparent
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            if (isOccupied) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(cellColor.copy(alpha = 0.28f), cellColor.copy(alpha = 0.12f))
                    ),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                )
                drawRoundRect(
                    color = cellColor.copy(alpha = 0.7f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                    style = Stroke(width = 1.2f.dp.toPx())
                )
            } else {
                drawRoundRect(
                    color = Color(0x3300E5FF),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    )
                )
            }
        }

        if (isOccupied) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = slot!!.subjectIcon, fontSize = 16.sp)
                Text(
                    text = slot.subjectName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${slot.startTime ?: ""} - ${slot.endTime ?: ""}",
                    fontSize = 8.sp,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "＋", fontSize = 15.sp, color = CyanAccent.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                Text(text = "فارغة", fontSize = 9.sp, color = TextMuted)
            }
        }
    }
}

@Composable
fun SinglePeriodSlotCard(
    dayOfWeek: Int,
    periodOrder: Int,
    slot: ScheduleSlot?,
    defaultTime: Pair<String, String>,
    canDirectEdit: Boolean = false,
    onClick: () -> Unit
) {
    val isOccupied = slot != null
    val periodTitle = when (periodOrder) {
        1 -> "الحصة الأولى"
        2 -> "الحصة الثانية"
        3 -> "الحصة الثالثة"
        4 -> "الحصة الرابعة"
        5 -> "الحصة الخامسة"
        6 -> "الحصة السادسة"
        else -> "الحصة $periodOrder"
    }

    val cellColor = if (isOccupied) {
        val rawHex = slot!!.colorHex ?: "#00E5FF"
        try { Color(android.graphics.Color.parseColor(rawHex)) } catch (_: Exception) { CyanAccent }
    } else {
        CyanAccent
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        borderColor = if (isOccupied) cellColor.copy(alpha = 0.5f) else GlassBorderSubtle,
        backgroundColor = if (isOccupied) GlassSurface else MidnightSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isOccupied) cellColor.copy(alpha = 0.25f) else GlassSurfaceLight)
                        .border(1.dp, if (isOccupied) cellColor else GlassBorderSubtle, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = slot?.subjectIcon ?: "＋", fontSize = 18.sp)
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = periodTitle,
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${slot?.startTime ?: defaultTime.first} - ${slot?.endTime ?: defaultTime.second}",
                            fontSize = 11.sp,
                            color = CyanAccent
                        )
                    }
                    Text(
                        text = slot?.subjectName ?: if (canDirectEdit) "خانة فارغة (اضغط لإضافة مادة)" else "خانة غير مسجلة (اضغط لاقتراح مادة)",
                        fontSize = 14.sp,
                        fontWeight = if (isOccupied) FontWeight.Bold else FontWeight.Normal,
                        color = if (isOccupied) TextPrimary else TextMuted
                    )
                }
            }

            if (canDirectEdit) {
                if (isOccupied) {
                    GlassPill(text = "تعديل ✏️", color = CyanAccent, bgColor = CyanGlow)
                } else {
                    GlassPill(text = "إضافة ＋", color = CyanAccent, bgColor = CyanGlow)
                }
            } else {
                if (isOccupied) {
                    GlassPill(text = "اقتراح تعديل 💡", color = WarmAmber, bgColor = WarmAmber.copy(alpha = 0.2f))
                } else {
                    GlassPill(text = "اقتراح إضافة 💡", color = CyanAccent, bgColor = CyanGlow)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SUBJECT PICKER BOTTOM SHEET ("اختيار مادة")
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectPickerSheet(
    dayName: String,
    dayIndex: Int,
    slotOrder: Int,
    existingSlot: ScheduleSlot?,
    defaultTime: Pair<String, String>,
    gradeSubjects: List<SubjectItem>,
    onDismiss: () -> Unit,
    onSave: (subjId: String, subjName: String, subjIcon: String, color: String?, start: String, end: String) -> Unit,
    onDelete: () -> Unit
) {
    var selectedSubject by remember {
        mutableStateOf(
            if (existingSlot != null) {
                gradeSubjects.find { it.id == existingSlot.subjectId } ?: gradeSubjects.firstOrNull()
            } else {
                gradeSubjects.firstOrNull()
            }
        )
    }

    var startTime by remember { mutableStateOf(existingSlot?.startTime ?: defaultTime.first) }
    var endTime by remember { mutableStateOf(existingSlot?.endTime ?: defaultTime.second) }

    val periodTitle = when (slotOrder) {
        1 -> "الحصة الأولى"
        2 -> "الحصة الثانية"
        3 -> "الحصة الثالثة"
        4 -> "الحصة الرابعة"
        5 -> "الحصة الخامسة"
        6 -> "الحصة السادسة"
        else -> "الحصة $slotOrder"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MidnightSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = CyanAccent) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Clean Header: "اختيار مادة"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "اختيار مادة 📚",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = CyanAccent
                    )
                    Text(
                        text = "$dayName • $periodTitle ($startTime - $endTime)",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            // Grid of Grade Subjects (No Computer, With PE)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
            ) {
                items(gradeSubjects, key = { it.id }) { subj ->
                    val isSelected = selectedSubject?.id == subj.id
                    val color = try {
                        Color(android.graphics.Color.parseColor(subj.colorHex))
                    } catch (_: Exception) {
                        CyanAccent
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) color.copy(alpha = 0.25f) else GlassSurface)
                            .border(1.2.dp, if (isSelected) color else GlassBorderSubtle, RoundedCornerShape(12.dp))
                            .clickable { selectedSubject = subj }
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = subj.icon, fontSize = 18.sp)
                            Text(
                                text = subj.name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) color else TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Times
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = "وقت البدء",
                    placeholder = "08:00",
                    modifier = Modifier.weight(1f)
                )
                GlassTextField(
                    value = endTime,
                    onValueChange = { endTime = it },
                    label = "وقت الانتهاء",
                    placeholder = "08:45",
                    modifier = Modifier.weight(1f)
                )
            }

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (existingSlot != null) {
                    GlassOutlinedButton(
                        text = "تفريغ 🗑️",
                        onClick = onDelete,
                        modifier = Modifier.weight(1f)
                    )
                }

                GlassButton(
                    text = if (existingSlot != null) "تحديث الحصة" else "تثبيت في الجدول",
                    enabled = selectedSubject != null,
                    onClick = {
                        selectedSubject?.let { s ->
                            onSave(s.id, s.name, s.icon, s.colorHex, startTime, endTime)
                        }
                    },
                    modifier = Modifier.weight(2f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// -------------------------------------------------------------
// SCHEDULE PROPOSAL DIALOG ("اقتراح تعديل حصة")
// -------------------------------------------------------------
@Composable
fun ScheduleProposalDialog(
    dayIndex: Int,
    slotOrder: Int,
    existingSlot: ScheduleSlot?,
    gradeSubjects: List<SubjectItem>,
    onDismiss: () -> Unit,
    onSubmit: (oldSubjectId: String?, newSubjectId: String, reason: String) -> Unit
) {
    val dayName = SCHEDULE_DAYS.find { it.first == dayIndex }?.second ?: "اليوم"
    var selectedSubject by remember { mutableStateOf<SubjectItem?>(null) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("💡", fontSize = 20.sp)
                Column {
                    Text(
                        text = "اقتراح تعديل حصة",
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "$dayName — الحصة $slotOrder",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Current slot info
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MidnightBackground)
                        .border(1.dp, GlassBorderSubtle, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("الحصة الحالية:", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = existingSlot?.let { "${it.subjectIcon} ${it.subjectName}" } ?: "فارغة (غير محددة)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                // New Subject selection
                Text("المادة المقترحة:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(gradeSubjects) { subj ->
                        val isSelected = selectedSubject?.id == subj.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) CyanGlow else MidnightBackground)
                                .border(1.dp, if (isSelected) CyanAccent else GlassBorderSubtle, RoundedCornerShape(8.dp))
                                .clickable { selectedSubject = subj }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(subj.icon, fontSize = 16.sp)
                                Text(
                                    subj.name,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) CyanAccent else TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Reason input: لماذا؟
                Text("لماذا؟ (سبب الاقتراح):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    placeholder = { Text("مثلاً: تغيير المدرس أو تبديل الحصة...", fontSize = 11.sp, color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = GlassBorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    maxLines = 3
                )

                Text(
                    text = "ℹ️ سيُطرح الاقتراح لتصويت الشعبة ويُعتمد رسمياً بعد الموافقة.",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            GlassButton(
                text = "إرسال الاقتراح",
                enabled = selectedSubject != null && reason.isNotBlank(),
                onClick = {
                    selectedSubject?.let { subj ->
                        onSubmit(existingSlot?.subjectId, subj.id, reason.trim())
                    }
                }
            )
        },
        dismissButton = {
            GlassOutlinedButton(text = "إلغاء", onClick = onDismiss)
        },
        containerColor = MidnightSurface,
        shape = RoundedCornerShape(18.dp)
    )
}

// -------------------------------------------------------------
// COPY SCHEDULE DAY DIALOG
// -------------------------------------------------------------
@Composable
fun CopyScheduleDayDialog(
    sourceDay: Int,
    allSlots: List<ScheduleSlot>,
    onDismiss: () -> Unit,
    onCopy: (targetDay: Int) -> Unit
) {
    val sourceDayName = SCHEDULE_DAYS.find { it.first == sourceDay }?.second ?: "اليوم"
    var targetDay by remember { mutableIntStateOf(if (sourceDay == 0) 1 else 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "نسخ حصص $sourceDayName 📋",
                color = CyanAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "اختر اليوم الهدف لنسخ الحصص إليه:",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                SCHEDULE_DAYS.filter { it.first != sourceDay }.forEach { (dIdx, dName) ->
                    val isSelected = targetDay == dIdx
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) CyanGlow else MidnightSurface)
                            .border(1.dp, if (isSelected) CyanAccent else GlassBorderSubtle, RoundedCornerShape(10.dp))
                            .clickable { targetDay = dIdx }
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "نسخ إلى يوم $dName",
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) CyanAccent else TextPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            GlassButton(text = "تأكيد النسخ", onClick = { onCopy(targetDay) })
        },
        dismissButton = {
            GlassOutlinedButton(text = "إلغاء", onClick = onDismiss)
        },
        containerColor = MidnightSurface,
        shape = RoundedCornerShape(18.dp)
    )
}

// -------------------------------------------------------------
// SCHEDULE PROPOSALS REVIEW & VOTING DIALOG
// -------------------------------------------------------------
@Composable
fun ScheduleProposalsReviewDialog(
    proposals: List<com.magd.tanweer.data.model.ScheduleProposalItem>,
    canManageSchedule: Boolean,
    onDismiss: () -> Unit,
    onVote: (proposalId: String, voteType: String) -> Unit,
    onApprove: (proposalId: String) -> Unit,
    onReject: (proposalId: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🗳️", fontSize = 22.sp)
                Column {
                    Text(
                        text = "مقترحات تعديل جدول الشعبة",
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "${proposals.size} مقترح متاح للمراجعة والتصويت",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            if (proposals.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✨", fontSize = 28.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "لا توجد مقترحات معلقة حالياً",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "الجدول مستقر ومتفق عليه بين جميع أعضاء الشعبة.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(proposals, key = { it.id }) { prop ->
                        val dayName = SCHEDULE_DAYS.find { it.first == prop.dayOfWeek }?.second ?: "اليوم"
                        val isPending = prop.status == "PENDING"

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MidnightBackground)
                                .border(1.dp, GlassBorderSubtle, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$dayName — الحصة ${prop.slotOrder}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyanAccent
                                    )
                                    val statusColor = when (prop.status) {
                                        "ACCEPTED" -> EmeraldGreen
                                        "REJECTED" -> RubyRed
                                        else -> WarmAmber
                                    }
                                    val statusText = when (prop.status) {
                                        "ACCEPTED" -> "معتمد ومطبق ✅"
                                        "REJECTED" -> "مرفوض ❌"
                                        else -> "قيد التصويت ⏳"
                                    }
                                    GlassPill(text = statusText, color = statusColor, bgColor = statusColor.copy(alpha = 0.15f))
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("التعديل المقترح:", fontSize = 11.sp, color = TextMuted)
                                    Text(
                                        text = "${prop.oldSubjectName ?: "فارغة"} ➔ ${prop.newSubjectIcon} ${prop.newSubjectName ?: prop.newSubjectId}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }

                                Text(
                                    text = "السبب: ${prop.reason}",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "المقترح من: ${prop.proposerName}",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("👍 ${prop.votesFor}", fontSize = 11.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                                        Text("👎 ${prop.votesAgainst}", fontSize = 11.sp, color = RubyRed, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (isPending) {
                                    HorizontalDivider(color = GlassBorderSubtle, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Voting buttons for all members
                                        OutlinedButton(
                                            onClick = { onVote(prop.id, "FOR") },
                                            modifier = Modifier.weight(1f).height(32.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, if (prop.myVote == "FOR") EmeraldGreen else GlassBorderSubtle)
                                        ) {
                                            Text(
                                                text = if (prop.myVote == "FOR") "موافق ✓" else "أوافق 👍",
                                                fontSize = 10.sp,
                                                color = if (prop.myVote == "FOR") EmeraldGreen else TextSecondary
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = { onVote(prop.id, "AGAINST") },
                                            modifier = Modifier.weight(1f).height(32.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, if (prop.myVote == "AGAINST") RubyRed else GlassBorderSubtle)
                                        ) {
                                            Text(
                                                text = if (prop.myVote == "AGAINST") "معارض ✕" else "أعارض 👎",
                                                fontSize = 10.sp,
                                                color = if (prop.myVote == "AGAINST") RubyRed else TextSecondary
                                            )
                                        }

                                        // Manager Decision buttons
                                        if (canManageSchedule) {
                                            Button(
                                                onClick = { onApprove(prop.id) },
                                                modifier = Modifier.weight(1.2f).height(32.dp),
                                                contentPadding = PaddingValues(0.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                                            ) {
                                                Text("اعتماد ✅", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                            Button(
                                                onClick = { onReject(prop.id) },
                                                modifier = Modifier.weight(0.9f).height(32.dp),
                                                contentPadding = PaddingValues(0.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = RubyRed)
                                            ) {
                                                Text("رفض ❌", fontSize = 10.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            GlassButton(text = "إغلاق", onClick = onDismiss)
        },
        containerColor = MidnightSurface,
        shape = RoundedCornerShape(18.dp)
    )
}
