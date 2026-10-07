package com.magd.tanweer.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.*
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*

@Composable
fun ClassKnowledgeBaseScreen(
    viewModel: TanweerViewModel
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId ?: "" }

    val selectedSubjectId by viewModel.selectedKnowledgeBaseSubjectId.collectAsStateWithLifecycle()

    val gradeSubjects = remember(currentUser?.gradeId) {
        SchoolHierarchy.getSubjectsForGrade(currentUser?.gradeId ?: 11)
    }

    val currentSubject = remember(selectedSubjectId, gradeSubjects) {
        gradeSubjects.find { it.id == selectedSubjectId } ?: gradeSubjects.firstOrNull() ?: DefaultSubjects.first()
    }

    // Load data for active subject
    val contents by viewModel.repository.getContentsBySubject(activeGroupId, currentSubject.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val subjectContents = remember(contents, currentSubject) {
        contents.filter { it.subjectId == currentSubject.id }
    }

    val homeworks by viewModel.repository.getHomeworks(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val subjectHomeworks = remember(homeworks, currentSubject) {
        homeworks.filter { it.subjectId == currentSubject.id }
    }

    val exams by viewModel.repository.getExams(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val subjectExams = remember(exams, currentSubject) {
        exams.filter { it.subjectId == currentSubject.id }
    }

    val issues by viewModel.repository.getIssues(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val subjectIssues = remember(issues, currentSubject) {
        issues.filter { it.subjectId == currentSubject.id }
    }

    val allBooks by viewModel.repository.getBooks(currentUser?.gradeId ?: 11)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val subjectBook = remember(allBooks, currentSubject) {
        allBooks.find { it.subjectId == currentSubject.id }
    }

    var activeTab by remember { mutableIntStateOf(0) } // 0: الدروس, 1: الواجبات, 2: الاختبارات, 3: الأسئلة, 4: الكتاب
    var correctionDialogContent by remember { mutableStateOf<ContentItem?>(null) }

    val themeColor = try {
        Color(android.graphics.Color.parseColor(currentSubject.colorHex))
    } catch (_: Exception) {
        CyanAccent
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
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
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "بنك معرفة المادة ومستودع الدروس 📚",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${currentUser?.gradeName ?: ""} • ${currentSubject.name}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // Horizontal Subject Selector Row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(gradeSubjects, key = { it.id }) { subj ->
                val isSelected = subj.id == currentSubject.id
                val subjColor = try {
                    Color(android.graphics.Color.parseColor(subj.colorHex))
                } catch (_: Exception) {
                    CyanAccent
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) subjColor.copy(alpha = 0.25f) else GlassSurface)
                        .border(
                            1.dp,
                            if (isSelected) subjColor else GlassBorderSubtle,
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { viewModel.openSubjectKnowledgeBase(subj.id) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(subj.icon, fontSize = 16.sp)
                        Text(
                            text = subj.name,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) subjColor else TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Hero Subject Card
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            backgroundColor = themeColor.copy(alpha = 0.12f),
            borderColor = themeColor.copy(alpha = 0.4f)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(themeColor.copy(alpha = 0.25f))
                                .border(1.dp, themeColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(currentSubject.icon, fontSize = 22.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = currentSubject.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                            Text(
                                text = "شعبة المادة الرسمية • ${currentUser?.gradeName ?: ""}",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    if (subjectBook != null) {
                        Button(
                            onClick = { viewModel.openBookInPdfReader(subjectBook) },
                            colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("الكتاب", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stats Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    KnowledgeStatItem("📚 الدروس", "${subjectContents.size}", themeColor)
                    KnowledgeStatItem("📝 الواجبات", "${subjectHomeworks.size}", WarmAmber)
                    KnowledgeStatItem("🔴 الاختبارات", "${subjectExams.size}", RubyRed)
                    KnowledgeStatItem("❓ الأسئلة", "${subjectIssues.size}", EmeraldGreen)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tabs Row
        ScrollableTabRow(
            selectedTabIndex = activeTab,
            containerColor = Color.Transparent,
            contentColor = CyanAccent,
            edgePadding = 16.dp,
            divider = {}
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("📅 الدروس (${subjectContents.size})", fontSize = 13.sp) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("📝 الواجبات (${subjectHomeworks.size})", fontSize = 13.sp) }
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                text = { Text("🔴 الاختبارات (${subjectExams.size})", fontSize = 13.sp) }
            )
            Tab(
                selected = activeTab == 3,
                onClick = { activeTab = 3 },
                text = { Text("❓ الأسئلة (${subjectIssues.size})", fontSize = 13.sp) }
            )
            Tab(
                selected = activeTab == 4,
                onClick = { activeTab = 4 },
                text = { Text("📖 الكتاب المدرسي", fontSize = 13.sp) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tab Content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (activeTab) {
                0 -> {
                    if (subjectContents.isEmpty()) {
                        item { EmptyStateKnowledge("لا توجد دروس موثقة في ${currentSubject.name} حتى الآن") }
                    } else {
                        items(subjectContents, key = { it.id }) { item ->
                            KnowledgeContentCard(
                                item = item,
                                onRequestCorrection = { correctionDialogContent = item }
                            )
                        }
                    }
                }
                1 -> {
                    if (subjectHomeworks.isEmpty()) {
                        item { EmptyStateKnowledge("لا توجد واجبات مسجلة لهذه المادة") }
                    } else {
                        items(subjectHomeworks, key = { it.id }) { hw ->
                            KnowledgeHomeworkCard(
                                homework = hw,
                                onOpenPdfPage = { page ->
                                    if (subjectBook != null) {
                                        viewModel.openBookInPdfReader(subjectBook, targetPage = page)
                                    } else {
                                        Toast.makeText(context, "لم يتم العثور على كتاب المادة", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
                2 -> {
                    if (subjectExams.isEmpty()) {
                        item { EmptyStateKnowledge("لا توجد اختبارات محددة لهذه المادة") }
                    } else {
                        items(subjectExams, key = { it.id }) { exam ->
                            KnowledgeExamCard(exam)
                        }
                    }
                }
                3 -> {
                    if (subjectIssues.isEmpty()) {
                        item { EmptyStateKnowledge("لا توجد استفسارات مفتوحة في هذه المادة") }
                    } else {
                        items(subjectIssues, key = { it.id }) { issue ->
                            KnowledgeIssueCard(issue)
                        }
                    }
                }
                4 -> {
                    item {
                        if (subjectBook != null) {
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = GlassSurface
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = subjectBook.title,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "الطبعة الرسمية • ${subjectBook.fileSizeMb} ميغابايت",
                                                fontSize = 12.sp,
                                                color = TextSecondary
                                            )
                                        }
                                        Button(
                                            onClick = { viewModel.openBookInPdfReader(subjectBook) },
                                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                                        ) {
                                            Text("فتح القارئ 📖", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        } else {
                            EmptyStateKnowledge("لم يتم العثور على ملف الكتاب المنهجي لهذه المادة في المكتبة")
                        }
                    }
                }
            }
        }
    }

    // Correction Modal
    if (correctionDialogContent != null) {
        val item = correctionDialogContent!!
        CorrectionRequestModal(
            contentItem = item,
            onDismiss = { correctionDialogContent = null },
            onSubmit = { field, orig, prop, reason ->
                viewModel.submitCorrection(item.id, activeGroupId, field, orig, prop, reason) { ok, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (ok) correctionDialogContent = null
                }
            }
        )
    }
}

@Composable
fun KnowledgeStatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = color)
        Text(label, fontSize = 11.sp, color = TextMuted)
    }
}

@Composable
fun KnowledgeContentCard(
    item: ContentItem,
    onRequestCorrection: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = GlassSurface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🗓️ ${item.studyDate} • ${item.title}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanAccent
                )
                TextButton(onClick = onRequestCorrection) {
                    Text("❌ تصحيح", fontSize = 11.sp, color = RubyRed)
                }
            }
            if (!item.description.isNull_or_empty()) {
                Text(
                    text = item.description!!,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (item.media.isNotEmpty()) {
                Text(
                    text = "📷 يحتوي ${item.media.size} صفحات صور موثقة",
                    fontSize = 11.sp,
                    color = EmeraldGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
fun KnowledgeHomeworkCard(
    homework: HomeworkItem,
    onOpenPdfPage: (Int) -> Unit
) {
    val pageNum = remember(homework.pageNumbers, homework.details) {
        val src = homework.pageNumbers ?: homework.details ?: ""
        val digits = src.filter { it.isDigit() }
        digits.toIntOrNull()
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = GlassSurface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = homework.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                GlassPill(
                    text = "التسليم: ${homework.dueDate}",
                    color = WarmAmber,
                    bgColor = WarmAmber.copy(alpha = 0.12f)
                )
            }

            if (!homework.details.isNull_or_empty()) {
                Text(
                    text = homework.details!!,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (pageNum != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onOpenPdfPage((pageNum - 1).coerceAtLeast(0)) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("📖 فتح صفحة $pageNum مباشرة في الكتاب", fontSize = 11.sp, color = CyanAccent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun KnowledgeExamCard(exam: ExamItem) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = GlassSurface,
        borderColor = RubyRed.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(exam.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RubyRed)
                Text("الموعد: ${exam.examDate}", fontSize = 12.sp, color = TextMuted)
            }
            if (!exam.requiredChapters.isNull_or_empty()) {
                Text("📚 المطلوب: ${exam.requiredChapters!!}", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun KnowledgeIssueCard(issue: IssueItem) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = GlassSurface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(issue.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            if (!issue.description.isNull_or_empty()) {
                Text(issue.description!!, fontSize = 12.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("💬 ${issue.commentsCount} إجابات", fontSize = 11.sp, color = CyanAccent)
                Text("بواسطة: ${issue.authorName}", fontSize = 11.sp, color = TextMuted)
            }
        }
    }
}

@Composable
fun EmptyStateKnowledge(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, fontSize = 13.sp, color = TextMuted)
    }
}

@Composable
fun CorrectionRequestModal(
    contentItem: ContentItem,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String?) -> Unit
) {
    var fieldName by remember { mutableStateOf("TITLE") }
    var proposedValue by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اقتراح تصحيح للدرس ✍️", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("عنوان الدرس: ${contentItem.title}", fontSize = 12.sp, color = TextSecondary)

                Text("ما هو الحقل المراد تصويبه؟", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = fieldName == "TITLE",
                        onClick = { fieldName = "TITLE" },
                        label = { Text("العنوان", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = fieldName == "DESCRIPTION",
                        onClick = { fieldName = "DESCRIPTION" },
                        label = { Text("الوصف والشرح", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = fieldName == "STUDY_DATE",
                        onClick = { fieldName = "STUDY_DATE" },
                        label = { Text("التاريخ", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = fieldName == "PAGE_NUMBERS",
                        onClick = { fieldName = "PAGE_NUMBERS" },
                        label = { Text("الصفحات", fontSize = 11.sp) }
                    )
                }

                OutlinedTextField(
                    value = proposedValue,
                    onValueChange = { proposedValue = it },
                    label = { Text("القيمة الصحيحة المقترحة") },
                    placeholder = {
                        Text(
                            when (fieldName) {
                                "TITLE" -> "مثال: مراجعة الوحدة الأولى"
                                "DESCRIPTION" -> "اكتب الشرح والتفاصيل الصحيحة..."
                                "STUDY_DATE" -> "YYYY-MM-DD"
                                else -> "مثال: ص 15-20"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("سبب ومبرر التصحيح (اختياري)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (proposedValue.isNotBlank()) {
                        val orig = when (fieldName) {
                            "TITLE" -> contentItem.title
                            "DESCRIPTION" -> contentItem.description ?: ""
                            "STUDY_DATE" -> contentItem.studyDate
                            else -> ""
                        }
                        onSubmit(fieldName, orig, proposedValue.trim(), reason.trim().ifBlank { null })
                    }
                },
                enabled = proposedValue.isNotBlank()
            ) {
                Text("إرسال الاقتراح ✨")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

private fun String?.isNull_or_empty(): Boolean = this == null || this.trim().isEmpty()
