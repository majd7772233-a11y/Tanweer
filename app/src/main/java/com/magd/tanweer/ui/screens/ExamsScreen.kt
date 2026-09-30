package com.magd.tanweer.ui.screens

import android.widget.Toast
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.ExamItem
import com.magd.tanweer.data.model.SubjectItem
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

    var isAddExamModalOpen by remember { mutableStateOf(false) }
    var selectedExamForDetail by remember { mutableStateOf<ExamItem?>(null) }
    var examToDelete by remember { mutableStateOf<ExamItem?>(null) }

    val manualCompletedMap = remember { mutableStateMapOf<String, Boolean>() }
    val todayDateStr = remember { TanweerViewModel.getTodayDateString() }

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
            // Header (Zero clutter, exactly ONE single add button)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🧪 جدول الاختبارات",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = RubyRed
                        )
                        Text(
                            text = "${exams.size} اختبارات مسجلة للشعبة",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    // Single Add Button
                    GlassButton(
                        text = "إضافة اختبار ＋",
                        color = RubyRed,
                        textColor = Color.White,
                        onClick = { isAddExamModalOpen = true },
                        modifier = Modifier.height(40.dp)
                    )
                }
            }

            if (exams.isEmpty()) {
                item {
                    EmptyStateGlass(
                        title = "جدول الاختبارات فارغ",
                        subtitle = "لم يتم تسجيل أي اختبارات حتى الآن. يمكنك إضافة اختبار جديد إلى الجدول.",
                        icon = "📋",
                        actionButtonText = "إضافة اختبار جديد",
                        onActionClick = { isAddExamModalOpen = true }
                    )
                }
            } else {
                // The Full Structured Canvas Exam Table
                item {
                    CanvasExamsTableBoard(
                        exams = exams,
                        todayDateStr = todayDateStr,
                        manualCompletedMap = manualCompletedMap,
                        onToggleDone = { examId, currentVal ->
                            manualCompletedMap[examId] = !currentVal
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

        // Add Exam Sheet
        if (isAddExamModalOpen) {
            AddExamSheet(
                gradeSubjects = gradeSubjects,
                onDismiss = { isAddExamModalOpen = false },
                onAdd = { subjId, title, chapters, notes, date ->
                    viewModel.addExam(
                        subjectId = subjId,
                        title = title,
                        requiredChapters = chapters,
                        notes = notes,
                        examDate = date
                    )
                    Toast.makeText(context, "تمت إضافة الاختبار بنجاح", Toast.LENGTH_SHORT).show()
                    isAddExamModalOpen = false
                }
            )
        }

        // Exam Detail Modal
        selectedExamForDetail?.let { exam ->
            ExamDetailModal(
                exam = exam,
                onDismiss = { selectedExamForDetail = null },
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
                            viewModel.deleteExam(exam.id)
                            examToDelete = null
                            Toast.makeText(context, "تم حذف الاختبار", Toast.LENGTH_SHORT).show()
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
    }
}

// -------------------------------------------------------------
// CANVAS EXAMS TABLE BOARD (AUTHENTIC SCHOOL TABLE WITH HEADERS ON TOP)
// -------------------------------------------------------------
@Composable
fun CanvasExamsTableBoard(
    exams: List<ExamItem>,
    todayDateStr: String,
    manualCompletedMap: Map<String, Boolean>,
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
            // 1. Table Top Column Headers (على رأس الجدول)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x2200E5FF))
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "#", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanAccent, modifier = Modifier.width(32.dp))
                Text(text = "المادة", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanAccent, modifier = Modifier.width(130.dp))
                Text(text = "اليوم والتاريخ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanAccent, modifier = Modifier.width(130.dp))
                Text(text = "الأستاذ / القاعة", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanAccent, modifier = Modifier.width(130.dp))
                Text(text = "الفصول المقررة", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanAccent, modifier = Modifier.width(150.dp))
                Text(text = "الحالة الذكية", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanAccent, textAlign = TextAlign.Center, modifier = Modifier.width(90.dp))
                Text(text = "حذف", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanAccent, textAlign = TextAlign.Center, modifier = Modifier.width(44.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Table Rows
            exams.forEachIndexed { index, exam ->
                val isDone = manualCompletedMap[exam.id] ?: isExamDatePassed(exam.examDate, todayDateStr)
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

                        // Subject
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.width(130.dp)
                        ) {
                            Text(text = exam.subjectIcon, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = exam.subjectName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDone) EmeraldGreen else TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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
                            modifier = Modifier.width(130.dp)
                        )

                        // Required Chapters
                        Text(
                            text = chapters,
                            fontSize = 11.sp,
                            color = CyanAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(150.dp)
                        )

                        // Smart Status Box
                        Box(
                            modifier = Modifier.width(90.dp),
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
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier.size(20.dp),
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
                                    val p = checkProgress / 0.5f
                                    lineTo(startX + (midX - startX) * p, startY + (midY - startY) * p)
                                } else {
                                    lineTo(midX, midY)
                                    val p = (checkProgress - 0.5f) / 0.5f
                                    lineTo(midX + (endX - midX) * p, midY + (endY - midY) * p)
                                }
                            }
                            drawPath(path = path, color = EmeraldGreen, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    } else {
                        drawRoundRect(
                            color = RubyRed.copy(alpha = 0.4f),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                            style = Stroke(width = 1.2f.dp.toPx())
                        )
                    }
                }
            }

            Text(
                text = if (isDone) "انتهى" else "مجدول",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDone) EmeraldGreen else RubyRed,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

// -------------------------------------------------------------
// ADD EXAM SHEET
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExamSheet(
    gradeSubjects: List<SubjectItem>,
    onDismiss: () -> Unit,
    onAdd: (subjectId: String, title: String, chapters: String?, notes: String?, date: String) -> Unit
) {
    var selectedSubj by remember { mutableStateOf(gradeSubjects.firstOrNull()) }
    var title by remember { mutableStateOf("") }
    var chapters by remember { mutableStateOf("") }
    var teacherNotes by remember { mutableStateOf("") }
    var examDate by remember { mutableStateOf(TanweerViewModel.getTodayDateString()) }

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
            Text(
                text = "جدولة اختبار جديد 🧪",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = RubyRed
            )

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
                        onAdd(s.id, title.trim(), chapters.trim().ifEmpty { null }, teacherNotes.trim().ifEmpty { null }, examDate.trim())
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// -------------------------------------------------------------
// EXAM DETAIL MODAL
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamDetailModal(
    exam: ExamItem,
    onDismiss: () -> Unit,
    onOpenLibraryBook: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MidnightSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = CyanAccent) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = exam.subjectIcon, fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "اختبار ${exam.subjectName}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = CyanAccent)
                        Text(text = exam.title, fontSize = 13.sp, color = TextPrimary)
                    }
                }
                Text(text = exam.examDate, fontSize = 12.sp, color = RubyRed, fontWeight = FontWeight.Bold)
            }

            if (!exam.requiredChapters.isNullOrBlank()) {
                GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "الفصول المقررة:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                        Text(text = exam.requiredChapters, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }

            if (!exam.notes.isNullOrBlank()) {
                GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "الأستاذ والقاعة:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WarmAmber)
                        Text(text = exam.notes, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }

            GlassButton(
                text = "فتح كتاب ${exam.subjectName} في المكتبة 📖",
                onClick = onOpenLibraryBook,
                modifier = Modifier.fillMaxWidth().height(46.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
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
