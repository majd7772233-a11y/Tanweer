package com.magd.tanweer.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.*
import com.magd.tanweer.R
import com.magd.tanweer.ui.GroupSyncSource
import com.magd.tanweer.ui.NavigationTab
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*
import com.magd.tanweer.util.IssueStatus
import com.magd.tanweer.util.IssueStatusPolicy
import com.magd.tanweer.util.ArabicSearchEngine
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: TanweerViewModel
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId ?: "" }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, CONTENTS, HOMEWORK, EXAMS, EVENTS, ISSUES, CHAT, BOOKS

    // Recent search history & suggestions
    var recentSearches by remember { mutableStateOf(ArabicSearchEngine.getRecentSearches(context)) }

    val groupSyncScopeIds by viewModel.groupSyncScopeIds.collectAsStateWithLifecycle()
    val sectionContentsSyncState by viewModel.sectionContentsSyncState.collectAsStateWithLifecycle()
    val homeworkSyncState by viewModel.homeworkSyncState.collectAsStateWithLifecycle()
    val examsSyncState by viewModel.examsSyncState.collectAsStateWithLifecycle()
    val eventsSyncState by viewModel.eventsSyncState.collectAsStateWithLifecycle()
    val issuesSyncState by viewModel.issuesSyncState.collectAsStateWithLifecycle()
    val searchChatHistorySyncState by viewModel.searchChatHistorySyncState.collectAsStateWithLifecycle()
    val searchChatHistorySyncGroupId by viewModel.searchChatHistorySyncGroupId.collectAsStateWithLifecycle()
    val librarySyncState by viewModel.librarySyncState.collectAsStateWithLifecycle()
    val librarySyncGradeId by viewModel.librarySyncGradeId.collectAsStateWithLifecycle()
    val searchGradeId = currentUser?.gradeId ?: 10

    val scopedSearchContentsSync = syncStateForScope(sectionContentsSyncState, groupSyncScopeIds[GroupSyncSource.CONTENTS], activeGroupId)
    val scopedSearchHomeworkSync = syncStateForScope(homeworkSyncState, groupSyncScopeIds[GroupSyncSource.HOMEWORKS], activeGroupId)
    val scopedSearchExamsSync = syncStateForScope(examsSyncState, groupSyncScopeIds[GroupSyncSource.EXAMS], activeGroupId)
    val scopedSearchEventsSync = syncStateForScope(eventsSyncState, groupSyncScopeIds[GroupSyncSource.EVENTS], activeGroupId)
    val scopedSearchIssuesSync = syncStateForScope(issuesSyncState, groupSyncScopeIds[GroupSyncSource.ISSUES], activeGroupId)
    val scopedSearchChatSync = syncStateForScope(searchChatHistorySyncState, searchChatHistorySyncGroupId, activeGroupId)
    val scopedSearchBooksSync = if (librarySyncGradeId == searchGradeId) librarySyncState else SyncState.Idle
    val allSearchSourceStates = listOf(
        scopedSearchContentsSync,
        scopedSearchHomeworkSync,
        scopedSearchExamsSync,
        scopedSearchEventsSync,
        scopedSearchIssuesSync,
        scopedSearchChatSync,
        scopedSearchBooksSync
    )
    val searchSyncState = when (selectedFilter) {
        "CONTENTS" -> scopedSearchContentsSync
        "HOMEWORK" -> scopedSearchHomeworkSync
        "EXAMS" -> scopedSearchExamsSync
        "EVENTS" -> scopedSearchEventsSync
        "ISSUES" -> scopedSearchIssuesSync
        "CHAT" -> scopedSearchChatSync
        "BOOKS" -> scopedSearchBooksSync
        else -> aggregateRequiredSyncStates(allSearchSourceStates)
    }

    LaunchedEffect(activeGroupId) {
        if (activeGroupId.isNotBlank()) {
            viewModel.refreshSearchChatHistory(activeGroupId)
        }
    }

    // Live Room flows for all 7 search dimensions
    val contents by viewModel.repository.getAllContents(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val homeworks by viewModel.repository.getHomeworks(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val exams by viewModel.repository.getExams(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val events by viewModel.repository.getEvents(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val issues by viewModel.repository.getIssues(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val chatMessages by viewModel.repository.getChatMessages(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val books by viewModel.repository.getBooks(currentUser?.gradeId ?: 10)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedContentForDetail by remember { mutableStateOf<ContentItem?>(null) }
    var activeTranscriptionContent by remember { mutableStateOf<ContentItem?>(null) }
    var selectedHomeworkForDetail by remember { mutableStateOf<HomeworkItem?>(null) }
    var selectedExamForDetail by remember { mutableStateOf<ExamItem?>(null) }
    var selectedEventForDetail by remember { mutableStateOf<SchoolEventItem?>(null) }
    var selectedIssueForDetail by remember { mutableStateOf<String?>(null) }
    var selectedChatMessageForDetail by remember { mutableStateOf<ChatMessageItem?>(null) }

    // If an issue was selected directly from search, open full IssueDetailView
    if (selectedIssueForDetail != null) {
        IssueDetailView(
            issueId = selectedIssueForDetail!!,
            viewModel = viewModel,
            onBack = { selectedIssueForDetail = null }
        )
        return
    }

    val query = searchQuery.trim()

    // Smart Ranked Search Results
    val rankedContents = remember(contents, query, selectedFilter) {
        if (selectedFilter != "ALL" && selectedFilter != "CONTENTS") emptyList()
        else if (query.isBlank()) contents.take(4).map { it to 1 }
        else contents.mapNotNull { item ->
            val score = ArabicSearchEngine.calculateRelevance(
                query = query,
                title = item.title,
                subject = item.subjectName,
                body = item.description,
                extra = item.authorName
            )
            if (score > 0) item to score else null
        }.sortedByDescending { it.second }
    }

    val rankedHomeworks = remember(homeworks, query, selectedFilter) {
        if (selectedFilter != "ALL" && selectedFilter != "HOMEWORK") emptyList()
        else if (query.isBlank()) homeworks.take(4).map { it to 1 }
        else homeworks.mapNotNull { item ->
            val score = ArabicSearchEngine.calculateRelevance(
                query = query,
                title = item.title,
                subject = item.subjectName,
                body = item.details,
                extra = "${item.pageNumbers ?: ""} ${item.questionNumbers ?: ""}"
            )
            if (score > 0) item to score else null
        }.sortedByDescending { it.second }
    }

    val rankedExams = remember(exams, query, selectedFilter) {
        if (selectedFilter != "ALL" && selectedFilter != "EXAMS") emptyList()
        else if (query.isBlank()) exams.take(4).map { it to 1 }
        else exams.mapNotNull { item ->
            val score = ArabicSearchEngine.calculateRelevance(
                query = query,
                title = item.title,
                subject = item.subjectName,
                body = item.requiredChapters,
                extra = item.notes
            )
            if (score > 0) item to score else null
        }.sortedByDescending { it.second }
    }

    val rankedEvents = remember(events, query, selectedFilter) {
        if (selectedFilter != "ALL" && selectedFilter != "EVENTS") emptyList()
        else if (query.isBlank()) events.take(4).map { it to 1 }
        else events.mapNotNull { item ->
            val score = ArabicSearchEngine.calculateRelevance(
                query = query,
                title = item.title,
                subject = item.category,
                body = item.description,
                extra = item.location
            )
            if (score > 0) item to score else null
        }.sortedByDescending { it.second }
    }

    val rankedIssues = remember(issues, query, selectedFilter) {
        if (selectedFilter != "ALL" && selectedFilter != "ISSUES") emptyList()
        else if (query.isBlank()) issues.take(4).map { it to 1 }
        else issues.mapNotNull { item ->
            val score = ArabicSearchEngine.calculateRelevance(
                query = query,
                title = item.title,
                subject = item.subjectName,
                body = item.description
            )
            if (score > 0) item to score else null
        }.sortedByDescending { it.second }
    }

    val rankedChatMessages = remember(chatMessages, query, selectedFilter) {
        if (selectedFilter != "ALL" && selectedFilter != "CHAT") emptyList()
        else if (query.isBlank()) emptyList()
        else chatMessages.mapNotNull { item ->
            val score = ArabicSearchEngine.calculateRelevance(
                query = query,
                title = item.text,
                subject = item.senderName,
                body = item.senderGradeSection
            )
            if (score > 0) item to score else null
        }.sortedByDescending { it.second }
    }

    val rankedBooks = remember(books, query, selectedFilter) {
        if (selectedFilter != "ALL" && selectedFilter != "BOOKS") emptyList()
        else if (query.isBlank()) books.take(4).map { it to 1 }
        else books.mapNotNull { item ->
            val score = ArabicSearchEngine.calculateRelevance(
                query = query,
                title = item.title,
                subject = item.subjectName,
                body = item.edition
            )
            if (score > 0) item to score else null
        }.sortedByDescending { it.second }
    }

    val refreshSearchSource: () -> Unit = {
        when (selectedFilter) {
            "CONTENTS" -> viewModel.syncSectionContents(activeGroupId, isRefresh = true)
            "HOMEWORK" -> viewModel.syncHomeworks(activeGroupId, isRefresh = true)
            "EXAMS" -> viewModel.syncExams(activeGroupId, isRefresh = true)
            "EVENTS" -> viewModel.syncEvents(activeGroupId, isRefresh = true)
            "ISSUES" -> viewModel.syncIssues(activeGroupId, isRefresh = true)
            "CHAT" -> viewModel.refreshSearchChatHistory(activeGroupId)
            "BOOKS" -> viewModel.syncBooks(searchGradeId, isRefresh = true)
            else -> {
                viewModel.syncSectionContents(activeGroupId, isRefresh = true)
                viewModel.syncHomeworks(activeGroupId, isRefresh = true)
                viewModel.syncExams(activeGroupId, isRefresh = true)
                viewModel.syncEvents(activeGroupId, isRefresh = true)
                viewModel.syncIssues(activeGroupId, isRefresh = true)
                viewModel.refreshSearchChatHistory(activeGroupId)
                viewModel.syncBooks(searchGradeId, isRefresh = true)
            }
        }
    }
    val totalResultsCount = rankedContents.size + rankedHomeworks.size + rankedExams.size +
            rankedEvents.size + rankedIssues.size + rankedChatMessages.size + rankedBooks.size

    fun triggerSearch(term: String) {
        val committedQuery = term.trim()
        searchQuery = committedQuery
        if (committedQuery.isNotBlank()) {
            ArabicSearchEngine.saveRecentSearch(context, committedQuery)
            recentSearches = ArabicSearchEngine.getRecentSearches(context)
        }
    }

    BackHandler {
        viewModel.navigateBack()
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
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Search Input Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = "محرك البحث الشامل الذكي",
                        placeholder = "ابحث عن درس، واجب، اختبار، فعالية، استفسار...",
                        modifier = Modifier.weight(1f),
                        leadingIcon = Icons.Default.Search,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { triggerSearch(searchQuery) }
                        )
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.padding(top = 22.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح", tint = TextMuted)
                        }
                    }
                }
            }

            // Quick Suggestions & Recent Searches
            if (recentSearches.isNotEmpty() || query.isBlank()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (recentSearches.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🕒 عمليات البحث الأخيرة:", fontSize = tanweerTextSize(11f), color = TextMuted, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "مسح السجل",
                                    fontSize = tanweerTextSize(11f),
                                    color = RubyRed,
                                    modifier = Modifier.clickable {
                                        ArabicSearchEngine.clearRecentSearches(context)
                                        recentSearches = emptyList()
                                    }
                                )
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(recentSearches) { term ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(GlassSurface)
                                            .border(1.dp, GlassBorderSubtle, RoundedCornerShape(12.dp))
                                            .clickable { triggerSearch(term) }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(text = term, fontSize = tanweerTextSize(11f), color = TextSecondary)
                                    }
                                }
                            }
                        }

                        // Keyword Suggestions
                        Text("💡 مقترحات بحث سريعة:", fontSize = tanweerTextSize(11f), color = TextMuted, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(ArabicSearchEngine.suggestedKeywords) { keyword ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CyanGlow)
                                        .border(1.dp, CyanAccent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .clickable { triggerSearch(keyword) }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(text = keyword, fontSize = tanweerTextSize(11f), color = CyanAccent, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            // Category Filter Chips (All 7 Domains)
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filterOptions = listOf(
                        "ALL" to "الكل 🌐",
                        "CONTENTS" to "الدروس 📚",
                        "HOMEWORK" to "واجبات 📋",
                        "EXAMS" to "اختبارات 🎯",
                        "EVENTS" to "فعاليات 🎪",
                        "ISSUES" to "استفسارات ❓",
                        "CHAT" to "محادثات 💬",
                        "BOOKS" to "كتب 📕"
                    )
                    items(filterOptions) { (key, label) ->
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
                                fontSize = tanweerTextSize(12f),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) TextOnAccent else TextSecondary
                            )
                        }
                    }
                }
            }

            // Search reads local Room data; keep source freshness visible beside the results.
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SyncStatusBar(
                        syncState = searchSyncState,
                        onRefresh = refreshSearchSource
                    )
                    Text(
                        text = stringResource(R.string.search_local_index_note),
                        fontSize = tanweerTextSize(10f),
                        color = TextMuted,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Results Summary
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (query.isBlank()) "استعراض أحدث محتويات المنظومة" else "نتائج البحث عن \"$query\"",
                        fontSize = tanweerTextSize(13f),
                        color = TextSecondary
                    )
                    GlassPill(
                        text = stringResource(R.string.search_local_results_count, totalResultsCount),
                        color = CyanAccent,
                        bgColor = CyanGlow
                    )
                }
            }

            // 1. Lessons Results (Exact Click -> Lesson Details)
            if (rankedContents.isNotEmpty()) {
                item {
                    Text(text = "📚 الدروس وتوثيق السبورة (${rankedContents.size})", fontSize = tanweerTextSize(15f), fontWeight = FontWeight.Bold, color = CyanAccent)
                }
                items(rankedContents) { (content, _) ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedContentForDetail = content },
                        backgroundColor = GlassSurface
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                GlassPill(text = content.subjectName, color = CyanAccent, bgColor = CyanGlow)
                                Text(text = "التاريخ: ${content.studyDate}", fontSize = tanweerTextSize(11f), color = TextMuted)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = content.title, fontSize = tanweerTextSize(15f), fontWeight = FontWeight.Bold, color = TextPrimary)
                            if (!content.description.isNullOrBlank()) {
                                Text(text = content.description, fontSize = tanweerTextSize(12f), color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (content.media.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "📷 يحتوي ${content.media.size} صور سبورة موثقة", fontSize = tanweerTextSize(11f), color = EmeraldGreen)
                            }
                        }
                    }
                }
            }

            // 2. Homework Results (Exact Click -> Homework Details & Checkbox)
            if (rankedHomeworks.isNotEmpty()) {
                item {
                    Text(text = "📋 الواجبات والمهام (${rankedHomeworks.size})", fontSize = tanweerTextSize(15f), fontWeight = FontWeight.Bold, color = WarmAmber)
                }
                items(rankedHomeworks) { (hw, _) ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedHomeworkForDetail = hw },
                        backgroundColor = GlassSurface
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassPill(text = hw.subjectName, color = WarmAmber, bgColor = WarmAmber.copy(alpha = 0.15f))
                                GlassPill(
                                    text = if (hw.isCompleted) "مكتمل ✓" else "قيد الإنجاز ⏳",
                                    color = if (hw.isCompleted) EmeraldGreen else WarmAmber,
                                    bgColor = if (hw.isCompleted) EmeraldGreen.copy(alpha = 0.15f) else WarmAmber.copy(alpha = 0.15f)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = hw.title, fontSize = tanweerTextSize(15f), fontWeight = FontWeight.Bold, color = TextPrimary)
                            if (!hw.details.isNullOrBlank()) {
                                Text(text = hw.details, fontSize = tanweerTextSize(12f), color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Text(text = "تاريخ الاستحقاق: ${hw.dueDate}", fontSize = tanweerTextSize(11f), color = TextMuted)
                        }
                    }
                }
            }

            // 3. Exam Results (Exact Click -> Exam Details & Checklist)
            if (rankedExams.isNotEmpty()) {
                item {
                    Text(text = "🎯 جدول الاختبارات (${rankedExams.size})", fontSize = tanweerTextSize(15f), fontWeight = FontWeight.Bold, color = RubyRed)
                }
                items(rankedExams) { (exam, _) ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedExamForDetail = exam },
                        backgroundColor = GlassSurface
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassPill(text = exam.subjectName, color = RubyRed, bgColor = RubyRed.copy(alpha = 0.15f))
                                Text(text = "موعد: ${exam.examDate}", fontSize = tanweerTextSize(11f), color = WarmAmber, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = exam.title, fontSize = tanweerTextSize(15f), fontWeight = FontWeight.Bold, color = TextPrimary)
                            if (!exam.requiredChapters.isNullOrBlank()) {
                                Text(text = "المقرر: ${localizedChapterListLabel(exam.requiredChapters)}", fontSize = tanweerTextSize(12f), color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            // 4. Events Results (Exact Click -> Event Details)
            if (rankedEvents.isNotEmpty()) {
                item {
                    Text(text = "🎪 الفعاليات والأنشطة (${rankedEvents.size})", fontSize = tanweerTextSize(15f), fontWeight = FontWeight.Bold, color = EmeraldGreen)
                }
                items(rankedEvents) { (event, _) ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedEventForDetail = event },
                        backgroundColor = GlassSurface
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassPill(text = localizedEventCategoryLabel(event.category), color = EmeraldGreen, bgColor = EmeraldGreen.copy(alpha = 0.15f))
                                Text(text = event.eventDate, fontSize = tanweerTextSize(11f), color = TextMuted)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = event.title, fontSize = tanweerTextSize(15f), fontWeight = FontWeight.Bold, color = TextPrimary)
                            if (!event.location.isNullOrBlank()) {
                                Text(text = "📍 المكان: ${event.location}", fontSize = tanweerTextSize(12f), color = CyanAccent)
                            }
                            if (!event.description.isNullOrBlank()) {
                                Text(text = event.description, fontSize = tanweerTextSize(12f), color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            // 5. Inquiries / Issues Results (Exact Click -> Full Issue Thread)
            if (rankedIssues.isNotEmpty()) {
                item {
                    Text(text = "❓ الاستفسارات والنقاشات الدراسية (${rankedIssues.size})", fontSize = tanweerTextSize(15f), fontWeight = FontWeight.Bold, color = PurpleAccent)
                }
                items(rankedIssues) { (issue, _) ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedIssueForDetail = issue.id },
                        backgroundColor = GlassSurface
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val issueStatus = IssueStatusPolicy.fromApiValue(issue.status)
                                val issueStatusColor = when (issueStatus) {
                                    IssueStatus.OPEN -> CyanAccent
                                    IssueStatus.IN_DISCUSSION -> WarmAmber
                                    IssueStatus.SOLVED, IssueStatus.VERIFIED -> EmeraldGreen
                                    IssueStatus.CLOSED -> TextMuted
                                    IssueStatus.UNKNOWN -> TextSecondary
                                }
                                GlassPill(
                                    text = stringResource(issueStatus.labelRes),
                                    color = issueStatusColor,
                                    bgColor = issueStatusColor.copy(alpha = 0.15f)
                                )
                                Text(text = "${issue.commentsCount} ردود", fontSize = tanweerTextSize(11f), color = TextMuted)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = issue.title, fontSize = tanweerTextSize(15f), fontWeight = FontWeight.Bold, color = TextPrimary)
                            if (!issue.description.isNullOrBlank()) {
                                Text(text = issue.description, fontSize = tanweerTextSize(12f), color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            // 6. Chat Messages Results (Exact Click -> View Message & Open Chat)
            if (rankedChatMessages.isNotEmpty()) {
                item {
                    Text(text = "💬 رسائل ونقاشات المجموعة (${rankedChatMessages.size})", fontSize = tanweerTextSize(15f), fontWeight = FontWeight.Bold, color = ElectricBlue)
                }
                items(rankedChatMessages) { (msg, _) ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedChatMessageForDetail = msg },
                        backgroundColor = GlassSurface
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "👤 ${msg.senderName}", fontSize = tanweerTextSize(12f), fontWeight = FontWeight.Bold, color = ElectricBlue)
                                Text(text = SimpleDateFormat("HH:mm • yyyy-MM-dd", Locale.US).format(Date(msg.timestamp)), fontSize = tanweerTextSize(10f), color = TextMuted)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = msg.text, fontSize = tanweerTextSize(13f), color = TextPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // 7. Books Results (Exact Click -> Instant Reader)
            if (rankedBooks.isNotEmpty()) {
                item {
                    Text(text = "📕 الكتب والمناهج الدراسية (${rankedBooks.size})", fontSize = tanweerTextSize(15f), fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                items(rankedBooks) { (book, _) ->
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
                                Text(text = book.title, fontSize = tanweerTextSize(15f), fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(text = "${book.subjectName} • ${book.edition}", fontSize = tanweerTextSize(12f), color = TextSecondary)
                            }
                            GlassOutlinedButton(
                                text = "مطالعة 📖",
                                onClick = { viewModel.openBookInPdfReader(book) }
                            )
                        }
                    }
                }
            }

            // An empty local Room result is not a confirmed no-match until its source sync succeeds.
            if (totalResultsCount == 0) {
                val confirmedNoResults = shouldShowConfirmedNoSearchResults(
                    query = query,
                    resultCount = totalResultsCount,
                    syncState = searchSyncState
                )

                item {
                    val emptyMessage = when {
                        query.isBlank() -> stringResource(R.string.search_enter_query)
                        confirmedNoResults -> stringResource(R.string.search_no_results_confirmed)
                        searchSyncState is SyncState.OfflineCached ->
                            stringResource(R.string.search_no_results_offline)
                        searchSyncState is SyncState.Loading || searchSyncState is SyncState.Refreshing ->
                            stringResource(R.string.search_results_verifying)
                        searchSyncState is SyncState.Error ->
                            stringResource(R.string.search_results_unverified)
                        else -> stringResource(R.string.search_results_pending)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "🔍", fontSize = tanweerTextSize(40f))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = emptyMessage,
                                fontSize = tanweerTextSize(14f),
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // EXACT DESTINATION DEEP LINK MODALS
        // =========================================================================

        // 1. Content / Lesson Modal
        selectedContentForDetail?.let { content ->
            Dialog(onDismissRequest = { selectedContentForDetail = null }) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    backgroundColor = MidnightSurface,
                    borderColor = CyanAccent
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassPill(text = content.subjectName, color = CyanAccent, bgColor = CyanGlow)
                            IconButton(onClick = { selectedContentForDetail = null }) {
                                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondary)
                            }
                        }

                        Text(text = content.title, fontSize = tanweerTextSize(18f), fontWeight = FontWeight.Black, color = TextPrimary)
                        Text(text = "📅 تاريخ الدرس: ${content.studyDate} • الكاتب: ${content.authorName}", fontSize = tanweerTextSize(11f), color = TextSecondary)

                        if (!content.description.isNullOrBlank()) {
                            Text(text = content.description, fontSize = tanweerTextSize(13f), color = TextPrimary, lineHeight = tanweerLineHeight(18f))
                        }

                        if (content.media.isNotEmpty()) {
                            Text("📷 لوحات الشرح والسبورة (${content.media.size} لوحات):", fontSize = tanweerTextSize(12f), fontWeight = FontWeight.Bold, color = CyanAccent)
                            Text("لوحات الشرح موثقة سحابياً — اضغط 'نقل الدرس للدفتر' لمطالعتها وتنزيلها لمذاكرتها أوفلاين.", fontSize = tanweerTextSize(11f), color = TextSecondary)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton(
                                text = "✍️ نقل الدرس للدفتر",
                                onClick = {
                                    activeTranscriptionContent = content
                                    selectedContentForDetail = null
                                },
                                modifier = Modifier.weight(1f)
                            )
                            GlassOutlinedButton(
                                text = "مساحة المادة 📚",
                                onClick = {
                                    val subjId = content.subjectId
                                    selectedContentForDetail = null
                                    viewModel.openSubjectKnowledgeBase(subjId)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 2. Homework Modal
        selectedHomeworkForDetail?.let { hw ->
            Dialog(onDismissRequest = { selectedHomeworkForDetail = null }) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    backgroundColor = MidnightSurface,
                    borderColor = WarmAmber
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassPill(text = hw.subjectName, color = WarmAmber, bgColor = WarmAmber.copy(alpha = 0.15f))
                            IconButton(onClick = { selectedHomeworkForDetail = null }) {
                                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondary)
                            }
                        }

                        Text(text = hw.title, fontSize = tanweerTextSize(18f), fontWeight = FontWeight.Black, color = TextPrimary)
                        Text(text = "📅 تاريخ الطرح: ${hw.studyDate} • الاستحقاق: ${hw.dueDate}", fontSize = tanweerTextSize(11f), color = TextSecondary)

                        if (!hw.details.isNullOrBlank()) {
                            Text(text = hw.details, fontSize = tanweerTextSize(13f), color = TextPrimary, lineHeight = tanweerLineHeight(18f))
                        }

                        if (!hw.pageNumbers.isNullOrBlank() || !hw.questionNumbers.isNullOrBlank()) {
                            Text(text = "📖 الصفحات: ${hw.pageNumbers ?: "—"} • الأسئلة: ${hw.questionNumbers ?: "—"}", fontSize = tanweerTextSize(12f), color = CyanAccent)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (hw.isCompleted) "تم إنجاز هذا الواجب ✓" else "الواجب غير منجز بعد",
                                fontSize = tanweerTextSize(12f),
                                color = if (hw.isCompleted) EmeraldGreen else WarmAmber,
                                fontWeight = FontWeight.Bold
                            )
                            GlassButton(
                                text = if (hw.isCompleted) "إلغاء الإنجاز" else "تحديد كمنجز ✓",
                                color = if (hw.isCompleted) TextMuted else EmeraldGreen,
                                onClick = {
                                    viewModel.toggleHomework(hw.id, hw.isCompleted)
                                    selectedHomeworkForDetail = hw.copy(isCompleted = !hw.isCompleted)
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. Exam Modal
        selectedExamForDetail?.let { exam ->
            Dialog(onDismissRequest = { selectedExamForDetail = null }) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    backgroundColor = MidnightSurface,
                    borderColor = RubyRed
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassPill(text = exam.subjectName, color = RubyRed, bgColor = RubyRed.copy(alpha = 0.15f))
                            IconButton(onClick = { selectedExamForDetail = null }) {
                                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondary)
                            }
                        }

                        Text(text = exam.title, fontSize = tanweerTextSize(18f), fontWeight = FontWeight.Black, color = TextPrimary)
                        Text(text = "📅 موعد الاختبار: ${exam.examDate}", fontSize = tanweerTextSize(12f), color = WarmAmber, fontWeight = FontWeight.Bold)

                        if (!exam.requiredChapters.isNullOrBlank()) {
                            Text(text = "المقرر والفصول: ${localizedChapterListLabel(exam.requiredChapters)}", fontSize = tanweerTextSize(13f), color = CyanAccent)
                        }
                        if (!exam.notes.isNullOrBlank()) {
                            Text(text = "ملاحظات: ${exam.notes}", fontSize = tanweerTextSize(12f), color = TextSecondary)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (exam.isCompleted) "تمت المذاكرة بالكامل ✓" else "مطلوب المذاكرة",
                                fontSize = tanweerTextSize(12f),
                                color = if (exam.isCompleted) EmeraldGreen else RubyRed,
                                fontWeight = FontWeight.Bold
                            )
                            GlassButton(
                                text = if (exam.isCompleted) "إلغاء الإنجاز" else "تمت المذاكرة ✓",
                                color = if (exam.isCompleted) TextMuted else EmeraldGreen,
                                onClick = {
                                    viewModel.toggleExamCompletion(exam.id, !exam.isCompleted)
                                    selectedExamForDetail = exam.copy(isCompleted = !exam.isCompleted)
                                }
                            )
                        }
                    }
                }
            }
        }

        // 4. Event Modal
        selectedEventForDetail?.let { event ->
            Dialog(onDismissRequest = { selectedEventForDetail = null }) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    backgroundColor = MidnightSurface,
                    borderColor = EmeraldGreen
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassPill(text = localizedEventCategoryLabel(event.category), color = EmeraldGreen, bgColor = EmeraldGreen.copy(alpha = 0.15f))
                            IconButton(onClick = { selectedEventForDetail = null }) {
                                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondary)
                            }
                        }

                        Text(text = event.title, fontSize = tanweerTextSize(18f), fontWeight = FontWeight.Black, color = TextPrimary)
                        Text(text = "📅 التاريخ: ${event.eventDate} ${event.timeStr?.let { "• $it" } ?: ""}", fontSize = tanweerTextSize(11f), color = TextSecondary)

                        if (!event.location.isNullOrBlank()) {
                            Text(text = "📍 المكان: ${event.location}", fontSize = tanweerTextSize(13f), color = CyanAccent, fontWeight = FontWeight.Bold)
                        }
                        if (!event.description.isNullOrBlank()) {
                            Text(text = event.description, fontSize = tanweerTextSize(13f), color = TextPrimary, lineHeight = tanweerLineHeight(18f))
                        }
                    }
                }
            }
        }

        // 5. Chat Message Modal
        selectedChatMessageForDetail?.let { msg ->
            Dialog(onDismissRequest = { selectedChatMessageForDetail = null }) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    backgroundColor = MidnightSurface,
                    borderColor = ElectricBlue
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "👤 ${msg.senderName}", fontSize = tanweerTextSize(14f), fontWeight = FontWeight.Bold, color = ElectricBlue)
                            IconButton(onClick = { selectedChatMessageForDetail = null }) {
                                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondary)
                            }
                        }

                        Text(
                            text = SimpleDateFormat("HH:mm • yyyy-MM-dd", Locale.US).format(Date(msg.timestamp)),
                            fontSize = tanweerTextSize(11f),
                            color = TextMuted
                        )

                        Text(text = msg.text, fontSize = tanweerTextSize(14f), color = TextPrimary, lineHeight = tanweerLineHeight(20f))

                        Spacer(modifier = Modifier.height(6.dp))

                        GlassButton(
                            text = "فتح الرسالة في محادثة الشعبة 💬",
                            onClick = {
                                val targetMsg = selectedChatMessageForDetail
                                selectedChatMessageForDetail = null
                                if (targetMsg != null) {
                                    viewModel.navigateToGroupChatMessage(targetMsg.groupId, targetMsg.id)
                                } else {
                                    viewModel.setSubScreen(SubScreen.GROUPS)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        activeTranscriptionContent?.let { content ->
            NotebookTranscriptionDialog(
                content = content,
                onDismiss = { activeTranscriptionContent = null }
            )
        }
    }
}
