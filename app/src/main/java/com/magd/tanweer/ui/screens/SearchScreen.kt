package com.magd.tanweer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.*
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: TanweerViewModel
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId ?: "" }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, CONTENTS, HOMEWORK, EXAMS, ISSUES, BOOKS

    val contents by viewModel.repository.getDayContents(activeGroupId, "")
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val homeworks by viewModel.repository.getHomeworks(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val exams by viewModel.repository.getExams(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val issues by viewModel.repository.getIssues(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val books by viewModel.repository.getBooks(currentUser?.gradeId ?: 10)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val query = searchQuery.trim()

    val filteredContents = remember(contents, query, selectedFilter) {
        if (selectedFilter != "ALL" && selectedFilter != "CONTENTS") emptyList()
        else if (query.isBlank()) contents.take(5)
        else contents.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.subjectName.contains(query, ignoreCase = true) ||
            (it.description?.contains(query, ignoreCase = true) == true)
        }
    }

    val filteredHomeworks = remember(homeworks, query, selectedFilter) {
        if (selectedFilter != "ALL" && selectedFilter != "HOMEWORK") emptyList()
        else if (query.isBlank()) homeworks.take(5)
        else homeworks.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.subjectName.contains(query, ignoreCase = true) ||
            (it.details?.contains(query, ignoreCase = true) == true)
        }
    }

    val filteredExams = remember(exams, query, selectedFilter) {
        if (selectedFilter != "ALL" && selectedFilter != "EXAMS") emptyList()
        else if (query.isBlank()) exams.take(5)
        else exams.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.subjectName.contains(query, ignoreCase = true) ||
            (it.requiredChapters?.contains(query, ignoreCase = true) == true) ||
            (it.notes?.contains(query, ignoreCase = true) == true)
        }
    }

    val filteredIssues = remember(issues, query, selectedFilter) {
        if (selectedFilter != "ALL" && selectedFilter != "ISSUES") emptyList()
        else if (query.isBlank()) issues.take(5)
        else issues.filter {
            it.title.contains(query, ignoreCase = true) ||
            (it.subjectName?.contains(query, ignoreCase = true) == true) ||
            (it.description?.contains(query, ignoreCase = true) == true)
        }
    }

    val filteredBooks = remember(books, query, selectedFilter) {
        if (selectedFilter != "ALL" && selectedFilter != "BOOKS") emptyList()
        else if (query.isBlank()) books.take(5)
        else books.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.subjectName.contains(query, ignoreCase = true)
        }
    }

    val totalResultsCount = filteredContents.size + filteredHomeworks.size + filteredExams.size + filteredIssues.size + filteredBooks.size

    BackHandler {
        viewModel.setSubScreen(SubScreen.NONE)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Search Input Bar
        item {
            GlassTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "البحث الفوري في المنظومة",
                placeholder = "ابحث عن درس، واجب، اختبار، أو كتاب مدرسي...",
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Default.Search
            )
        }

        // Category Filter Chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "الكل 🌐",
                    "CONTENTS" to "الدروس 📚",
                    "HOMEWORK" to "واجبات 📋",
                    "EXAMS" to "اختبارات 🎯",
                    "ISSUES" to "أسئلة ❓",
                    "BOOKS" to "كتب 📕"
                ).forEach { (key, label) ->
                    val isSelected = selectedFilter == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) CyanAccent else GlassSurface)
                            .clickable { selectedFilter = key }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) TextOnAccent else TextSecondary
                        )
                    }
                }
            }
        }

        // Results Status Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (query.isBlank()) "استعراض المحتوى الأخير" else "نتائج البحث عن \"$query\"",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Text(
                    text = "$totalResultsCount نتيجة",
                    fontSize = 12.sp,
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Lessons Results
        if (filteredContents.isNotEmpty()) {
            item {
                Text(text = "📚 الدروس والتوثيق", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            items(filteredContents) { content ->
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openSubjectKnowledgeBase(content.subjectId) },
                    backgroundColor = GlassSurface
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            GlassPill(text = content.subjectName, color = CyanAccent, bgColor = CyanGlow)
                            Text(text = "التاريخ: ${content.studyDate}", fontSize = 11.sp, color = TextMuted)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = content.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        if (!content.description.isNullOrBlank()) {
                            Text(text = content.description, fontSize = 12.sp, color = TextSecondary, maxLines = 2)
                        }
                    }
                }
            }
        }

        // Homework Results
        if (filteredHomeworks.isNotEmpty()) {
            item {
                Text(text = "📋 الواجبات والمهام", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            items(filteredHomeworks) { hw ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurface
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            GlassPill(text = hw.subjectName, color = CyanAccent, bgColor = CyanGlow)
                            Text(text = "تاريخ: ${hw.studyDate}", fontSize = 11.sp, color = TextMuted)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = hw.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        if (!hw.details.isNullOrBlank()) {
                            Text(text = hw.details, fontSize = 12.sp, color = TextSecondary, maxLines = 2)
                        }
                    }
                }
            }
        }

        // Exam Results
        if (filteredExams.isNotEmpty()) {
            item {
                Text(text = "🎯 الاختبارات المدرسية", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            items(filteredExams) { exam ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurface
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            GlassPill(text = exam.subjectName, color = RubyRed, bgColor = RubyRed.copy(alpha = 0.15f))
                            Text(text = "موعد: ${exam.examDate}", fontSize = 11.sp, color = WarmAmber, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = exam.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        if (!exam.requiredChapters.isNullOrBlank()) {
                            Text(text = "المقرر: ${exam.requiredChapters}", fontSize = 12.sp, color = TextSecondary, maxLines = 2)
                        }
                    }
                }
            }
        }

        // Books Results
        if (filteredBooks.isNotEmpty()) {
            item {
                Text(text = "📚 الكتب الدراسية", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            items(filteredBooks) { book ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurface
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = book.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(text = "${book.subjectName} • ${book.edition}", fontSize = 12.sp, color = TextSecondary)
                        }
                        GlassOutlinedButton(
                            text = "مطالعة 📖",
                            onClick = { viewModel.openBookInPdfReader(book) }
                        )
                    }
                }
            }
        }

        if (totalResultsCount == 0) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🔍", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (query.isBlank()) "اكتب كلمة للبحث في المنظومة" else "لم يتم العثور على نتائج تطابق \"$query\"",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
