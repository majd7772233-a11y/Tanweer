package com.magd.tanweer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.ExamItem
import com.magd.tanweer.data.model.HomeworkItem
import com.magd.tanweer.ui.NavigationTab
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*

@Composable
fun TeacherDashboardScreen(
    viewModel: TanweerViewModel
) {
    val dashboardData by viewModel.teacherDashboard.collectAsStateWithLifecycle()
    val isLoading by viewModel.isDashboardLoading.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadTeacherDashboard()
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Overview & Stats, 1: Official Homeworks, 2: Official Exams

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        // Teacher Hero Header
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MidnightSurface,
                borderColor = CyanAccent.copy(alpha = 0.6f)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(listOf(CyanAccent.copy(alpha = 0.25f), EmeraldGreen.copy(alpha = 0.25f)))
                                    )
                                    .border(1.5.dp, CyanAccent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🎓", fontSize = 26.sp)
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = dashboardData?.teacher?.fullName ?: currentUser?.fullName ?: "الأستاذ المعتمد",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    GlassPill(
                                        text = "أستاذ معتمد ✅",
                                        color = EmeraldGreen,
                                        bgColor = EmeraldGreen.copy(alpha = 0.15f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "مركز التعليم وإدارة المحتوى الأكاديمي الرسمي",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.loadTeacherDashboard() },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GlassSurface)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = CyanAccent, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Stats Grid
                    val stats = dashboardData?.stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TeacherStatCard(
                            title = "واجبات رسمية",
                            count = "${stats?.officialHomeworksCount ?: 0}",
                            icon = "📝",
                            color = CyanAccent,
                            modifier = Modifier.weight(1f)
                        )
                        TeacherStatCard(
                            title = "اختبارات رسمية",
                            count = "${stats?.officialExamsCount ?: 0}",
                            icon = "📑",
                            color = WarmAmber,
                            modifier = Modifier.weight(1f)
                        )
                        TeacherStatCard(
                            title = "إجابات معتمدة",
                            count = "${stats?.teacherAnswersCount ?: 0}",
                            icon = "⭐",
                            color = EmeraldGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Action Buttons Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassButton(
                    text = "إضافة واجب رسمي 📝",
                    color = CyanAccent,
                    textColor = TextOnAccent,
                    onClick = { viewModel.setTab(NavigationTab.HOMEWORK) },
                    modifier = Modifier.weight(1f)
                )
                GlassButton(
                    text = "إضافة اختبار رسمي 📑",
                    color = WarmAmber,
                    textColor = TextOnAccent,
                    onClick = { viewModel.setTab(NavigationTab.EXAMS) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Section Tabs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassSurface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("نظرة عامة 📊", "الواجبات الرسمية 📝", "الاختبارات الرسمية 📑").forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) CyanAccent else Color.Transparent)
                            .clickable { selectedTab = index }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) TextOnAccent else TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Content based on selected Tab
        when (selectedTab) {
            0 -> {
                // Overview & Guidance
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MidnightSurface
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "💡 سلطات وصلاحيات الأستاذ في المنظومة:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = WarmAmber
                            )
                            TeacherGuidelineItem(
                                icon = "📌",
                                title = "الواجبات والاختبارات الرسمية",
                                desc = "أي واجب أو اختبار تنشئه يحمل تلقائياً وسام (معتمد من الأستاذ) وتكون له الأولوية في جداول الطلاب وتنبيهاتهم."
                            )
                            TeacherGuidelineItem(
                                icon = "💬",
                                title = "إجابات الأسئلة المعتمدة",
                                desc = "إجابتك على أي استفسار في قسم الاستفسارات تُعتمد فورياً كـ (Teacher Answer) وتُثبت في أعلى النقاش."
                            )
                            TeacherGuidelineItem(
                                icon = "📅",
                                title = "إدارة وتعديل الجدول المدرسي",
                                desc = "يمكنك تعديل وتثبيت الجدول مباشرة دون الحاجة لفتح تصويت عام، أو اعتماد مقترحات الطلاب بضغطة زر."
                            )
                            TeacherGuidelineItem(
                                icon = "✍️",
                                title = "مراجعة وتصويب الدروس",
                                desc = "لديك الصلاحية الكاملة لاعتماد وتصويب شروحات وتلاخيص الطلاب المرفوعة في المنظومة."
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "آخر الواجبات الرسمية المسجلة:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                val recentHw = dashboardData?.recentHomeworks ?: emptyList()
                if (recentHw.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                            Text(
                                text = "لم تقم بإضافة واجبات بعد، اضغط على زر إضافة واجب للبدء.",
                                fontSize = 12.sp,
                                color = TextMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(12.dp)
                            )
                        }
                    }
                } else {
                    items(recentHw) { hw ->
                        OfficialHomeworkCard(hw = hw)
                    }
                }
            }

            1 -> {
                // Official Homeworks
                val recentHw = dashboardData?.recentHomeworks ?: emptyList()
                if (recentHw.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                            Text(
                                text = "لا توجد واجبات رسمية مضافة حالياً.",
                                fontSize = 13.sp,
                                color = TextMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(recentHw) { hw ->
                        OfficialHomeworkCard(hw = hw)
                    }
                }
            }

            2 -> {
                // Official Exams
                val recentExams = dashboardData?.recentExams ?: emptyList()
                if (recentExams.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                            Text(
                                text = "لا توجد اختبارات رسمية مسجلة حالياً.",
                                fontSize = 13.sp,
                                color = TextMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(recentExams) { exam ->
                        OfficialExamCard(exam = exam)
                    }
                }
            }
        }
    }
}

@Composable
fun TeacherStatCard(
    title: String,
    count: String,
    icon: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MidnightBackground.copy(alpha = 0.6f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(icon, fontSize = 20.sp)
            Text(
                text = count,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
            Text(
                text = title,
                fontSize = 10.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TeacherGuidelineItem(
    icon: String,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(icon, fontSize = 16.sp, modifier = Modifier.padding(top = 2.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = desc, fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp)
        }
    }
}

@Composable
fun OfficialHomeworkCard(hw: HomeworkItem) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MidnightSurface,
        borderColor = CyanAccent.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(hw.subjectIcon, fontSize = 18.sp)
                    Text(
                        text = hw.subjectName.ifBlank { "واجب مدرسي" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                }
                GlassPill(
                    text = "واجب رسمي معتمد 🎓",
                    color = EmeraldGreen,
                    bgColor = EmeraldGreen.copy(alpha = 0.15f)
                )
            }

            Text(
                text = hw.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            if (!hw.details.isNullOrBlank()) {
                Text(
                    text = hw.details,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "📅 التسليم: ${hw.dueDate}",
                    fontSize = 10.sp,
                    color = WarmAmber
                )
                if (!hw.pageNumbers.isNullOrBlank()) {
                    Text(
                        text = "📖 ص: ${hw.pageNumbers}",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
fun OfficialExamCard(exam: ExamItem) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MidnightSurface,
        borderColor = WarmAmber.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(exam.subjectIcon, fontSize = 18.sp)
                    Text(
                        text = exam.subjectName.ifBlank { "اختبار رسمي" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WarmAmber
                    )
                }
                GlassPill(
                    text = "اختبار رسمي معتمد 🎓",
                    color = EmeraldGreen,
                    bgColor = EmeraldGreen.copy(alpha = 0.15f)
                )
            }

            Text(
                text = exam.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            if (!exam.requiredChapters.isNullOrBlank()) {
                Text(
                    text = "📚 الفصول المطلوبة: ${exam.requiredChapters}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }

            Text(
                text = "📅 الموعد: ${exam.examDate}",
                fontSize = 10.sp,
                color = CyanAccent
            )
        }
    }
}
