package com.magd.tanweer.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.*
import com.magd.tanweer.ui.NavigationTab
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

enum class MissedTimeRange(val label: String, val daysBack: Int) {
    YESTERDAY("يوم أمس ⏪", 1),
    LAST_3_DAYS("آخر 3 أيام 📅", 3),
    THIS_WEEK("الأسبوع الحالي 🗓️", 7),
    LAST_TWO_WEEKS("آخر أسبوعين 📚", 14)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatDidIMissScreen(
    viewModel: TanweerViewModel
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId ?: "" }

    var selectedRange by remember { mutableStateOf(MissedTimeRange.LAST_3_DAYS) }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val cutoffDate = remember(selectedRange) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -selectedRange.daysBack)
        sdf.format(cal.time)
    }

    val allHomeworks by viewModel.repository.getHomeworks(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val allExams by viewModel.repository.getExams(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Filter relevant items within missed range
    val missedHomeworks = remember(allHomeworks, cutoffDate) {
        allHomeworks.filter { it.studyDate >= cutoffDate && !it.isCompleted }
    }

    val missedExams = remember(allExams, cutoffDate) {
        allExams.filter { it.examDate >= cutoffDate }
    }

    BackHandler {
        viewModel.setSubScreen(SubScreen.NONE)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header Card
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = GlassSurface
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(CyanGlow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "ماذا فاتني أثناء الغياب؟ 🎒",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "راجع كل الدروس والواجبات والاختبارات التي تمت إضافتها",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Time Range Selector Filter Pills
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(MissedTimeRange.values()) { range ->
                            val isSelected = range == selectedRange
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) CyanAccent else GlassSurface)
                                    .border(1.dp, if (isSelected) CyanAccent else GlassBorderSubtle, RoundedCornerShape(20.dp))
                                    .clickable { selectedRange = range }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = range.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) TextOnAccent else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Summary Statistics Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassCard(
                    modifier = Modifier.weight(1f),
                    backgroundColor = GlassSurface
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "📋", fontSize = 22.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${missedHomeworks.size}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarmAmber
                        )
                        Text(
                            text = "واجبات مستحقة",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                GlassCard(
                    modifier = Modifier.weight(1f),
                    backgroundColor = GlassSurface
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "🎯", fontSize = 22.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${missedExams.size}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = RubyRed
                        )
                        Text(
                            text = "اختبارات قادمة",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                GlassCard(
                    modifier = Modifier.weight(1f),
                    backgroundColor = GlassSurface
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "🗓️", fontSize = 22.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${selectedRange.daysBack}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent
                        )
                        Text(
                            text = "أيام سابقة",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Homework Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "📋", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "الواجبات والمهام المطلوبة",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                TextButton(onClick = { viewModel.setTab(NavigationTab.HOMEWORK) }) {
                    Text(text = "عرض الكل", color = CyanAccent, fontSize = 13.sp)
                }
            }
        }

        if (missedHomeworks.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "✨", fontSize = 24.sp)
                        Text(
                            text = "لا توجد واجبات غير مكتملة في هذه الفترة، عمل رائع!",
                            fontSize = 13.sp,
                            color = EmeraldGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            items(missedHomeworks) { hw ->
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                GlassPill(
                                    text = hw.subjectName,
                                    color = CyanAccent,
                                    bgColor = CyanGlow
                                )
                                Text(
                                    text = "تاريخ الحصة: ${hw.studyDate}",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = hw.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (!hw.details.isNullOrBlank()) {
                                Text(
                                    text = hw.details,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    maxLines = 2
                                )
                            }
                            if (!hw.dueDate.isBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "⏳ موعد التسليم: ${hw.dueDate}",
                                    fontSize = 11.sp,
                                    color = WarmAmber,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.toggleHomework(hw.id, hw.isCompleted) },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(GlassSurface)
                        ) {
                            Icon(
                                imageVector = if (hw.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = "إكمال الواجب",
                                tint = if (hw.isCompleted) EmeraldGreen else TextMuted
                            )
                        }
                    }
                }
            }
        }

        // Exams Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🎯", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "الاختبارات المعلنة",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                TextButton(onClick = { viewModel.setTab(NavigationTab.EXAMS) }) {
                    Text(text = "عرض الكل", color = CyanAccent, fontSize = 13.sp)
                }
            }
        }

        if (missedExams.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "🎉", fontSize = 24.sp)
                        Text(
                            text = "لا توجد اختبارات مجدولة مسبقاً في هذه الفترة",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(missedExams) { exam ->
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
                            GlassPill(
                                text = exam.subjectName,
                                color = RubyRed,
                                bgColor = RubyRed.copy(alpha = 0.15f)
                            )
                            Text(
                                text = "📅 ${exam.examDate}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WarmAmber
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = exam.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (!exam.requiredChapters.isNullOrBlank()) {
                            Text(
                                text = "المقرر: ${exam.requiredChapters}",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }

        // Quick Shortcut Links
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassButton(
                    text = "توثيق درس جديد 📝",
                    onClick = { viewModel.openUploadDialog() },
                    modifier = Modifier.weight(1f)
                )
                GlassOutlinedButton(
                    text = "جدول الحصص 🗓️",
                    onClick = { viewModel.setSubScreen(SubScreen.SCHEDULE) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
