package com.magd.tanweer.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.SchoolHierarchy
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*
import com.magd.tanweer.util.ArabicNameValidator

@Composable
fun ProfileScreen(
    viewModel: TanweerViewModel
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    var isDataSaverEnabled by remember { mutableStateOf(true) }
    var isEditProfileDialogOpen by remember { mutableStateOf(false) }

    val gradeName = currentUser?.gradeId?.let { SchoolHierarchy.getGradeName(it) } ?: "المرحلة الدراسية"
    val sectionAr = currentUser?.sectionId?.let { SchoolHierarchy.getSectionArabicName(it) } ?: ""

    // Edit Profile States
    var editFullName by remember { mutableStateOf("") }
    var editGradeId by remember { mutableIntStateOf(currentUser?.gradeId ?: 11) }
    var editSectionId by remember { mutableStateOf(currentUser?.sectionId ?: "A") }
    var editErrorMessage by remember { mutableStateOf<String?>(null) }
    var isSavingProfile by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.repository.syncProfile()
    }

    LaunchedEffect(currentUser) {
        currentUser?.let {
            editFullName = it.fullName
            editGradeId = it.gradeId
            editSectionId = it.sectionId
        }
    }

    val selectedGradeInfo = SchoolHierarchy.grades.find { it.id == editGradeId } ?: SchoolHierarchy.grades[4]

    LaunchedEffect(editGradeId) {
        if (!selectedGradeInfo.allowedSections.contains(editSectionId)) {
            editSectionId = selectedGradeInfo.allowedSections.firstOrNull() ?: "A"
        }
    }

    val editNameValidation = remember(editFullName) {
        if (editFullName.isEmpty()) null else ArabicNameValidator.validate(editFullName)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // User Identity Card
        item {
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
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(CyanGlow)
                                    .border(1.5.dp, CyanAccent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "👤", fontSize = 28.sp)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = currentUser?.fullName ?: "طالب تنوير",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$gradeName — شعبة $sectionAr",
                                    fontSize = 13.sp,
                                    color = CyanAccent,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "🛡️ رقم الهاتف: ${currentUser?.phoneNumber ?: ""}",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        GlassOutlinedButton(
                            text = "تعديل",
                            icon = Icons.Default.Edit,
                            onClick = {
                                editFullName = currentUser?.fullName ?: ""
                                editGradeId = currentUser?.gradeId ?: 11
                                editSectionId = currentUser?.sectionId ?: "A"
                                editErrorMessage = null
                                isEditProfileDialogOpen = true
                            },
                            modifier = Modifier.height(38.dp)
                        )
                    }
                }
            }
        }

        // Contributions Stats
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MidnightSurface
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "📚 سجل مساهماتي الدراسية في الشعبة",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val stats = currentUser?.stats
                    val lessonsCount = stats?.lessonsCount ?: 0
                    val photosCount = stats?.photosCount ?: 0
                    val homeworksCount = stats?.homeworksCount ?: 0
                    val issuesCount = stats?.issuesCount ?: 0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem(label = "دروس موثقة", count = "$lessonsCount", icon = "📚")
                        StatItem(label = "صور سبورة", count = "$photosCount", icon = "📷")
                        StatItem(label = "واجبات مسجلة", count = "$homeworksCount", icon = "📝")
                        StatItem(label = "استفسارات", count = "$issuesCount", icon = "❓")
                    }
                }
            }
        }

        // Badges Section (Dynamically computed from actual student activity)
        item {
            val stats = currentUser?.stats
            val lessonsCount = stats?.lessonsCount ?: 0
            val photosCount = stats?.photosCount ?: 0
            val homeworksCount = stats?.homeworksCount ?: 0
            val issuesCount = stats?.issuesCount ?: 0
            val userRole = currentUser?.role ?: "MEMBER"

            val isActiveMember = (lessonsCount + homeworksCount + issuesCount) > 0 || currentUser != null
            val isLessonDoc = lessonsCount >= 1 || photosCount >= 3
            val isHelper = issuesCount >= 1 || homeworksCount >= 2 || userRole == "MODERATOR" || userRole == "ADMIN" || userRole == "VERIFIED_TEACHER"

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = GlassSurface
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "🎖️ شارات المساهمة والنشاط",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BadgeItem(name = "عضو نشط", icon = "🌟", active = isActiveMember, modifier = Modifier.weight(1f))
                        BadgeItem(name = "موثق الدروس", icon = "📚", active = isLessonDoc, modifier = Modifier.weight(1f))
                        BadgeItem(name = "مساعد الشعبة", icon = "⭐", active = isHelper, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Navigation shortcuts to Academic History & Knowledge Base
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurface,
                    onClick = { viewModel.setSubScreen(com.magd.tanweer.ui.SubScreen.ACADEMIC_HISTORY) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🏛️", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("الأرشيف الأكاديمي الشخصي", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("تصفح سجلاتك ودروسك للأعوام الدراسية السابقة", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = CyanAccent)
                    }
                }

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurface,
                    onClick = { viewModel.setSubScreen(com.magd.tanweer.ui.SubScreen.SUBJECT_KNOWLEDGE_BASE) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📚", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("مساحة المادة الموحدة (Knowledge Base)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("دروس، واجبات، اختبارات، وأسئلة كل مادة في مكان واحد", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = CyanAccent)
                    }
                }
            }
        }

        // Settings Navigation Card
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = GlassSurface,
                onClick = { viewModel.setSubScreen(com.magd.tanweer.ui.SubScreen.SETTINGS) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyanGlow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "إعدادات التطبيق وتخصيص الخط", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(text = "الخط (12px)، السمة، المزامنة والكاش", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = TextSecondary)
                }
            }
        }

        // Logout
        item {
            GlassOutlinedButton(
                text = "تسجيل الخروج",
                icon = Icons.Default.ExitToApp,
                borderColor = RubyRed.copy(alpha = 0.5f),
                textColor = RubyRed,
                onClick = { viewModel.logout() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Edit Profile Modal Dialog
    if (isEditProfileDialogOpen) {
        AlertDialog(
            onDismissRequest = { if (!isSavingProfile) isEditProfileDialogOpen = false },
            title = {
                Text(
                    text = "✏️ تعديل البيانات الشخصية",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanAccent
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassTextField(
                        value = editFullName,
                        onValueChange = { editFullName = it },
                        label = "الاسم الثلاثي (عربي أو إنجليزي)",
                        placeholder = "مثال: مجد محمد أحمد أو Majd Mohammed Ahmed",
                        leadingIcon = Icons.Default.Person
                    )

                    if (editNameValidation != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (editNameValidation.isValid) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (editNameValidation.isValid) EmeraldGreen else RubyRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (editNameValidation.isValid) "الاسم الثلاثي صالح ومعتمد ✅ (${if (editNameValidation.detectedLanguage == "AR") "عربي" else "إنجليزي"})" else (editNameValidation.errorMessage ?: ""),
                                fontSize = 11.sp,
                                color = if (editNameValidation.isValid) EmeraldGreen else RubyRed
                            )
                        }
                    }

                    // Grade Selection
                    Text(
                        text = "الصف الدراسي:",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SchoolHierarchy.grades.take(3).forEach { g ->
                            val isSel = g.id == editGradeId
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) CyanGlow else GlassSurfaceLight)
                                    .border(1.dp, if (isSel) CyanAccent else GlassBorderSubtle, RoundedCornerShape(8.dp))
                                    .clickable { editGradeId = g.id }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = g.name.replace("الصف ", ""),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) CyanAccent else TextPrimary
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SchoolHierarchy.grades.drop(3).forEach { g ->
                            val isSel = g.id == editGradeId
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) CyanGlow else GlassSurfaceLight)
                                    .border(1.dp, if (isSel) CyanAccent else GlassBorderSubtle, RoundedCornerShape(8.dp))
                                    .clickable { editGradeId = g.id }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = g.name,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) CyanAccent else TextPrimary
                                )
                            }
                        }
                    }

                    // Section Selection
                    Text(
                        text = "الشعبة:",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        selectedGradeInfo.allowedSections.forEach { sec ->
                            val isSel = sec == editSectionId
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) AmberGlow else GlassSurfaceLight)
                                    .border(1.dp, if (isSel) WarmAmber else GlassBorderSubtle, RoundedCornerShape(8.dp))
                                    .clickable { editSectionId = sec }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "شعبة (${SchoolHierarchy.getSectionArabicName(sec)})",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) WarmAmber else TextPrimary
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = editErrorMessage != null) {
                        Text(
                            text = editErrorMessage ?: "",
                            color = RubyRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                GlassButton(
                    text = if (isSavingProfile) "جاري الحفظ..." else "حفظ التعديلات",
                    enabled = !isSavingProfile,
                    onClick = {
                        val nameVal = ArabicNameValidator.validate(editFullName)
                        if (!nameVal.isValid) {
                            editErrorMessage = nameVal.errorMessage
                            return@GlassButton
                        }

                        isSavingProfile = true
                        editErrorMessage = null
                        viewModel.updateProfile(
                            fullName = editFullName.trim(),
                            gradeId = editGradeId,
                            sectionId = editSectionId,
                            onSuccess = {
                                isSavingProfile = false
                                isEditProfileDialogOpen = false
                                Toast.makeText(context, "تم حفظ وتحديث بيانات الحساب بنجاح ✨", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err ->
                                isSavingProfile = false
                                editErrorMessage = err
                            }
                        )
                    }
                )
            },
            dismissButton = {
                GlassOutlinedButton(
                    text = "إلغاء",
                    enabled = !isSavingProfile,
                    onClick = { isEditProfileDialogOpen = false }
                )
            },
            containerColor = MidnightSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun StatItem(label: String, count: String, icon: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = icon, fontSize = 20.sp)
        Text(text = count, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
        Text(text = label, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
fun BadgeItem(name: String, icon: String, active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MidnightSurface)
            .border(1.dp, if (active) CyanGlow else GlassBorderSubtle, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = icon, fontSize = 22.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) TextPrimary else TextMuted
            )
        }
    }
}
