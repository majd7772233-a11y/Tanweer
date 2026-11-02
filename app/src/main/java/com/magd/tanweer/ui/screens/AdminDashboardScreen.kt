package com.magd.tanweer.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.UserBrief
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*

@Composable
fun AdminDashboardScreen(
    viewModel: TanweerViewModel
) {
    val context = LocalContext.current
    val dashboardData by viewModel.adminDashboard.collectAsStateWithLifecycle()
    val isLoading by viewModel.isDashboardLoading.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val communityDecisions by viewModel.communityDecisions.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Operations & Governance, 1: Teachers, 2: Moderators & Users, 3: Analytics

    var showBroadcastDialog by remember { mutableStateOf(false) }
    var broadcastTitle by remember { mutableStateOf("") }
    var broadcastContent by remember { mutableStateOf("") }
    var isPublishingBroadcast by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadAdminDashboard()
        viewModel.loadCommunityDecisions()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp)
    ) {
        // School Admin Header Card
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MidnightSurface,
                borderColor = Color(0xFFFFD700).copy(alpha = 0.6f)
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
                                        Brush.linearGradient(
                                            listOf(
                                                Color(0xFFFFD700).copy(alpha = 0.25f),
                                                CyanAccent.copy(alpha = 0.25f)
                                            )
                                        )
                                    )
                                    .border(1.5.dp, Color(0xFFFFD700), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("👑", fontSize = 26.sp)
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = dashboardData?.school?.adminName ?: currentUser?.fullName ?: "مدير المدرسة",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    GlassPill(
                                        text = "مدير المدرسة 👑",
                                        color = Color(0xFFFFD700),
                                        bgColor = Color(0xFFFFD700).copy(alpha = 0.15f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = dashboardData?.school?.name ?: "مدرسة تنوير النموذجية — مركز الإدارة الشاملة",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                viewModel.loadAdminDashboard()
                                viewModel.loadCommunityDecisions()
                                Toast.makeText(context, "تم تحديث بيانات المنظومة 🔄", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GlassSurface)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = CyanAccent, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick Actions Row for Director
                    GlassButton(
                        text = "تعميم رسمي 📢",
                        onClick = { showBroadcastDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // School Overview Stats Grid
                    val stats = dashboardData?.stats
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TeacherStatCard(
                                title = "إجمالي الطلاب",
                                count = "${stats?.totalStudents ?: 0}",
                                icon = "🎒",
                                color = CyanAccent,
                                modifier = Modifier.weight(1f)
                            )
                            TeacherStatCard(
                                title = "الأساتذة المعتمدون",
                                count = "${stats?.totalTeachers ?: 0}",
                                icon = "🎓",
                                color = EmeraldGreen,
                                modifier = Modifier.weight(1f)
                            )
                            TeacherStatCard(
                                title = "المشرفون",
                                count = "${stats?.totalModerators ?: 0}",
                                icon = "🛡️",
                                color = WarmAmber,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TeacherStatCard(
                                title = "الحسابات المسجلة",
                                count = "${stats?.totalUsers ?: 0}",
                                icon = "👥",
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            TeacherStatCard(
                                title = "الشعب الدراسية",
                                count = "${stats?.totalGroups ?: 0}",
                                icon = "🏫",
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            TeacherStatCard(
                                title = "الدروس الموثقة",
                                count = "${stats?.totalLessons ?: 0}",
                                icon = "📚",
                                color = CyanAccent,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TeacherStatCard(
                                title = "الواجبات",
                                count = "${stats?.totalHomeworks ?: 0}",
                                icon = "📝",
                                color = WarmAmber,
                                modifier = Modifier.weight(1f)
                            )
                            TeacherStatCard(
                                title = "الاختبارات",
                                count = "${stats?.totalExams ?: 0}",
                                icon = "📋",
                                color = EmeraldGreen,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Navigation Tabs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassSurface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "العمليات ⚡",
                    "المعلمون 🎓",
                    "المشرفون 🛡️",
                    "الطلاب 🎒",
                    "السياسات 🏛️"
                ).forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFFFFD700) else Color.Transparent)
                            .clickable { selectedTab = index }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MidnightBackground else TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Tab Content
        when (selectedTab) {
            0 -> {
                // Tab 0: Direct Administrative Operations & Community Governance
                item {
                    Text(
                        text = "🗳️ قرارات الحوكمة والتصويتات المعلقة (${communityDecisions.filter { it.status == "PENDING" }.size}):",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                }

                val pendingDecisions = communityDecisions.filter { it.status == "PENDING" }
                if (pendingDecisions.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                            Text(
                                text = "لا توجد قرارات معلقة تتطلب تدخلاً إدارياً حالياً ✓",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                            )
                        }
                    }
                } else {
                    items(pendingDecisions) { decision ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = MidnightSurface,
                            borderColor = WarmAmber.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = decision.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    GlassPill(text = "معلق للتصويت", color = WarmAmber, bgColor = WarmAmber.copy(alpha = 0.15f))
                                }
                                Text(
                                    text = decision.description ?: "قرار مطروح على تصويت الشعبة",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                val reqVotes = (Math.ceil(decision.totalEligibleVoters * decision.thresholdPercent / 100.0)).toInt().coerceAtLeast(1)
                                Text(
                                    text = "الأصوات: ${decision.votesFor} مؤيد • ${decision.votesAgainst} معارض (الهدف: $reqVotes)",
                                    fontSize = 11.sp,
                                    color = CyanAccent
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    GlassButton(
                                        text = "اعتماد القرار وتطبيقه ⚡",
                                        onClick = {
                                            viewModel.voteCommunityDecision(decision.id, 1) { success, msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                viewModel.loadCommunityDecisions()
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    GlassOutlinedButton(
                                        text = "رفض القرار ✕",
                                        onClick = {
                                            viewModel.voteCommunityDecision(decision.id, -1) { success, msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                viewModel.loadCommunityDecisions()
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Shortcuts to School Management Hubs
                item {
                    Text(
                        text = "🏫 الوصول المباشر لإدارة المجموعات والمحتوى:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = GlassSurface,
                        onClick = { viewModel.setSubScreen(SubScreen.GROUPS) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("🏫", fontSize = 22.sp)
                                Column {
                                    Text("إدارة المجموعات والشعب المدرسية", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text("عرض كل الشعب، تعديل الأعضاء، وتعيين مشرفي الفصول", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = CyanAccent)
                        }
                    }
                }

                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = GlassSurface,
                        onClick = { viewModel.setSubScreen(SubScreen.COMMUNITY_DECISIONS) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("🗳️", fontSize = 22.sp)
                                Column {
                                    Text("محرك الحوكمة والقرارات المدرسية", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text("متابعة كافة تصويتات الحذف والتصويب ومقترحات الجداول", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = EmeraldGreen)
                        }
                    }
                }
            }

            1 -> {
                // Tab 1: Teachers List & Management
                val teachers = dashboardData?.teachers ?: emptyList()
                if (teachers.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🎓", fontSize = 32.sp)
                                Text(
                                    text = "لا يوجد معلمون مسجلون حالياً",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "يُصدر رمز الاعتماد الفعلي من مسار طلبات الترقية الرسمي في Dashboard، ثم يفعّله الأستاذ من خيار «تفعيل رمز» في ملفه الشخصي.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(teachers) { teacher ->
                        AdminUserManageCard(
                            user = teacher,
                            roleBadge = "أستاذ معتمد 🎓",
                            roleColor = EmeraldGreen
                        )
                    }
                }
            }

            2 -> {
                // Tab 2: Moderators List & Direct User Promotion
                val moderators = dashboardData?.moderators ?: emptyList()
                if (moderators.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🛡️", fontSize = 32.sp)
                                Text(
                                    text = "لا يوجد مشرفون معينون حالياً",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "ستظهر هنا الحسابات التي لديها عضوية إشراف نشطة في شعبة واحدة أو أكثر، دون تكرار الحساب.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(moderators) { mod ->
                        AdminUserManageCard(
                            user = mod,
                            roleBadge = "مشرف الشعبة 🛡️",
                            roleColor = WarmAmber
                        )
                    }
                }
            }

            3 -> {
                val students = dashboardData?.students ?: emptyList()
                if (students.isEmpty()) {
                    item {
                        EmptyStateGlass(
                            title = "لا يوجد طلاب مسجلون",
                            subtitle = "ستظهر هنا حسابات الطلاب الفريدة مع صفوفهم وشعبهم وعضوياتهم النشطة.",
                            icon = "🎒"
                        )
                    }
                } else {
                    item {
                        Text(
                            "الطلاب المسجلون (${dashboardData?.stats?.totalStudents ?: students.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                    }
                    items(students, key = { it.id }) { student ->
                        AdminUserManageCard(
                            user = student,
                            roleBadge = "طالب 🎒",
                            roleColor = CyanAccent
                        )
                    }
                }
            }

            4 -> {
                // Tab 4: School Policies & Guidelines
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MidnightSurface
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "🏛️ لوحة مدير المدرسة — السياسات والإشراف العام:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD700)
                            )
                            TeacherGuidelineItem(
                                icon = "🎓",
                                title = "كادر المعلمين الأكاديمي",
                                desc = "الأساتذة المعتمدون هم المخولون بوضع المناهج الرسمية والواجبات المعتمدة، وتوثيق الإجابات النموذجية في المنظومة."
                            )
                            TeacherGuidelineItem(
                                icon = "🛡️",
                                title = "تنظيم الشعب والصفوف",
                                desc = "يتابع مشرفو الشعب تدقيق تصويبات الدروس ومقترحات الجداول لضمان جودة المحتوى التشاركي واستقراره."
                            )
                            TeacherGuidelineItem(
                                icon = "📊",
                                title = "الحوكمة والنزاهة التعليمية",
                                desc = "أي قرار لحذف محتوى أو تصويب كبير يتم بنظام النصاب الجماعي التشاركي تحت إشراف المنظومة."
                            )
                        }
                    }
                }
            }
        }
    }

    // Broadcast Announcement Dialog
    if (showBroadcastDialog) {
        AlertDialog(
            onDismissRequest = { showBroadcastDialog = false },
            title = {
                Text("إرسال تعميم رسمي عاجل 📢", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("سيتم إرسال هذا التنبيه لكافة طلاب وأساتذة المدرسة:", fontSize = 12.sp, color = TextSecondary)
                    OutlinedTextField(
                        value = broadcastTitle,
                        onValueChange = { broadcastTitle = it },
                        label = { Text("عنوان التعميم") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = broadcastContent,
                        onValueChange = { broadcastContent = it },
                        label = { Text("نص التنبيه أو البيان المدرسي") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val title = broadcastTitle.trim()
                        val body = broadcastContent.trim()
                        if (title.isBlank() || body.isBlank() || isPublishingBroadcast) {
                            Toast.makeText(context, "أدخل عنوان التعميم ونصه بالكامل", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isPublishingBroadcast = true
                        viewModel.publishSchoolAnnouncement(title, body) { success, message ->
                            isPublishingBroadcast = false
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            if (success) {
                                showBroadcastDialog = false
                                broadcastTitle = ""
                                broadcastContent = ""
                            }
                        }
                    },
                    enabled = !isPublishingBroadcast && broadcastTitle.isNotBlank() && broadcastContent.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700), contentColor = MidnightBackground)
                ) {
                    if (isPublishingBroadcast) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MidnightBackground
                        )
                    } else {
                        Text("نشر التعميم", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showBroadcastDialog = false }) {
                    Text("إلغاء", color = TextSecondary)
                }
            },
            containerColor = MidnightSurface
        )
    }


}

@Composable
fun AdminUserManageCard(
    user: UserBrief,
    roleBadge: String,
    roleColor: Color
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MidnightSurface,
        borderColor = roleColor.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(roleColor.copy(alpha = 0.15f))
                        .border(1.dp, roleColor.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(user.full_name.take(1), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = roleColor)
                }
                Column {
                    Text(
                        text = user.full_name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "📱 ${user.phone_number}",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Text(
                        text = if (user.grade_id > 0) "الصف ${user.grade_id} • شعبة ${user.section_id}" else "البيانات الدراسية غير محددة",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "عضويات نشطة: ${user.active_group_count}",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            GlassPill(
                text = roleBadge,
                color = roleColor,
                bgColor = roleColor.copy(alpha = 0.15f)
            )
        }
    }
}
