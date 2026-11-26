package com.magd.tanweer.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.Role
import com.magd.tanweer.data.model.getRoleEnum
import com.magd.tanweer.R
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.RoleCenterAccess
import com.magd.tanweer.ui.roleCenterAccess
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.GlassButton
import com.magd.tanweer.ui.components.GlassCard
import com.magd.tanweer.ui.components.GlassFilterChip
import com.magd.tanweer.ui.components.HorizontalFilterStrip
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


    val matchesHubSearch: (String) -> Boolean = { searchableText ->
        ExtraSectionsHubSearchPolicy.matches(searchQuery, searchableText)
    }
    val showLibraryCard = matchesHubSearch("مكتبة الكتب المناهج pdf دراسية تحميل تخزين")
    val showCatchUpCard = matchesHubSearch("ماذا فاتني غياب واجبات دروس سابقة استدراك")
    val showScheduleCard = matchesHubSearch("جدول حصص أسبوعي مواعيد مقترحات")
    val showTimelineCard = matchesHubSearch("رحلة المادة خط زمني دروس مستودع معرفة")
    val showArchiveCard = matchesHubSearch("أرشيف أكاديمي سجل سنوي سنوات سابقة")
    val showAcademicSection = (selectedCategoryFilter == "ALL" || selectedCategoryFilter == "ACADEMIC") &&
        (showLibraryCard || showCatchUpCard || showScheduleCard || showTimelineCard || showArchiveCard)

    val showDecisionsCard = matchesHubSearch("قرارات تصويت جماعي حوكمة ديمقراطية نصاب")
    val showGroupsCard = matchesHubSearch("مجموعات شعب دردشة حية صف أعضاء")
    val showEventsCard = matchesHubSearch("فعاليات انشطة مسابقات مناسبات اجازات احداث")
    val showGlobalSearchCard = matchesHubSearch("بحث شامل دروس واجبات كتب وسائط")
    val showCommunitySection = (selectedCategoryFilter == "ALL" || selectedCategoryFilter == "COMMUNITY") &&
        (showDecisionsCard || showGroupsCard || showEventsCard || showGlobalSearchCard)

    val roleCenterAvailability = roleCenterAccess(userRole, selectedCategoryFilter)
    val showTeacherRoleCard = roleCenterAvailability == RoleCenterAccess.AVAILABLE &&
        userRole == Role.TEACHER &&
        matchesHubSearch("مركز التعليم للأستاذ المعتمد إدارة الواجبات الرسمية الاختبارات الدورية الإجابات النموذجية")
    val showModeratorRoleCard = roleCenterAvailability == RoleCenterAccess.AVAILABLE &&
        userRole == Role.MODERATOR &&
        matchesHubSearch("مركز إشراف الشعبة والتدقيق تقارير المحادثة التصحيحات مقترحات الطلاب")
    val showAdminRoleCard = roleCenterAvailability == RoleCenterAccess.AVAILABLE &&
        userRole == Role.ADMIN &&
        matchesHubSearch("لوحة مدير المدرسة والنظام إدارة الشعب كادر المعلمين سجلات التدقيق")
    val showAvailableRoleCenterSection = showTeacherRoleCard || showModeratorRoleCard || showAdminRoleCard
    val showLockedRoleCenterCard = roleCenterAvailability == RoleCenterAccess.LOCKED &&
        matchesHubSearch("مراكز الإدارة والرتب مسؤول شعبة أستاذ معتمد مدير المدرسة")

    val showProfileCard = matchesHubSearch("ملف شخصي مساهمات رتبة أوسمة ترقية حساب")
    val showVersionCard = matchesHubSearch("تحديثات إصدار فحص النسخة ترقية changelog")
    val showSettingsCard = matchesHubSearch("إعدادات حجم الخط سمة مظهر مزامنة إشعارات")
    val showSettingsSection = (selectedCategoryFilter == "ALL" || selectedCategoryFilter == "SETTINGS") &&
        (showProfileCard || showVersionCard || showSettingsCard)

    val hasSearchResults = showAcademicSection || showCommunitySection ||
        showAvailableRoleCenterSection || showLockedRoleCenterCard || showSettingsSection

    LazyColumn(
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            Text("✨", fontSize = tanweerTextSize(22f))
                        }
                        Column {
                            Text(
                                text = "أقسام تـنـويـر والخدمات",
                                fontSize = tanweerTextSize(18f),
                                fontWeight = FontWeight.Black,
                                color = CyanAccent
                            )
                            Text(
                                text = "بوابة الخدمات التعليمية، الحوكمة، الإدارة والملفات",
                                fontSize = tanweerTextSize(12f),
                                color = TextSecondary
                            )
                        }
                    }

                    // Search inside sections
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("ابحث في أقسام المنظومة...", fontSize = tanweerTextSize(12f), color = TextMuted) },
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

                    // Quick Category Filter Row with an explicit continuation cue.
                    HubCategoryFilterRow(
                        categories = categories,
                        selectedCategoryFilter = selectedCategoryFilter,
                        onCategorySelected = { selectedCategoryFilter = it },
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        if (searchQuery.isNotBlank() && !hasSearchResults) {
            item {
                HubSearchEmptyState(onClear = { searchQuery = "" })
            }
        }

        // Section 1: الأكاديمية والتعلم (Academic & Learning Hub)
        if (showAcademicSection) {
            item {
                HubCategoryHeader(title = "📚 التعلم والمناهج الأكاديمية", color = CyanAccent)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showLibraryCard) {
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

                    if (showCatchUpCard) {
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

                    if (showScheduleCard) {
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

                    if (showTimelineCard) {
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

                    if (showArchiveCard) {
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
        if (showCommunitySection) {
            item {
                HubCategoryHeader(title = "🗳️ المجتمع والحوكمة المدرسية", color = EmeraldGreen)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showDecisionsCard) {
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

                    if (showGroupsCard) {
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

                    if (showEventsCard) {
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

                    if (showGlobalSearchCard) {
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

        // Section 3: مركز الرتبة الحالية (Current Role Center Only)
        if (showAvailableRoleCenterSection) {
            item {
                HubCategoryHeader(title = "🛡️ مركز الرتبة المعتمدة", color = WarmAmber)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showTeacherRoleCard) {
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

                    if (showModeratorRoleCard) {
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

                    if (showAdminRoleCard) {
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

        if (showLockedRoleCenterCard) {
            item {
                HubCategoryHeader(title = "🛡️ مراكز الإدارة والرتب", color = WarmAmber)
            }
            item {
                LockedRoleCenterCard(
                    onOpenProfile = { viewModel.setSubScreen(SubScreen.PROFILE) }
                )
            }
        }

        // Section 4: الحساب والتحديثات (Account & System Updates)
        if (showSettingsSection) {
            item {
                HubCategoryHeader(title = "⚙️ الحساب وإدارة النظام", color = TextSecondary)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showProfileCard) {
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

                    if (showVersionCard) {
                        HubActionCard(
                            title = "فحص التحديثات والإصدار",
                            subtitle = "التحقق من توافق النسخة، سجل التحديثات، والميزات الجديدة في المنظومة",
                            badge = "الإصدار ${com.magd.tanweer.BuildConfig.VERSION_NAME} 🚀",
                            badgeColor = EmeraldGreen,
                            icon = Icons.Default.SystemUpdate,
                            accentColor = EmeraldGreen,
                            onClick = { viewModel.setSubScreen(SubScreen.VERSION_CHECK) }
                        )
                    }

                    if (showSettingsCard) {
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
internal fun HubSearchEmptyState(
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hub_search_empty_state"),
        backgroundColor = GlassSurface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.hub_search_no_results_title),
                fontSize = tanweerTextSize(14f),
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.hub_search_no_results_description),
                fontSize = tanweerTextSize(12f),
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            GlassButton(
                text = stringResource(R.string.hub_search_clear_action),
                icon = Icons.Default.Clear,
                onClick = onClear,
                modifier = Modifier.fillMaxWidth().testTag("hub_search_clear_action")
            )
        }
    }
}

@Composable
internal fun HubCategoryFilterRow(@Composable
internal fun HubCategoryFilterRow(
    categories: List<Pair<String, String>>,
    selectedCategoryFilter: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalFilterStrip(
            state = listState,
            edgeColor = MidnightSurface,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("hub_category_filter_row"),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
            itemSpacing = 6.dp
        ) {
            items(items = categories, key = { it.first }) { (categoryKey, categoryLabel) ->
                val isSelected = selectedCategoryFilter == categoryKey
                GlassFilterChip(
                    selected = isSelected,
                    onClick = { onCategorySelected(categoryKey) },
                    modifier = Modifier.testTag("hub_category_chip_$categoryKey"),
                    containerColor = MidnightSurface.copy(alpha = 0.65f),
                    selectedContainerColor = CyanAccent,
                    labelColor = TextSecondary,
                    selectedLabelColor = TextOnAccent
                ) {
                    Text(
                        text = categoryLabel,
                        fontSize = tanweerTextSize(11f),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // The hub always contains multiple categories; keep the swipe cue visible
        // from the first frame instead of depending on post-measure scroll flags.
        if (categories.size > 1) {
            Text(
                text = stringResource(R.string.hub_category_scroll_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hub_category_scroll_hint"),
                textAlign = TextAlign.End,
                fontSize = tanweerTextSize(10f),
                color = TextMuted
            )
        }
    }
}

@Composable
internal fun LockedRoleCenterCard(
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = MidnightSurface.copy(alpha = 0.88f),
        borderColor = WarmAmber.copy(alpha = 0.48f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(WarmAmber.copy(alpha = 0.14f))
                        .border(1.dp, WarmAmber.copy(alpha = 0.38f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = WarmAmber,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = stringResource(R.string.hub_role_center_locked_title),
                        fontSize = tanweerTextSize(13f),
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    GlassPill(
                        text = stringResource(R.string.hub_role_center_required_badge),
                        color = WarmAmber,
                        bgColor = WarmAmber.copy(alpha = 0.14f)
                    )
                }
            }

            Text(
                text = stringResource(R.string.hub_role_center_locked_description),
                fontSize = tanweerTextSize(11f),
                color = TextSecondary,
                lineHeight = tanweerLineHeight(16f)
            )

            GlassButton(
                text = stringResource(R.string.hub_role_center_request_upgrade),
                onClick = onOpenProfile,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hub_role_locked_upgrade_button"),
                color = WarmAmber,
                textColor = TextOnAccent,
                icon = Icons.Default.Person
            )
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
            fontSize = tanweerTextSize(15f),
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/**
 * Compact, full-surface action card for the services hub.
 *
 * GlassCard already contributes 16dp of inner padding. Keep the content free of a
 * second padding layer so cards remain short while retaining a comfortable target.
 */
@Composable
internal fun HubActionCard(
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clickable(
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onClick
            ),
        backgroundColor = MidnightSurface.copy(alpha = 0.85f),
        borderColor = accentColor.copy(alpha = 0.25f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.15f))
                    .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(19.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        fontSize = tanweerTextSize(13f),
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    GlassPill(
                        text = badge,
                        color = badgeColor,
                        bgColor = badgeColor.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }

                Text(
                    text = subtitle,
                    fontSize = tanweerTextSize(11f),
                    color = TextSecondary,
                    lineHeight = tanweerLineHeight(15f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = null,
                tint = CyanAccent,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}
