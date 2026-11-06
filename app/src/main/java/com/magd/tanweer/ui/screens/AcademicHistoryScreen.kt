package com.magd.tanweer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.SchoolHierarchy
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.GlassCard
import com.magd.tanweer.ui.components.GlassPill
import com.magd.tanweer.ui.theme.*
import com.magd.tanweer.util.AcademicArchiveEngine
import com.magd.tanweer.util.AcademicYearManager

@Composable
fun AcademicHistoryScreen(
    viewModel: TanweerViewModel
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val archiveSyncState by viewModel.academicArchiveSyncState.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedAcademicYear.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId ?: "" }

    val gradeSubjects = remember(currentUser?.gradeId) {
        SchoolHierarchy.getSubjectsForGrade(currentUser?.gradeId ?: 11)
    }

    // Load group-specific and all-historical cross-group content
    val activeGroupContents by viewModel.repository.getAllContents(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val activeGroupHomeworks by viewModel.repository.getHomeworks(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val activeGroupExams by viewModel.repository.getExams(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val allHistoricalContents by viewModel.repository.getAllHistoricalContents()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val allHistoricalHomeworks by viewModel.repository.getAllHistoricalHomeworks()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val allHistoricalExams by viewModel.repository.getAllHistoricalExams()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val todayStr = remember { TanweerViewModel.getTodayDateString() }

    val allRecordedDates = remember(
        allHistoricalContents,
        allHistoricalHomeworks,
        allHistoricalExams,
        activeGroupContents,
        activeGroupHomeworks,
        activeGroupExams
    ) {
        buildList {
            allHistoricalContents.forEach { add(it.studyDate) }
            allHistoricalHomeworks.forEach {
                if (it.dueDate.isNotBlank()) add(it.dueDate)
                if (it.studyDate.isNotBlank()) add(it.studyDate)
            }
            allHistoricalExams.forEach { add(it.examDate) }

            // Include active-group snapshots as well. This keeps the year selector
            // accurate even when a current-group item has not yet appeared in the
            // account-wide historical endpoint.
            activeGroupContents.forEach { add(it.studyDate) }
            activeGroupHomeworks.forEach {
                if (it.dueDate.isNotBlank()) add(it.dueDate)
                if (it.studyDate.isNotBlank()) add(it.studyDate)
            }
            activeGroupExams.forEach { add(it.examDate) }
        }
    }

    val academicYears = remember(todayStr, allRecordedDates) {
        AcademicYearManager.resolveAvailableAcademicYears(todayStr, allRecordedDates)
    }

    val currentAcademicYear = remember(todayStr) {
        AcademicYearManager.getAcademicYearForDate(todayStr)
    }

    LaunchedEffect(academicYears) {
        if (selectedYear !in academicYears && academicYears.isNotEmpty()) {
            val defaultYear = if (currentAcademicYear in academicYears) currentAcademicYear else academicYears.first()
            viewModel.setSelectedAcademicYear(defaultYear)
        }
    }

    // Mode: 0 = إنجازاتي الشخصية (الأرشيف الشخصي الشامل عبر كل المجموعات), 1 = أرشيف الشعبة الحالية
    var viewMode by remember { mutableIntStateOf(0) }

    var previewContent by remember { mutableStateOf<com.magd.tanweer.data.model.ContentItem?>(null) }
    var previewHomework by remember { mutableStateOf<com.magd.tanweer.data.model.HomeworkItem?>(null) }
    var previewExam by remember { mutableStateOf<com.magd.tanweer.data.model.ExamItem?>(null) }

    val allContents = remember(viewMode, activeGroupContents, allHistoricalContents) {
        if (viewMode == 0) allHistoricalContents else activeGroupContents
    }
    val allHomeworks = remember(viewMode, activeGroupHomeworks, allHistoricalHomeworks) {
        if (viewMode == 0) allHistoricalHomeworks else activeGroupHomeworks
    }
    val allExams = remember(viewMode, activeGroupExams, allHistoricalExams) {
        if (viewMode == 0) allHistoricalExams else activeGroupExams
    }

    // Generate comprehensive academic archive report
    val archiveReport = remember(selectedYear, allContents, allHomeworks, allExams, currentUser) {
        AcademicArchiveEngine.generateArchiveReport(
            academicYear = selectedYear,
            contents = allContents,
            homeworks = allHomeworks,
            exams = allExams,
            userFullName = currentUser?.fullName,
            currentDate = todayStr,
            userId = currentUser?.id
        )
    }

    // Filtered by selected year using deterministic academic calendar boundaries (Point 20)
    val yearContents = remember(allContents, selectedYear) {
        allContents.filter { AcademicYearManager.isDateInAcademicYear(it.studyDate, selectedYear) }
    }
    val yearHomeworks = remember(allHomeworks, selectedYear) {
        allHomeworks.filter {
            AcademicYearManager.isDateInAcademicYear(it.dueDate, selectedYear) ||
                    AcademicYearManager.isDateInAcademicYear(it.studyDate, selectedYear)
        }
    }
    val yearExams = remember(allExams, selectedYear) {
        allExams.filter { AcademicYearManager.isDateInAcademicYear(it.examDate, selectedYear) }
    }

    // Personal achievements filtering: matches student across all academic groups
    val myPersonalLessons = remember(yearContents, currentUser) {
        val uid = currentUser?.id.orEmpty()
        val uName = currentUser?.fullName.orEmpty()
        yearContents.filter {
            (uid.isNotEmpty() && it.createdBy.isNotBlank() && it.createdBy == uid) ||
            (uName.isNotEmpty() && (it.authorName == uName || it.authorName.contains(uName)))
        }
    }
    val myCompletedHomeworks = remember(yearHomeworks) {
        yearHomeworks.filter { it.isCompleted }
    }

    val totalYearItemsCount = yearContents.size + yearHomeworks.size + yearExams.size

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
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "الأرشيف الأكاديمي 🏛️",
                    fontSize = tanweerTextSize(17f),
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "السجل التراكمي للإنجازات والدروس والمواد",
                    fontSize = tanweerTextSize(12f),
                    color = TextSecondary
                )
                if (archiveSyncState.isBusy()) {
                    Text(
                        text = "جارٍ تحديث أرشيفك من الخادم…",
                        fontSize = tanweerTextSize(10f),
                        color = CyanAccent
                    )
                } else if (archiveSyncState is com.magd.tanweer.data.model.SyncState.OfflineCached) {
                    Text(
                        text = "تعرض السجلات المحفوظة محلياً حتى عودة الاتصال.",
                        fontSize = tanweerTextSize(10f),
                        color = WarmAmber
                    )
                } else if (archiveSyncState is com.magd.tanweer.data.model.SyncState.Error) {
                    Text(
                        text = (archiveSyncState as com.magd.tanweer.data.model.SyncState.Error).message,
                        fontSize = tanweerTextSize(10f),
                        color = RubyRed,
                        maxLines = 2
                    )
                }
            }
            IconButton(
                onClick = { viewModel.syncAcademicArchive(isRefresh = true) },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(GlassSurface)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "تحديث الأرشيف الأكاديمي",
                    tint = CyanAccent
                )
            }
        }

        // Academic Year Selector (dynamically rendered from AcademicYearManager & available school records)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            academicYears.forEach { yr ->
                val isCurrent = yr == currentAcademicYear
                val isSelected = yr == selectedYear

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) CyanGlow.copy(alpha = 0.25f) else GlassSurface)
                        .border(
                            1.dp,
                            if (isSelected) CyanAccent else GlassBorderSubtle,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { viewModel.setSelectedAcademicYear(yr) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = yr,
                            fontSize = tanweerTextSize(13f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) CyanAccent else TextSecondary
                        )
                        if (isCurrent) {
                            Text("📍 الحالي", fontSize = tanweerTextSize(11f), color = CyanAccent)
                        } else {
                            Text("📦 مؤرشف", fontSize = tanweerTextSize(10f), color = TextMuted)
                        }
                    }
                }
            }
        }

        // Mode Selector: إنجازاتي الشخصية vs أرشيف الشعبة
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (viewMode == 0) PurpleAccent.copy(alpha = 0.2f) else GlassSurface)
                    .border(1.dp, if (viewMode == 0) PurpleAccent else GlassBorderSubtle, RoundedCornerShape(12.dp))
                .clickable { viewMode = 0 }
                .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "👤 إنجازاتي ومشاركاتي",
                    fontSize = tanweerTextSize(12f),
                    fontWeight = if (viewMode == 0) FontWeight.Bold else FontWeight.Normal,
                    color = if (viewMode == 0) PurpleAccent else TextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (viewMode == 1) EmeraldGreen.copy(alpha = 0.2f) else GlassSurface)
                    .border(1.dp, if (viewMode == 1) EmeraldGreen else GlassBorderSubtle, RoundedCornerShape(12.dp))
                .clickable { viewMode = 1 }
                .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🏫 أرشيف الشعبة للعام",
                    fontSize = tanweerTextSize(12f),
                    fontWeight = if (viewMode == 1) FontWeight.Bold else FontWeight.Normal,
                    color = if (viewMode == 1) EmeraldGreen else TextSecondary
                )
            }
        }

        // Content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary Card
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurface
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "أرشيف العام: $selectedYear",
                                fontSize = tanweerTextSize(15f),
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent
                            )
                            Text(
                                text = if (selectedYear == currentAcademicYear) "العام الدراسي النشط حالياً" else "سجل مؤرشف ومحفوظ للرجوع إليه",
                                fontSize = tanweerTextSize(12f),
                                color = TextSecondary
                            )
                        }
                        GlassPill(
                            text = if (viewMode == 0) "${myCompletedHomeworks.size} منجز / ${myPersonalLessons.size} موثق" else "$totalYearItemsCount عنصر مسجل",
                            color = if (viewMode == 0) PurpleAccent else EmeraldGreen,
                            bgColor = if (viewMode == 0) PurpleAccent.copy(alpha = 0.15f) else EmeraldGreen.copy(alpha = 0.15f)
                        )
                    }
                }
            }

            // Structured Academic Archive Card (Point 19 & 20)
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurface
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📊 النطاق الأكاديمي: ${archiveReport.startDate} إلى ${archiveReport.endDate}",
                                fontSize = tanweerTextSize(12f),
                                color = TextSecondary
                            )
                            if (archiveReport.isArchivedPastYear) {
                                GlassPill(text = "سنة مؤرشفة 🔒", color = WarmAmber, bgColor = WarmAmber.copy(alpha = 0.15f))
                            } else {
                                GlassPill(text = "سنة دراسية جارية 🟢", color = EmeraldGreen, bgColor = EmeraldGreen.copy(alpha = 0.15f))
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CyanGlow.copy(alpha = 0.15f))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("الفصل الدراسي الأول", fontSize = tanweerTextSize(11f), color = TextSecondary)
                                    Text("${archiveReport.semester1Lessons} درس • ${archiveReport.semester1Homeworks} واجب", fontSize = tanweerTextSize(12f), fontWeight = FontWeight.Bold, color = CyanAccent)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PurpleAccent.copy(alpha = 0.15f))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("الفصل الدراسي الثاني", fontSize = tanweerTextSize(11f), color = TextSecondary)
                                    Text("${archiveReport.semester2Lessons} درس • ${archiveReport.semester2Homeworks} واجب", fontSize = tanweerTextSize(12f), fontWeight = FontWeight.Bold, color = PurpleAccent)
                                }
                            }
                        }
                    }
                }
            }

            // If selected archived year has no data
            if (totalYearItemsCount == 0) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = GlassSurface.copy(alpha = 0.4f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("📦", fontSize = tanweerTextSize(32f))
                            Text(
                                text = "لا توجد سجلات محفوظة للشعبة في عام $selectedYear",
                                fontSize = tanweerTextSize(14f),
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "تُعرض الأعوام التي تحتوي على سجلات فعلية، ويظل العام الحالي ${currentAcademicYear} متاحاً حتى قبل تسجيل أول نشاط.",
                                fontSize = tanweerTextSize(12f),
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else if (viewMode == 0) {
                // Personal View Mode
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = PurpleAccent.copy(alpha = 0.4f),
                        backgroundColor = GlassSurface
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📊 ملخص أدائي الشخصي في $selectedYear",
                                    fontSize = tanweerTextSize(14f),
                                    fontWeight = FontWeight.Bold,
                                    color = PurpleAccent
                                )
                                GlassPill(
                                    text = currentUser?.fullName ?: "طالب",
                                    color = CyanAccent,
                                    bgColor = CyanAccent.copy(alpha = 0.15f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${myCompletedHomeworks.size}", fontSize = tanweerTextSize(18f), fontWeight = FontWeight.Bold, color = EmeraldGreen)
                                    Text("واجبات منجزة", fontSize = tanweerTextSize(11f), color = TextSecondary)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${myPersonalLessons.size}", fontSize = tanweerTextSize(18f), fontWeight = FontWeight.Bold, color = CyanAccent)
                                    Text("دروس وثقتها", fontSize = tanweerTextSize(11f), color = TextSecondary)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${yearHomeworks.size}", fontSize = tanweerTextSize(18f), fontWeight = FontWeight.Bold, color = WarmAmber)
                                    Text("إجمالي الواجبات", fontSize = tanweerTextSize(11f), color = TextSecondary)
                                }
                            }
                        }
                    }
                }

                // Personal Lessons
                item {
                    Text(
                        text = "📖 الدروس التي وثقتها شخصياً (${myPersonalLessons.size})",
                        fontSize = tanweerTextSize(13f),
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (myPersonalLessons.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface.copy(alpha = 0.3f)) {
                            Text(
                                text = "لم تقم بتوثيق دروس للشعبة تحت هذا العام بعد. يمكنك استخدام زر الإضافة لتوثيق حصصك.",
                                fontSize = tanweerTextSize(12f),
                                color = TextMuted,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                } else {
                    items(myPersonalLessons, key = { it.id }) { lesson ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = GlassSurface,
                            onClick = { previewContent = lesson }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${lesson.subjectIcon} ${lesson.subjectName}: ${lesson.title}",
                                        fontSize = tanweerTextSize(13f),
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "تاريخ الحصة: ${lesson.studyDate} • ${lesson.viewsCount} مشاهدة (انقر للمعاينة 👁️)",
                                        fontSize = tanweerTextSize(11f),
                                        color = TextSecondary
                                    )
                                }
                                GlassPill(text = "موثق بواسطةك", color = EmeraldGreen, bgColor = EmeraldGreen.copy(alpha = 0.12f))
                            }
                        }
                    }
                }

                // Personal Completed Homeworks
                item {
                    Text(
                        text = "✅ الواجبات التي أنجزتها (${myCompletedHomeworks.size})",
                        fontSize = tanweerTextSize(13f),
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (myCompletedHomeworks.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface.copy(alpha = 0.3f)) {
                            Text(
                                text = "لا توجد واجبات مكتملة مسجلة في هذا العام الدراسي حتى الآن.",
                                fontSize = tanweerTextSize(12f),
                                color = TextMuted,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                } else {
                    items(myCompletedHomeworks, key = { it.id }) { hw ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = GlassSurface,
                            onClick = { previewHomework = hw }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "📝 واجب ${hw.subjectName}: ${hw.title}",
                                        fontSize = tanweerTextSize(13f),
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "تاريخ التسليم: ${hw.dueDate} (انقر للتفاصيل 👁️)",
                                        fontSize = tanweerTextSize(11f),
                                        color = TextSecondary
                                    )
                                }
                                GlassPill(text = "مكتمل ✓", color = EmeraldGreen, bgColor = EmeraldGreen.copy(alpha = 0.15f))
                            }
                        }
                    }
                }
            } else {
                // Class Archive Mode (by Subject)
                items(gradeSubjects, key = { it.id }) { subject ->
                    var expanded by remember { mutableStateOf(false) }

                    val subjContents = yearContents.filter { it.subjectId == subject.id }
                    val subjHomeworks = yearHomeworks.filter { it.subjectId == subject.id }
                    val subjExams = yearExams.filter { it.subjectId == subject.id }

                    val subjColor = try {
                        Color(android.graphics.Color.parseColor(subject.colorHex))
                    } catch (_: Exception) {
                        CyanAccent
                    }

                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = GlassSurface,
                        borderColor = if (expanded) subjColor.copy(alpha = 0.6f) else GlassBorderSubtle
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expanded = !expanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(subjColor.copy(alpha = 0.2f))
                                            .border(1.dp, subjColor.copy(alpha = 0.4f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(subject.icon, fontSize = tanweerTextSize(18f))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(subject.name, fontSize = tanweerTextSize(15f), fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(
                                            text = "${subjContents.size} دروس • ${subjHomeworks.size} واجبات • ${subjExams.size} اختبارات",
                                            fontSize = tanweerTextSize(11f),
                                            color = TextMuted
                                        )
                                    }
                                }

                                IconButton(onClick = { expanded = !expanded }) {
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ChevronRight,
                                        contentDescription = "عرض التفاصيل",
                                        tint = subjColor
                                    )
                                }
                            }

                            AnimatedVisibility(visible = expanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(GlassBorderSubtle)
                                    )

                                    Text("📚 الدروس الموثقة (${subjContents.size}):", fontSize = tanweerTextSize(12f), fontWeight = FontWeight.Bold, color = CyanAccent)
                                    if (subjContents.isEmpty()) {
                                        Text("لا توجد دروس مسجلة تحت هذا العام", fontSize = tanweerTextSize(11f), color = TextMuted)
                                    } else {
                                        subjContents.take(5).forEach { c ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MidnightBackground.copy(alpha = 0.5f))
                                                    .clickable { previewContent = c }
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("  • ${c.studyDate}: ${c.title} (${c.authorName})", fontSize = tanweerTextSize(12f), color = TextSecondary, modifier = Modifier.weight(1f))
                                                Text("👁️", fontSize = tanweerTextSize(12f))
                                            }
                                        }
                                    }

                                    Text("📝 الواجبات والمشاريع (${subjHomeworks.size}):", fontSize = tanweerTextSize(12f), fontWeight = FontWeight.Bold, color = WarmAmber)
                                    if (subjHomeworks.isEmpty()) {
                                        Text("لا توجد واجبات مسجلة", fontSize = tanweerTextSize(11f), color = TextMuted)
                                    } else {
                                        subjHomeworks.take(5).forEach { hw ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MidnightBackground.copy(alpha = 0.5f))
                                                    .clickable { previewHomework = hw }
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("  • ${hw.title} (التسليم: ${hw.dueDate})", fontSize = tanweerTextSize(12f), color = TextSecondary, modifier = Modifier.weight(1f))
                                                Text("👁️", fontSize = tanweerTextSize(12f))
                                            }
                                        }
                                    }

                                    Text("🔴 الاختبارات والتقييمات (${subjExams.size}):", fontSize = tanweerTextSize(12f), fontWeight = FontWeight.Bold, color = RubyRed)
                                    if (subjExams.isEmpty()) {
                                        Text("لا توجد اختبارات مسجلة", fontSize = tanweerTextSize(11f), color = TextMuted)
                                    } else {
                                        subjExams.take(5).forEach { ex ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MidnightBackground.copy(alpha = 0.5f))
                                                    .clickable { previewExam = ex }
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("  • ${ex.examDate}: ${ex.title}", fontSize = tanweerTextSize(12f), color = TextSecondary, modifier = Modifier.weight(1f))
                                                Text("👁️", fontSize = tanweerTextSize(12f))
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = { viewModel.openSubjectKnowledgeBase(subject.id) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = subjColor.copy(alpha = 0.2f)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("فتح مساحة المادة الكاملة 🚀", fontSize = tanweerTextSize(12f), color = subjColor, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Previews for Academic Archive Items
    previewContent?.let { content ->
        AcademicContentPreviewDialog(
            content = content,
            onDismiss = { previewContent = null }
        )
    }

    previewHomework?.let { hw ->
        AcademicHomeworkPreviewDialog(
            homework = hw,
            onDismiss = { previewHomework = null }
        )
    }

    previewExam?.let { ex ->
        AcademicExamPreviewDialog(
            exam = ex,
            onDismiss = { previewExam = null }
        )
    }
}

@Composable
fun AcademicContentPreviewDialog(
    content: com.magd.tanweer.data.model.ContentItem,
    onDismiss: () -> Unit
) {
    val mediaUrls = content.media.map { it.url }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
            ) {
                Text("إغلاق المعاينة", color = TextOnAccent)
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(content.subjectIcon.ifEmpty { "📚" }, fontSize = tanweerTextSize(22f))
                    Column {
                        Text(
                            text = content.subjectName,
                            fontSize = tanweerTextSize(13f),
                            color = CyanAccent,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = content.title,
                            fontSize = tanweerTextSize(15f),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("📅 تاريخ الدرس: ${content.studyDate}", fontSize = tanweerTextSize(12f), color = TextSecondary)
                    Text("👤 الموثق: ${content.authorName}", fontSize = tanweerTextSize(12f), color = TextSecondary)
                }

                if (content.authorGradeSection.isNotBlank()) {
                    Text("🏫 الشعبة: ${content.authorGradeSection}", fontSize = tanweerTextSize(11f), color = TextMuted)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MidnightBackground.copy(alpha = 0.8f))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("محتوى الدرس والشرح:", fontSize = tanweerTextSize(11f), color = TextMuted)
                        Text(
                            text = (content.description ?: "").ifBlank { "لا يوجد وصف إضافي مكتوب لهذا الدرس." },
                            fontSize = tanweerTextSize(13f),
                            color = TextPrimary
                        )
                    }
                }

                if (mediaUrls.isNotEmpty()) {
                    Text("📎 المرفقات والصور (${mediaUrls.size}):", fontSize = tanweerTextSize(12f), color = WarmAmber, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for ((index, _) in mediaUrls.withIndex()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(GlassSurface)
                                    .border(1.dp, GlassBorderSubtle, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("📷 مرفق ${index + 1}", fontSize = tanweerTextSize(11f), color = CyanAccent)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("👁️ المشاهدات: ${content.viewsCount}", fontSize = tanweerTextSize(11f), color = TextMuted)
                    Text("👍 المفيدة: ${content.usefulCount}", fontSize = tanweerTextSize(11f), color = TextMuted)
                }
            }
        },
        containerColor = MidnightSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun AcademicHomeworkPreviewDialog(
    homework: com.magd.tanweer.data.model.HomeworkItem,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = WarmAmber)
            ) {
                Text("إغلاق", color = TextOnAccent)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📝", fontSize = tanweerTextSize(22f))
                Column {
                    Text(text = "واجب مادة ${homework.subjectName}", fontSize = tanweerTextSize(13f), color = WarmAmber, fontWeight = FontWeight.Bold)
                    Text(text = homework.title, fontSize = tanweerTextSize(15f), color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("📅 تاريخ التسليم: ${homework.dueDate}", fontSize = tanweerTextSize(12f), color = TextSecondary)
                    val isOfficialHw = homework.taskType == "OFFICIAL" || homework.taskType == "HOMEWORK"
                    GlassPill(
                        text = if (isOfficialHw) "واجب رسمي معتمد ⭐" else "واجب تشاركي",
                        color = if (isOfficialHw) WarmAmber else CyanAccent,
                        bgColor = (if (isOfficialHw) WarmAmber else CyanAccent).copy(alpha = 0.15f)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MidnightBackground.copy(alpha = 0.8f))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("الأسئلة والصفحات المطلوبة:", fontSize = tanweerTextSize(11f), color = TextMuted)
                        val hwDesc = homework.details ?: homework.questionNumbers ?: homework.pageNumbers ?: ""
                        Text(
                            text = hwDesc.ifBlank { "لا توجد تفاصيل إضافية مسجلة للواجب." },
                            fontSize = tanweerTextSize(13f),
                            color = TextPrimary
                        )
                    }
                }
            }
        },
        containerColor = MidnightSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun AcademicExamPreviewDialog(
    exam: com.magd.tanweer.data.model.ExamItem,
    onDismiss: () -> Unit
) {
    val examTypeAr = when (exam.examType.uppercase()) {
        "MONTHLY" -> "اختبار شهري"
        "QUIZ" -> "كويز قصير"
        "ORAL" -> "اختبار شفهي 🗣️"
        "MIDTERM" -> "نصف العام"
        "FINAL" -> "نهاية العام"
        else -> exam.examType
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = RubyRed)
            ) {
                Text("إغلاق", color = TextOnAccent)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📑", fontSize = tanweerTextSize(22f))
                Column {
                    Text(text = "اختبار مادة ${exam.subjectName}", fontSize = tanweerTextSize(13f), color = RubyRed, fontWeight = FontWeight.Bold)
                    Text(text = exam.title, fontSize = tanweerTextSize(15f), color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("📅 موعد الاختبار: ${exam.examDate}", fontSize = tanweerTextSize(12f), color = TextSecondary)
                    GlassPill(
                        text = examTypeAr,
                        color = RubyRed,
                        bgColor = RubyRed.copy(alpha = 0.15f)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MidnightBackground.copy(alpha = 0.8f))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("المنهج والملاحظات المشمولة في الاختبار:", fontSize = tanweerTextSize(11f), color = TextMuted)
                        Text(
                            text = (exam.notes ?: "").ifBlank { "كافة الدروس السابقة مشمولة ضمن الاختبار." },
                            fontSize = tanweerTextSize(13f),
                            color = TextPrimary
                        )
                    }
                }
            }
        },
        containerColor = MidnightSurface,
        shape = RoundedCornerShape(16.dp)
    )
}
