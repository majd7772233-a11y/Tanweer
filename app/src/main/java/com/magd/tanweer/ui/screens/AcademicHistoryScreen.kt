package com.magd.tanweer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

@Composable
fun AcademicHistoryScreen(
    viewModel: TanweerViewModel
) {
    BackHandler {
        viewModel.setSubScreen(SubScreen.NONE)
    }

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedAcademicYear.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId ?: "" }

    val gradeSubjects = remember(currentUser?.gradeId) {
        SchoolHierarchy.getSubjectsForGrade(currentUser?.gradeId ?: 11)
    }

    // Load full historical content across all dates, plus homeworks and exams
    val allContents by viewModel.repository.getAllContents(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val allHomeworks by viewModel.repository.getHomeworks(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val allExams by viewModel.repository.getExams(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val academicYears = listOf("2026-2027", "2025-2026", "2024-2025")

    // Mode: 0 = إنجازاتي الشخصية, 1 = أرشيف الشعبة
    var viewMode by remember { mutableIntStateOf(0) }

    // Helper to match dates with school years
    fun isDateInAcademicYear(dateStr: String, year: String): Boolean {
        if (dateStr.isBlank()) return false
        val parts = year.split("-")
        if (parts.size != 2) return true
        val startYear = parts[0].trim()
        val endYear = parts[1].trim()
        val dateYear = dateStr.take(4)
        return when (year) {
            "2026-2027" -> dateYear == "2026" || dateYear == "2027"
            "2025-2026" -> dateYear == "2025" || (dateYear == "2026" && dateStr < "2026-08-01")
            "2024-2025" -> dateYear == "2024" || (dateYear == "2025" && dateStr < "2025-08-01")
            else -> dateStr.startsWith(startYear) || dateStr.startsWith(endYear)
        }
    }

    // Filtered by selected year
    val yearContents = remember(allContents, selectedYear) {
        allContents.filter { isDateInAcademicYear(it.studyDate, selectedYear) }
    }
    val yearHomeworks = remember(allHomeworks, selectedYear) {
        allHomeworks.filter { isDateInAcademicYear(it.dueDate, selectedYear) || isDateInAcademicYear(it.studyDate, selectedYear) }
    }
    val yearExams = remember(allExams, selectedYear) {
        allExams.filter { isDateInAcademicYear(it.examDate, selectedYear) }
    }

    // Personal achievements filtering
    val myPersonalLessons = remember(yearContents, currentUser) {
        yearContents.filter { it.authorName.isNotBlank() && it.authorName == currentUser?.fullName }
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
            Column {
                Text(
                    text = "الأرشيف الأكاديمي 🏛️",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "السجل التراكمي للإنجازات والدروس والمواد",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        // Academic Year Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            academicYears.forEach { yr ->
                val isCurrent = yr == "2026-2027"
                val isSelected = yr == selectedYear

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) CyanGlow.copy(alpha = 0.25f) else GlassSurface)
                        .border(
                            1.dp,
                            if (isSelected) CyanAccent else GlassBorderSubtle,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { viewModel.setSelectedAcademicYear(yr) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = yr,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) CyanAccent else TextSecondary
                        )
                        if (isCurrent) {
                            Text("📍", fontSize = 11.sp)
                        } else {
                            Text("📦", fontSize = 10.sp)
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
                    fontSize = 12.sp,
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
                    fontSize = 12.sp,
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
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent
                            )
                            Text(
                                text = if (selectedYear == "2026-2027") "العام الدراسي النشط حالياً" else "سجل مؤرشف ومحفوظ للرجوع إليه",
                                fontSize = 12.sp,
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
                            Text("📦", fontSize = 32.sp)
                            Text(
                                text = "لا توجد سجلات محفوظة للشعبة في عام $selectedYear",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "يتم حفظ السجلات وأرشفتها تلقائياً مع انتهاء الفصول والأعوام الدراسية. يمكنك استعراض العام الدراسي الحالي (2026-2027).",
                                fontSize = 12.sp,
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
                                    fontSize = 14.sp,
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
                                    Text("${myCompletedHomeworks.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                                    Text("واجبات منجزة", fontSize = 11.sp, color = TextSecondary)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${myPersonalLessons.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                                    Text("دروس وثقتها", fontSize = 11.sp, color = TextSecondary)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${yearHomeworks.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WarmAmber)
                                    Text("إجمالي الواجبات", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }

                // Personal Lessons
                item {
                    Text(
                        text = "📖 الدروس التي وثقتها شخصياً (${myPersonalLessons.size})",
                        fontSize = 13.sp,
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
                                fontSize = 12.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                } else {
                    items(myPersonalLessons, key = { it.id }) { lesson ->
                        GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${lesson.subjectIcon} ${lesson.subjectName}: ${lesson.title}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "تاريخ الحصة: ${lesson.studyDate} • ${lesson.viewsCount} مشاهدة",
                                        fontSize = 11.sp,
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
                        fontSize = 13.sp,
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
                                fontSize = 12.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                } else {
                    items(myCompletedHomeworks, key = { it.id }) { hw ->
                        GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "📝 واجب ${hw.subjectName}: ${hw.title}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "تاريخ التسليم: ${hw.dueDate}",
                                        fontSize = 11.sp,
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
                                        Text(subject.icon, fontSize = 18.sp)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(subject.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(
                                            text = "${subjContents.size} دروس • ${subjHomeworks.size} واجبات • ${subjExams.size} اختبارات",
                                            fontSize = 11.sp,
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

                                    Text("📚 الدروس الموثقة (${subjContents.size}):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                                    if (subjContents.isEmpty()) {
                                        Text("لا توجد دروس مسجلة تحت هذا العام", fontSize = 11.sp, color = TextMuted)
                                    } else {
                                        subjContents.take(5).forEach { c ->
                                            Text("  • ${c.studyDate}: ${c.title} (${c.authorName})", fontSize = 12.sp, color = TextSecondary)
                                        }
                                    }

                                    Text("📝 الواجبات والمشاريع (${subjHomeworks.size}):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WarmAmber)
                                    if (subjHomeworks.isEmpty()) {
                                        Text("لا توجد واجبات مسجلة", fontSize = 11.sp, color = TextMuted)
                                    } else {
                                        subjHomeworks.take(5).forEach { hw ->
                                            Text("  • ${hw.title} (التسليم: ${hw.dueDate})", fontSize = 12.sp, color = TextSecondary)
                                        }
                                    }

                                    Text("🔴 الاختبارات والتقييمات (${subjExams.size}):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RubyRed)
                                    if (subjExams.isEmpty()) {
                                        Text("لا توجد اختبارات مسجلة", fontSize = 11.sp, color = TextMuted)
                                    } else {
                                        subjExams.take(5).forEach { ex ->
                                            Text("  • ${ex.examDate}: ${ex.title}", fontSize = 12.sp, color = TextSecondary)
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
                                        Text("فتح مساحة المادة الكاملة 🚀", fontSize = 12.sp, color = subjColor, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
