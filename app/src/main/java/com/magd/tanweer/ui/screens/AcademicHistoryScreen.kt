package com.magd.tanweer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

    val contents by viewModel.repository.getDayContents(activeGroupId, "")
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val homeworks by viewModel.repository.getHomeworks(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val exams by viewModel.repository.getExams(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val academicYears = listOf("2026-2027", "2025-2026", "2024-2025")

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
                    text = "الأرشيف الأكاديمي الشخصي 🏛️",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "سجل إنجازاتك ودروسك عبر السنوات الدراسية",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        // Academic Year Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            academicYears.forEach { yr ->
                val isCurrent = yr == "2026-2027"
                val isSelected = yr == selectedYear

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) CyanGlow.copy(alpha = 0.2f) else GlassSurface)
                        .border(
                            1.dp,
                            if (isSelected) CyanAccent else GlassBorderSubtle,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { viewModel.setSelectedAcademicYear(yr) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = yr,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) CyanAccent else TextSecondary
                        )
                        if (!isCurrent) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "مغلق",
                                tint = TextMuted,
                                modifier = Modifier.size(12.dp)
                            )
                        } else {
                            Text("🔥", fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // History Tree List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                            Text("أرشيف العام: $selectedYear", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                            Text(
                                text = if (selectedYear == "2026-2027") "العام الدراسي النشط حالياً" else "سجل مؤرَخ ومحفوظ بصورة آمنة 🔒",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        GlassPill(
                            text = "${gradeSubjects.size} مواد",
                            color = EmeraldGreen,
                            bgColor = EmeraldGreen.copy(alpha = 0.12f)
                        )
                    }
                }
            }

            items(gradeSubjects, key = { it.id }) { subject ->
                var expanded by remember { mutableStateOf(false) }

                val subjContents = contents.filter { it.subjectId == subject.id }
                val subjHomeworks = homeworks.filter { it.subjectId == subject.id }
                val subjExams = exams.filter { it.subjectId == subject.id }

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
                                    imageVector = Icons.Default.ChevronRight,
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
                                    subjContents.take(3).forEach { c ->
                                        Text("  • ${c.studyDate}: ${c.title}", fontSize = 12.sp, color = TextSecondary)
                                    }
                                }

                                Text("📝 الواجبات والمشاريع (${subjHomeworks.size}):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WarmAmber)
                                if (subjHomeworks.isEmpty()) {
                                    Text("لا توجد واجبات مسجلة", fontSize = 11.sp, color = TextMuted)
                                } else {
                                    subjHomeworks.take(3).forEach { hw ->
                                        Text("  • ${hw.title} (التسليم: ${hw.dueDate})", fontSize = 12.sp, color = TextSecondary)
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
