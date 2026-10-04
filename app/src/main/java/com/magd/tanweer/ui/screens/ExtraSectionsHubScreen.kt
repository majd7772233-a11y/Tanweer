package com.magd.tanweer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.Role
import com.magd.tanweer.data.model.getRoleEnum
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.GlassCard
import com.magd.tanweer.ui.theme.*

@Composable
fun ExtraSectionsHubScreen(
    viewModel: TanweerViewModel
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val userRole = currentUser?.getRoleEnum() ?: Role.STUDENT
    var searchQuery by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        // Hero Header Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MidnightSurface,
                                CyanAccent.copy(alpha = 0.12f),
                                ElectricBlue.copy(alpha = 0.25f)
                            )
                        )
                    )
                    .border(1.dp, CyanGlow.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CyanGlow.copy(alpha = 0.3f))
                                .border(1.dp, CyanAccent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✨", fontSize = 20.sp)
                        }
                        Column {
                            Text(
                                text = "أقسام تـنـويـر الإضافية",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Black,
                                color = CyanAccent
                            )
                            Text(
                                text = "بوابة المناهج، الحوكمة، الإدارة والخدمات التراكمية",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    // Search within extra sections
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("ابحث في أقسام المنظومة...", fontSize = 13.sp, color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث", tint = CyanAccent) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح", tint = TextSecondary)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = GlassBorder,
                            focusedContainerColor = MidnightSurface.copy(alpha = 0.8f),
                            unfocusedContainerColor = MidnightSurface.copy(alpha = 0.6f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true
                    )
                }
            }
        }

        // Section 1: الأكاديمية والتعلم (Academic & Learning Hub)
        item {
            HubCategoryHeader(title = "📚 التعلم والمناهج الأكاديمية", color = CyanAccent)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (searchQuery.isEmpty() || "مكتبة الكتب المناهج pdf".contains(searchQuery.trim())) {
                    HubActionCard(
                        title = "مكتبة الكتب والمناهج المعتمدة",
                        subtitle = "الكتب الدراسية المعتمدة مع قارئ PDF الذكي وتقليب الصفحات",
                        badge = "PDF تفاعلي 📖",
                        badgeColor = CyanAccent,
                        icon = Icons.Default.LocalLibrary,
                        accentColor = CyanAccent,
                        onClick = { viewModel.setSubScreen(SubScreen.LIBRARY) }
                    )
                }

                if (searchQuery.isEmpty() || "ماذا فاتني غياب واجبات دروس سابقة".contains(searchQuery.trim())) {
                    HubActionCard(
                        title = "ماذا فاتني أثناء الغياب؟",
                        subtitle = "تجميع ذكي لكل الواجبات والدروس والاختبارات المضافة أثناء غيابك",
                        badge = "استدراك ذكي 🎒",
                        badgeColor = WarmAmber,
                        icon = Icons.Default.WorkHistory,
                        accentColor = WarmAmber,
                        onClick = { viewModel.setSubScreen(SubScreen.WHAT_DID_I_MISS) }
                    )
                }

                if (searchQuery.isEmpty() || "جدول حصص أسبوعي مواعيد".contains(searchQuery.trim())) {
                    HubActionCard(
                        title = "جدول الحصص الأسبوعي",
                        subtitle = "استعراض جدول الشعبة وتقديم مقترحات تعديل الحصص والتصويت عليها",
                        badge = "تفاعلي 📅",
                        badgeColor = EmeraldGreen,
                        icon = Icons.Default.Schedule,
                        accentColor = EmeraldGreen,
                        onClick = { viewModel.setSubScreen(SubScreen.SCHEDULE) }
                    )
                }

                if (searchQuery.isEmpty() || "رحلة المادة خريطة تراكمية خط زمني".contains(searchQuery.trim())) {
                    HubActionCard(
                        title = "رحلة المادة والخط الزمني",
                        subtitle = "الخريطة التراكمية والتسلسل الزمني للدروس والمراجعات",
                        badge = "تراكمي 🗺️",
                        badgeColor = PurpleAccent,
                        icon = Icons.Default.Timeline,
                        accentColor = PurpleAccent,
                        onClick = { viewModel.setSubScreen(SubScreen.TIMELINE) }
                    )
                }

                if (searchQuery.isEmpty() || "أرشيف أكاديمي سجل سنوي".contains(searchQuery.trim())) {
                    HubActionCard(
                        title = "الأرشيف الأكاديمي والسنوات السابقة",
                        subtitle = "استعراض سجلات الأعوام والمواد المؤرشفة للرجوع إليها",
                        badge = "أرشيف 🏛️",
                        badgeColor = TextSecondary,
                        icon = Icons.Default.Archive,
                        accentColor = TextSecondary,
                        onClick = { viewModel.setSubScreen(SubScreen.ACADEMIC_HISTORY) }
                    )
                }
            }
        }

        // Section 2: المجتمع والحوكمة (Community & Governance)
        item {
            HubCategoryHeader(title = "🗳️ المجتمع والحوكمة الطلابية", color = EmeraldGreen)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (searchQuery.isEmpty() || "قرارات تصويت جماعي حوكمة ديمقراطية".contains(searchQuery.trim())) {
                    HubActionCard(
                        title = "القرارات والتصويتات الجماعية",
                        subtitle = "محرك الحوكمة الموحد للتصويت على الحذف، التصويب، الجداول والاعتمادات",
                        badge = "حوكمة موحدة 🗳️",
                        badgeColor = EmeraldGreen,
                        icon = Icons.Default.HowToVote,
                        accentColor = EmeraldGreen,
                        onClick = { viewModel.setSubScreen(SubScreen.COMMUNITY_DECISIONS) }
                    )
                }

                if (searchQuery.isEmpty() || "مجموعات شعب دردشة حية صف".contains(searchQuery.trim())) {
                    HubActionCard(
                        title = "المجموعات والشعب الدراسية",
                        subtitle = "الانضمام إلى مجموعات الصف، إدارة الأعضاء، والمحادثة الفورية",
                        badge = "شعب ونوادي 👥",
                        badgeColor = CyanAccent,
                        icon = Icons.Default.Groups,
                        accentColor = CyanAccent,
                        onClick = { viewModel.setSubScreen(SubScreen.GROUPS) }
                    )
                }

                if (searchQuery.isEmpty() || "بحث شامل دروس واجبات كتب".contains(searchQuery.trim())) {
                    HubActionCard(
                        title = "محرك البحث الشامل",
                        subtitle = "البحث الفوري في كافة الدروس، الوسائط، الواجبات والكتب",
                        badge = "بحث فوري 🔍",
                        badgeColor = CyanAccent,
                        icon = Icons.Default.Search,
                        accentColor = CyanAccent,
                        onClick = { viewModel.setSubScreen(SubScreen.SEARCH) }
                    )
                }
            }
        }

        // Section 3: مراكز الإدارة والرتب (Role Centers)
        if (userRole == Role.TEACHER || userRole == Role.MODERATOR || userRole == Role.ADMIN || userRole == Role.SYSTEM_OWNER) {
            item {
                HubCategoryHeader(title = "🛡️ مراكز الرتب والإدارة", color = WarmAmber)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (userRole == Role.TEACHER || userRole == Role.ADMIN || userRole == Role.SYSTEM_OWNER) {
                        HubActionCard(
                            title = "مركز التعليم للأستاذ المعتمد",
                            subtitle = "إدارة الواجبات الرسمية، الاختبارات الدورية، وتوثيق الإجابات النموذجية",
                            badge = "أستاذ معتمد 🎓",
                            badgeColor = PurpleAccent,
                            icon = Icons.Default.School,
                            accentColor = PurpleAccent,
                            onClick = { viewModel.setSubScreen(SubScreen.TEACHER_DASHBOARD) }
                        )
                    }

                    if (userRole == Role.MODERATOR || userRole == Role.ADMIN || userRole == Role.SYSTEM_OWNER) {
                        HubActionCard(
                            title = "مركز إشراف الشعبة والتدقيق",
                            subtitle = "مراجعة طلبات الانضمام، تقارير الشات، وتدقيق مقترحات الطلاب",
                            badge = "مشرف الشعبة 🛡️",
                            badgeColor = WarmAmber,
                            icon = Icons.Default.Shield,
                            accentColor = WarmAmber,
                            onClick = { viewModel.setSubScreen(SubScreen.MODERATOR_DASHBOARD) }
                        )
                    }

                    if (userRole == Role.ADMIN || userRole == Role.SYSTEM_OWNER) {
                        HubActionCard(
                            title = "لوحة مدير المدرسة والنظام",
                            subtitle = "إدارة الشعب، كادر المعلمين، سجلات التدقيق والأمان",
                            badge = "مدير النظام 👑",
                            badgeColor = RubyRed,
                            icon = Icons.Default.AdminPanelSettings,
                            accentColor = RubyRed,
                            onClick = { viewModel.setSubScreen(SubScreen.ADMIN_DASHBOARD) }
                        )
                    }
                }
            }
        }

        // Section 4: الحساب والتخصيص (Account & Preferences)
        item {
            HubCategoryHeader(title = "⚙️ الحساب والإعدادات", color = TextSecondary)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (searchQuery.isEmpty() || "ملف شخصي مساهمات رتبة أوسمة".contains(searchQuery.trim())) {
                    HubActionCard(
                        title = "الملف الشخصي والمساهمات",
                        subtitle = "بيانات الحساب، الأوسمة المحصلة، وسجل المشاركات الفعالة",
                        badge = "حسابي 👤",
                        badgeColor = CyanAccent,
                        icon = Icons.Default.Person,
                        accentColor = CyanAccent,
                        onClick = { viewModel.setSubScreen(SubScreen.PROFILE) }
                    )
                }

                if (searchQuery.isEmpty() || "إعدادات حجم الخط سمة مظهر مزامنة".contains(searchQuery.trim())) {
                    HubActionCard(
                        title = "إعدادات التطبيق وتخصيص الخط",
                        subtitle = "تخصيص حجم الخط (افتراضي 12px)، السمة، والمزامنة السحابية",
                        badge = "تخصيص ⚙️",
                        badgeColor = TextSecondary,
                        icon = Icons.Default.Settings,
                        accentColor = TextSecondary,
                        onClick = { viewModel.setSubScreen(SubScreen.SETTINGS) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HubCategoryHeader(title: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun HubActionCard(
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        backgroundColor = MidnightSurface.copy(alpha = 0.85f),
        borderColor = accentColor.copy(alpha = 0.25f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f))
                    .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .border(0.5.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "فتح",
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
