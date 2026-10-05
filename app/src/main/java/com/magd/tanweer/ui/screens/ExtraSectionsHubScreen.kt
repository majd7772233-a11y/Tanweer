package com.magd.tanweer.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.Role
import com.magd.tanweer.data.model.getRoleEnum
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.GlassCard
import com.magd.tanweer.ui.components.GlassPill
import com.magd.tanweer.ui.theme.*

@Composable
fun ExtraSectionsHubScreen(
    viewModel: TanweerViewModel
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val userRole = currentUser?.getRoleEnum() ?: Role.STUDENT
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }

    val categories = listOf(
        "ALL" to "الكل ✨",
        "ACADEMIC" to "المناهج والتعلم 📚",
        "COMMUNITY" to "المجتمع والحوكمة 🗳️",
        "ROLES" to "مراكز الإدارة 🛡️",
        "SETTINGS" to "النظام والإعدادات ⚙️"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp)
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
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(CyanGlow.copy(alpha = 0.3f))
                                .border(1.5.dp, CyanAccent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✨", fontSize = 22.sp)
                        }
                        Column {
                            Text(
                                text = "أقسام تـنـويـر والخدمات",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = CyanAccent
                            )
                            Text(
                                text = "بوابة الخدمات التعليمية، الحوكمة، الإدارة والملفات",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    // Search inside sections
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("ابحث في أقسام المنظومة...", fontSize = 12.sp, color = TextMuted) },
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
                            .padding(top = 2.dp),
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

                    // Quick Category Filter Row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                    ) {
                        items(categories) { (catKey, catLabel) ->
                            val isSelected = selectedCategoryFilter == catKey
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategoryFilter = catKey },
                                label = { Text(catLabel, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyanAccent,
                                    selectedLabelColor = TextOnAccent
                                )
                            )
                        }
                    }
                }
            }
        }

        // Section 1: الأكاديمية والتعلم (Academic & Learning Hub)
        if (selectedCategoryFilter == "ALL" || selectedCategoryFilter == "ACADEMIC") {
            item {
                HubCategoryHeader(title = "📚 التعلم والمناهج الأكاديمية", color = CyanAccent)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (searchQuery.isEmpty() || "مكتبة الكتب المناهج pdf دراسية تحميل تخزين".contains(searchQuery.trim())) {
                        HubActionCard(
                            title = "مكتبة الكتب والمناهج المعتمدة",
                            subtitle = "الكتب الدراسية المعتمدة مع قارئ PDF الذكي وخاصية القراءة بدون إنترنت",
                            badge = "PDF تفاعلي 📖",
                            badgeColor = CyanAccent,
                            icon = Icons.Default.LocalLibrary,
                            accentColor = CyanAccent,
                            onClick = { viewModel.setSubScreen(SubScreen.LIBRARY) }
                        )
                    }

                    if (searchQuery.isEmpty() || "ماذا فاتني غياب واجبات دروس سابقة استدراك".contains(searchQuery.trim())) {
                        HubActionCard(
                            title = "ماذا فاتني أثناء الغياب؟",
                            subtitle = "تجميع ذكي وشامل لكل الواجبات والدروس والاختبارات المضافة أثناء الغياب",
                            badge = "استدراك ذكي 🎒",
                            badgeColor = WarmAmber,
                            icon = Icons.Default.WorkHistory,
                            accentColor = WarmAmber,
                            onClick = { viewModel.setSubScreen(SubScreen.WHAT_DID_I_MISS) }
                        )
                    }

                    if (searchQuery.isEmpty() || "جدول حصص أسبوعي مواعيد مقترحات".contains(searchQuery.trim())) {
                        HubActionCard(
                            title = "جدول الحصص الأسبوعي",
                            subtitle = "استعراض جدول الشعبة وتقديم مقترحات تعديل الحصص والتصويت الجماعي عليها",
                            badge = "تفاعلي 📅",
                            badgeColor = EmeraldGreen,
                            icon = Icons.Default.Schedule,
                            accentColor = EmeraldGreen,
                            onClick = { viewModel.setSubScreen(SubScreen.SCHEDULE) }
                        )
                    }

                    if (searchQuery.isEmpty() || "رحلة المادة خط زمني دروس مستودع معرفة".contains(searchQuery.trim())) {
                        HubActionCard(
                            title = "رحلة المادة والخط الزمني",
                            subtitle = "الخريطة التراكمية والتسلسل الزمني للدروس والمراجعات الدورية",
                            badge = "تراكمي 🗺️",
                            badgeColor = PurpleAccent,
                            icon = Icons.Default.Timeline,
                            accentColor = PurpleAccent,
                            onClick = { viewModel.setSubScreen(SubScreen.TIMELINE) }
                        )
                    }

                    if (searchQuery.isEmpty() || "أرشيف أكاديمي سجل سنوي سنوات سابقة".contains(searchQuery.trim())) {
                        HubActionCard(
                            title = "الأرشيف الأكاديمي والسنوات السابقة",
                            subtitle = "استعراض سجلات الأعوام والمواد المؤرشفة والرجوع إليها في أي وقت",
                            badge = "أرشيف 🏛️",
                            badgeColor = TextSecondary,
                            icon = Icons.Default.Archive,
                            accentColor = TextSecondary,
                            onClick = { viewModel.setSubScreen(SubScreen.ACADEMIC_HISTORY) }
                        )
                    }
                }
            }
        }

        // Section 2: المجتمع والحوكمة (Community & Governance)
        if (selectedCategoryFilter == "ALL" || selectedCategoryFilter == "COMMUNITY") {
            item {
                HubCategoryHeader(title = "🗳️ المجتمع والحوكمة المدرسية", color = EmeraldGreen)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (searchQuery.isEmpty() || "قرارات تصويت جماعي حوكمة ديمقراطية نصاب".contains(searchQuery.trim())) {
                        HubActionCard(
                            title = "القرارات والتصويتات الجماعية",
                            subtitle = "محرك الحوكمة الموحد للتصويت على الحذف، التصويب، وتعديل الجداول",
                            badge = "حوكمة موحدة 🗳️",
                            badgeColor = EmeraldGreen,
                            icon = Icons.Default.HowToVote,
                            accentColor = EmeraldGreen,
                            onClick = { viewModel.setSubScreen(SubScreen.COMMUNITY_DECISIONS) }
                        )
                    }

                    if (searchQuery.isEmpty() || "مجموعات شعب دردشة حية صف أعضاء".contains(searchQuery.trim())) {
                        HubActionCard(
                            title = "المجموعات والشعب الدراسية",
                            subtitle = "الانضمام إلى مجموعات الصف، والمحادثة الفورية التفاعلية مع الزملاء",
                            badge = "شعب ونوادي 👥",
                            badgeColor = CyanAccent,
                            icon = Icons.Default.Groups,
                            accentColor = CyanAccent,
                            onClick = { viewModel.setSubScreen(SubScreen.GROUPS) }
                        )
                    }

                    if (searchQuery.isEmpty() || "فعاليات انشطة مسابقات مناسبات اجازات احداث".contains(searchQuery.trim())) {
                        HubActionCard(
                            title = "الفعاليات والأنشطة المدرسية",
                            subtitle = "جدول المسابقات، الاحتفالات، العطل الرسمية والورش التفاعلية",
                            badge = "أنشطة ومناسبات 🎪",
                            badgeColor = PurpleAccent,
                            icon = Icons.Default.Event,
                            accentColor = PurpleAccent,
                            onClick = { viewModel.setSubScreen(SubScreen.EVENTS) }
                        )
                    }

                    if (searchQuery.isEmpty() || "بحث شامل دروس واجبات كتب وسائط".contains(searchQuery.trim())) {
                        HubActionCard(
                            title = "محرك البحث الشامل",
                            subtitle = "البحث الفوري الدقيق في كافة الدروس والواجبات والاختبارات والكتب",
                            badge = "بحث فوري 🔍",
                            badgeColor = CyanAccent,
                            icon = Icons.Default.Search,
                            accentColor = CyanAccent,
                            onClick = { viewModel.setSubScreen(SubScreen.SEARCH) }
                        )
                    }
                }
            }
        }

        // Section 3: مراكز الإدارة والرتب (Role Centers)
        if ((selectedCategoryFilter == "ALL" || selectedCategoryFilter == "ROLES") &&
            (userRole == Role.TEACHER || userRole == Role.MODERATOR || userRole == Role.ADMIN || userRole == Role.SYSTEM_OWNER)
        ) {
            item {
                HubCategoryHeader(title = "🛡️ مراكز الرتب والإدارة المدرسية", color = WarmAmber)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (userRole == Role.TEACHER || userRole == Role.ADMIN || userRole == Role.SYSTEM_OWNER) {
                        HubActionCard(
                            title = "مركز التعليم للأستاذ المعتمد",
                            subtitle = "إدارة وتثبيت الواجبات الرسمية، الاختبارات الدورية، وتوثيق الإجابات النموذجية",
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
                            subtitle = "مراجعة تقارير المحادثة، اعتماد التصحيحات، وتدقيق مقترحات الطلاب",
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
                            subtitle = "إدارة كاملة للشعب، كادر المعلمين، سجلات التدقيق، القرارات الإدارية والتنبيهات",
                            badge = "إدارة المدرسة 👑",
                            badgeColor = RubyRed,
                            icon = Icons.Default.AdminPanelSettings,
                            accentColor = RubyRed,
                            onClick = { viewModel.setSubScreen(SubScreen.ADMIN_DASHBOARD) }
                        )
                    }
                }
            }
        }

        // Section 4: الحساب والتحديثات (Account & System Updates)
        if (selectedCategoryFilter == "ALL" || selectedCategoryFilter == "SETTINGS") {
            item {
                HubCategoryHeader(title = "⚙️ الحساب وإدارة النظام", color = TextSecondary)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (searchQuery.isEmpty() || "ملف شخصي مساهمات رتبة أوسمة ترقية حساب".contains(searchQuery.trim())) {
                        HubActionCard(
                            title = "الملف الشخصي والمساهمات",
                            subtitle = "بيانات الحساب، الأوسمة المحصلة، وسجل المشاركات الفعالة وطلب الترقية",
                            badge = "حسابي 👤",
                            badgeColor = CyanAccent,
                            icon = Icons.Default.Person,
                            accentColor = CyanAccent,
                            onClick = { viewModel.setSubScreen(SubScreen.PROFILE) }
                        )
                    }

                    if (searchQuery.isEmpty() || "تحديثات إصدار فحص النسخة ترقية changelog".contains(searchQuery.trim())) {
                        HubActionCard(
                            title = "فحص التحديثات والإصدار",
                            subtitle = "التحقق من توافق النسخة، سجل التحديثات، والميزات الجديدة في المنظومة",
                            badge = "الإصدار 1.2.0 🚀",
                            badgeColor = EmeraldGreen,
                            icon = Icons.Default.SystemUpdate,
                            accentColor = EmeraldGreen,
                            onClick = { viewModel.setSubScreen(SubScreen.VERSION_CHECK) }
                        )
                    }

                    if (searchQuery.isEmpty() || "إعدادات حجم الخط سمة مظهر مزامنة إشعارات".contains(searchQuery.trim())) {
                        HubActionCard(
                            title = "إعدادات التطبيق وتخصيص المظهر",
                            subtitle = "تخصيص حجم الخط المفضل، السمة الليلية، والمزامنة السحابية الذكية",
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

/**
 * Robust responsive card layout that prevents character wrapping / cutting on any screen size.
 */
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row with Icon, Title, and Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.15f))
                            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                GlassPill(
                    text = badge,
                    color = badgeColor,
                    bgColor = badgeColor.copy(alpha = 0.15f)
                )
            }

            // Description & Forward Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "فتح",
                    tint = CyanAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
