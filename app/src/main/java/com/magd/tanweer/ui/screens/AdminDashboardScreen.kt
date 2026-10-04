package com.magd.tanweer.ui.screens

import android.widget.Toast
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

    LaunchedEffect(Unit) {
        viewModel.loadAdminDashboard()
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: School Stats, 1: Teachers, 2: Moderators

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
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
                                        Brush.linearGradient(listOf(Color(0xFFFFD700).copy(alpha = 0.25f), CyanAccent.copy(alpha = 0.25f)))
                                    )
                                    .border(1.5.dp, Color(0xFFFFD700), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("👑", fontSize = 26.sp)
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                    text = dashboardData?.school?.name ?: "مدرسة تنوير النموذجية — إدارة المنظومة",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                viewModel.loadAdminDashboard()
                                Toast.makeText(context, "تم تحديث بيانات المدرسة 🔄", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GlassSurface)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = CyanAccent, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // School Overview Stats Grid
                    val stats = dashboardData?.stats
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TeacherStatCard(
                                title = "إجمالي الطلاب",
                                count = "${stats?.totalUsers ?: 0}",
                                icon = "👥",
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
                            TeacherStatCard(
                                title = "الواجبات والاختبارات",
                                count = "${(stats?.totalHomeworks ?: 0) + (stats?.totalExams ?: 0)}",
                                icon = "📝",
                                color = Color(0xFFFFD700),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
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
                listOf(
                    "إحصاءات المدرسة 🏫",
                    "كادر المعلمين 🎓",
                    "هيئة الإشراف 🛡️"
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
                // School Management Guidelines & Policies
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
                                    Text(
                                        text = "استعراض جميع الشعب والمجموعات المدرسية",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "الدخول لأي شعبة والاطلاع على نشاطها وإدارتها مباشرة",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = CyanAccent)
                        }
                    }
                }
            }

            1 -> {
                // Teachers List
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
                                    text = "لا يوجد معلمون معتمدون مسجلون حالياً",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "يمكن للمعلمين تقديم طلب ترقية وتفعيله عبر رمز الاعتماد.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(teachers) { teacher ->
                        AdminUserCard(user = teacher, roleBadge = "أستاذ معتمد 🎓", roleColor = EmeraldGreen)
                    }
                }
            }

            2 -> {
                // Moderators List
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
                                    text = "لا يوجد مشرفون مسجلون حالياً",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "يمكن تعيين مشرفين لكل شعبة من داخل قائمة الأعضاء.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(moderators) { mod ->
                        AdminUserCard(user = mod, roleBadge = "مسؤول شعبة 🛡️", roleColor = WarmAmber)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminUserCard(
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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
