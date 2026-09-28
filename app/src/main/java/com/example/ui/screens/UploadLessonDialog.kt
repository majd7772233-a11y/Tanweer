package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FilterDrama
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
import com.example.data.model.DefaultSubjects
import com.example.ui.TanweerViewModel
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassOutlinedButton
import com.example.ui.components.GlassPill
import com.example.ui.components.GlassTextField
import com.example.ui.theme.*

@Composable
fun UploadLessonDialog(
    viewModel: TanweerViewModel,
    initialSubjectId: String? = null,
    onDismiss: () -> Unit
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val gradeSubjects = remember { viewModel.getSubjectsForCurrentGrade() }
    var selectedSubjectId by remember {
        mutableStateOf(initialSubjectId ?: gradeSubjects.firstOrNull()?.id ?: "math")
    }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var studyDate by remember { mutableStateOf(selectedDate) }
    var autoEnhanceBoard by remember { mutableStateOf(true) }
    var pagesCount by remember { mutableIntStateOf(1) }

    val subject = gradeSubjects.find { it.id == selectedSubjectId } ?: gradeSubjects.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📷 توثيق درس ومساهمة",
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                GlassPill(text = "Scanner السبورة", color = CyanAccent, bgColor = CyanGlow)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Backdated Publishing Notice
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MidnightSurface,
                    borderColor = CyanGlow
                ) {
                    Column {
                        Text(
                            text = "🗓️ تاريخ الحصة الموثقة:",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = TanweerViewModel.getFormattedArabicDate(studyDate),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent
                        )
                        Text(
                            text = "يمكنك توثيق الدروس القديمة في أي وقت لتبقى محفوظة في موضعها",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }

                // Subject Selector
                Text("المادة الدراسية:", fontSize = 12.sp, color = TextSecondary)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(gradeSubjects) { subj ->
                        val isSelected = subj.id == selectedSubjectId
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) CyanGlow else MidnightSurface)
                                .border(1.dp, if (isSelected) CyanAccent else GlassBorderSubtle, RoundedCornerShape(10.dp))
                                .clickable { selectedSubjectId = subj.id }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${subj.icon} ${subj.name}",
                                fontSize = 11.sp,
                                color = if (isSelected) CyanAccent else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                GlassTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "عنوان الدرس أو الموضوع",
                    placeholder = "مثال: درس تفاعلات الأكسدة والاختزال"
                )

                GlassTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "ملخص أو ملاحظات الدرس",
                    placeholder = "أهم النقاط التي شرحها المدرس..."
                )

                // Multi-page Scanner & Board enhancement options
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MidnightSurface
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "صفحات الدرس: ($pagesCount صفحات)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            IconButton(
                                onClick = { if (pagesCount < 8) pagesCount++ },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(CyanGlow)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "صفحة أخرى", tint = CyanAccent, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Pages preview pills
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (p in 1..pagesCount) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(GlassSurfaceLight)
                                        .border(1.dp, GlassBorderSubtle, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("صفحة $p 📄", fontSize = 10.sp, color = TextPrimary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "☑ تحسين تلقائي للسبورة", fontSize = 12.sp, color = TextPrimary)
                                Text(text = "قص الحواف، رفع التباين وضغط الحجم", fontSize = 10.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = autoEnhanceBoard,
                                onCheckedChange = { autoEnhanceBoard = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = CyanAccent)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            GlassButton(
                text = "نشر وتوثيق الدرس",
                onClick = {
                    if (title.isNotBlank()) {
                        viewModel.addLesson(
                            subjectId = selectedSubjectId,
                            title = title,
                            description = description.ifBlank { "درس موثق بـ $pagesCount صفحات ومحسن للقراءة" },
                            date = studyDate
                        )
                    }
                }
            )
        },
        dismissButton = {
            GlassOutlinedButton(text = "إلغاء", onClick = onDismiss)
        },
        containerColor = MidnightSurface,
        shape = RoundedCornerShape(20.dp)
    )
}
