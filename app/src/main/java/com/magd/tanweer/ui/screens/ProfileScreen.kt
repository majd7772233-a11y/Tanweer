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
import com.magd.tanweer.data.model.Role
import android.content.Intent
import android.net.Uri
import com.magd.tanweer.data.model.SubmitRoleUpgradeResponse
import com.magd.tanweer.data.model.SchoolHierarchy
import com.magd.tanweer.data.model.getRoleEnum
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

    val myRoleRequests by viewModel.myRoleRequests.collectAsStateWithLifecycle()
    var isRoleUpgradeDialogOpen by remember { mutableStateOf(false) }
    var isRedeemCodeDialogOpen by remember { mutableStateOf(false) }
    var requestSuccessInfo by remember { mutableStateOf<SubmitRoleUpgradeResponse?>(null) }

    LaunchedEffect(Unit) {
        viewModel.repository.syncProfile()
        viewModel.loadMyRoleRequests()
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
                        val roleEnum = currentUser?.getRoleEnum() ?: Role.STUDENT
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
                                Text(text = roleEnum.badgeIcon, fontSize = 28.sp)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = currentUser?.fullName ?: "طالب تنوير",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    GlassPill(
                                        text = "${roleEnum.badgeIcon} ${roleEnum.displayNameAr}",
                                        color = when (roleEnum) {
                                            Role.SYSTEM_OWNER, Role.ADMIN -> WarmAmber
                                            Role.TEACHER -> EmeraldGreen
                                            Role.MODERATOR -> CyanAccent
                                            Role.STUDENT -> TextSecondary
                                        },
                                        bgColor = MidnightBackground
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$gradeName — شعبة $sectionAr",
                                    fontSize = 13.sp,
                                    color = CyanAccent,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "📱 رقم الهاتف: ${currentUser?.phoneNumber ?: ""}",
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

        // Unified Role & Authority Card (نواة الصلاحيات)
        item {
            val userRole = currentUser?.getRoleEnum() ?: Role.STUDENT
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(userRole.badgeIcon, fontSize = 22.sp)
                            Column {
                                Text(
                                    text = "الرتبة في المنظومة: ${userRole.displayNameAr}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "المستوى ${userRole.level} من 5 في الهيكل الإداري",
                                    fontSize = 11.sp,
                                    color = CyanAccent
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GlassOutlinedButton(
                                text = "تفعيل رمز 🔑",
                                onClick = { isRedeemCodeDialogOpen = true },
                                modifier = Modifier.height(34.dp)
                            )
                            if (userRole == Role.STUDENT || userRole == Role.MODERATOR) {
                                GlassButton(
                                    text = "طلب ترقية 🎓",
                                    onClick = { isRoleUpgradeDialogOpen = true },
                                    modifier = Modifier.height(34.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = userRole.descriptionAr,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MidnightBackground.copy(alpha = 0.6f))
                            .border(1.dp, GlassBorderSubtle, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "💡 فلسفة تنوير: المساهمة في إضافة الدروس والصور والواجبات مفتوحة للجميع بالتساوي، بينما تمنح الرتبة سلطات الاعتماد والمراجعة والإشراف.",
                            fontSize = 10.sp,
                            color = TextMuted,
                            lineHeight = 14.sp
                        )
                    }

                    // Display pending/recent role request status if any
                    if (myRoleRequests.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("حالة طلبات الترقية السابقة:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        myRoleRequests.take(2).forEach { req ->
                            val statusLabel = when (req.status) {
                                "APPROVED" -> "تمت الموافقة والاعتماد ✅"
                                "REJECTED" -> "مرفوض ❌"
                                else -> "قيد المراجعة والتدقيق ⏳"
                            }
                            val statusColor = when (req.status) {
                                "APPROVED" -> EmeraldGreen
                                "REJECTED" -> RubyRed
                                else -> WarmAmber
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "طلب رتبة: ${Role.fromString(req.requestedRole).displayNameAr}",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                GlassPill(text = statusLabel, color = statusColor, bgColor = statusColor.copy(alpha = 0.15f))
                            }
                        }
                    }

                    // Quick access to Role Dashboard if eligible
                    if (userRole == Role.TEACHER || userRole == Role.MODERATOR || userRole == Role.ADMIN || userRole == Role.SYSTEM_OWNER) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val dashboardTitle = when (userRole) {
                            Role.TEACHER -> "🎓 فتح مركز التعليم للأستاذ المعتمد"
                            Role.MODERATOR -> "🛡️ فتح مركز إشراف الشعبة والتدقيق"
                            Role.ADMIN, Role.SYSTEM_OWNER -> "👑 فتح لوحة مدير المدرسة"
                            else -> "مركز الإدارة"
                        }
                        val targetSubScreen = when (userRole) {
                            Role.TEACHER -> com.magd.tanweer.ui.SubScreen.TEACHER_DASHBOARD
                            Role.MODERATOR -> com.magd.tanweer.ui.SubScreen.MODERATOR_DASHBOARD
                            Role.ADMIN, Role.SYSTEM_OWNER -> com.magd.tanweer.ui.SubScreen.ADMIN_DASHBOARD
                            else -> com.magd.tanweer.ui.SubScreen.NONE
                        }
                        GlassButton(
                            text = dashboardTitle,
                            color = CyanAccent,
                            textColor = TextOnAccent,
                            onClick = { viewModel.setSubScreen(targetSubScreen) },
                            modifier = Modifier.fillMaxWidth().height(42.dp)
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

            val isLessonDoc = lessonsCount >= 1 || photosCount >= 3
            val isHelper = issuesCount >= 1 || homeworksCount >= 2
            val isBestAnswerOwner = issuesCount >= 2
            val isMonthContributor = (lessonsCount + photosCount + homeworksCount + issuesCount) >= 5

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
                            text = "🎖️ شارات الإنجاز والنشاط الموثقة",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "تُحسب تلقائياً من مساهماتك",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BadgeItem(name = "موثق الدروس", icon = "📚", active = isLessonDoc, modifier = Modifier.weight(1f))
                        BadgeItem(name = "مساعد الشعبة", icon = "🛡️", active = isHelper, modifier = Modifier.weight(1f))
                        BadgeItem(name = "صاحب أفضل إجابة", icon = "⭐", active = isBestAnswerOwner, modifier = Modifier.weight(1f))
                        BadgeItem(name = "مساهم الشهر", icon = "🌟", active = isMonthContributor, modifier = Modifier.weight(1f))
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

    if (isRoleUpgradeDialogOpen) {
        val userRole = currentUser?.getRoleEnum() ?: Role.STUDENT
        RoleUpgradeRequestDialog(
            currentRole = userRole,
            onDismiss = { isRoleUpgradeDialogOpen = false },
            onSubmit = { targetRole, reason ->
                viewModel.submitRoleUpgradeRequest(targetRole, reason) { success, resp, msg ->
                    if (success && resp != null) {
                        isRoleUpgradeDialogOpen = false
                        requestSuccessInfo = resp
                    } else {
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    requestSuccessInfo?.let { info ->
        RoleRequestSuccessDialog(
            info = info,
            onDismiss = { requestSuccessInfo = null },
            onOpenWhatsApp = {
                val url = info.whatsappUrl ?: "https://wa.me/967735465673"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            },
            onOpenRedeemDialog = {
                requestSuccessInfo = null
                isRedeemCodeDialogOpen = true
            }
        )
    }

    if (isRedeemCodeDialogOpen) {
        RedeemRoleCodeDialog(
            onDismiss = { isRedeemCodeDialogOpen = false },
            onRedeem = { code ->
                viewModel.redeemRoleCode(code) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    if (success) {
                        isRedeemCodeDialogOpen = false
                    }
                }
            }
        )
    }
}

@Composable
fun RoleRequestSuccessDialog(
    info: SubmitRoleUpgradeResponse,
    onDismiss: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    onOpenRedeemDialog: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📋", fontSize = 22.sp)
                Column {
                    Text("تم إرسال الطلب بنجاح", color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("الرتبة المطلوبة: ${info.targetRoleNameAr ?: "أستاذ / مسؤول"}", color = TextSecondary, fontSize = 11.sp)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MidnightBackground)
                        .border(1.dp, GlassBorderSubtle, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("رقم الطلب الخاص بك", fontSize = 11.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = info.requestId ?: "TNV-8F294",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = CyanAccent
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        GlassPill(text = "قيد المراجعة ⏳", color = WarmAmber, bgColor = WarmAmber.copy(alpha = 0.15f))
                    }
                }

                Text(
                    text = "يرجى التواصل مع إدارة المنظومة عبر WhatsApp لتأكيد هويتك واعتماد الرتبة واستلام رمز التفعيل السري.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )

                Button(
                    onClick = onOpenWhatsApp,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("💬", fontSize = 16.sp)
                        Text("التواصل على WhatsApp (+967 735465673)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MidnightBackground)
                    }
                }

                GlassOutlinedButton(
                    text = "وصلني الرمز؟ إدخال رمز التفعيل 🔑",
                    onClick = onOpenRedeemDialog,
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                )
            }
        },
        confirmButton = {
            GlassButton(text = "حسناً", onClick = onDismiss)
        },
        containerColor = MidnightSurface,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
fun RedeemRoleCodeDialog(
    onDismiss: () -> Unit,
    onRedeem: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🔑", fontSize = 22.sp)
                Column {
                    Text("تفعيل الرتبة بالرمز السري", color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("رمز معتمد من إدارة المنظومة (صالح 24 ساعة)", color = TextSecondary, fontSize = 11.sp)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("أدخل رمز التفعيل المكون من 8 أرقام:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                OutlinedTextField(
                    value = code,
                    onValueChange = { 
                        if (it.length <= 10) code = it 
                    },
                    placeholder = { Text("مثال: 7391-8426", fontSize = 14.sp, color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = GlassBorderSubtle,
                        focusedTextColor = CyanAccent,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Text(
                    text = "🔒 الرمز صالح للاستخدام لمرة واحدة فقط ومقيد بحسابك ومحمي بحد أقصى 5 محاولات.",
                    fontSize = 10.sp,
                    color = TextMuted,
                    lineHeight = 14.sp
                )
            }
        },
        confirmButton = {
            GlassButton(
                text = if (isSubmitting) "جاري التحقق..." else "تفعيل الرتبة ✨",
                enabled = code.trim().replace("-", "").length == 8 && !isSubmitting,
                onClick = {
                    isSubmitting = true
                    onRedeem(code.trim())
                }
            )
        },
        dismissButton = {
            GlassOutlinedButton(text = "إلغاء", enabled = !isSubmitting, onClick = onDismiss)
        },
        containerColor = MidnightSurface,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
fun RoleUpgradeRequestDialog(
    currentRole: Role,
    onDismiss: () -> Unit,
    onSubmit: (Role, String) -> Unit
) {
    var targetRole by remember { mutableStateOf(if (currentRole == Role.STUDENT) Role.MODERATOR else Role.TEACHER) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🎓", fontSize = 22.sp)
                Column {
                    Text("طلب ترقية رتبة", color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("إدارة الشعبة والمحتوى الدراسي", color = TextSecondary, fontSize = 11.sp)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("اختر الرتبة المطلوبة:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isMod = targetRole == Role.MODERATOR
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isMod) CyanGlow else MidnightBackground)
                            .border(1.dp, if (isMod) CyanAccent else GlassBorderSubtle, RoundedCornerShape(10.dp))
                            .clickable { targetRole = Role.MODERATOR }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🛡️", fontSize = 20.sp)
                            Text("مسؤول شعبة", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isMod) CyanAccent else TextPrimary)
                            Text("مراجعة وإشراف", fontSize = 9.sp, color = TextSecondary)
                        }
                    }

                    val isTeacher = targetRole == Role.TEACHER
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isTeacher) CyanGlow else MidnightBackground)
                            .border(1.dp, if (isTeacher) CyanAccent else GlassBorderSubtle, RoundedCornerShape(10.dp))
                            .clickable { targetRole = Role.TEACHER }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎓", fontSize = 20.sp)
                            Text("أستاذ معتمد", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isTeacher) CyanAccent else TextPrimary)
                            Text("اعتماد وتثبيت", fontSize = 9.sp, color = TextSecondary)
                        }
                    }
                }

                Text("سبب التقديم ومسؤولياتك:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    placeholder = { Text("وضح سبب طلب الترقية (مثلاً: رائد الفصل، ممثل الشعبة، أو معلم المادة)...", fontSize = 11.sp, color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = GlassBorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    maxLines = 3
                )

                Text(
                    text = "ℹ️ في تنوير، المساهمة مفتوحة لجميع الطلاب بالتساوي. الترقية تضيف صلاحيات المراجعة والاعتماد والإشراف.",
                    fontSize = 10.sp,
                    color = TextSecondary,
                    lineHeight = 14.sp
                )
            }
        },
        confirmButton = {
            GlassButton(
                text = "إرسال الطلب",
                enabled = reason.isNotBlank(),
                onClick = { onSubmit(targetRole, reason.trim()) }
            )
        },
        dismissButton = {
            GlassOutlinedButton(text = "إلغاء", onClick = onDismiss)
        },
        containerColor = MidnightSurface,
        shape = RoundedCornerShape(18.dp)
    )
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
