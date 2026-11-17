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
import com.magd.tanweer.data.model.CommunityDecisionItem
import com.magd.tanweer.data.model.ContentCorrectionItem
import com.magd.tanweer.data.model.GroupMemberItem
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*

@Composable
fun ModeratorDashboardScreen(
    viewModel: TanweerViewModel
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId.orEmpty() }
    val dashboardData by viewModel.moderatorDashboard.collectAsStateWithLifecycle()
    val dashboardError by viewModel.moderatorDashboardError.collectAsStateWithLifecycle()
    val isDashboardLoading by viewModel.isModeratorDashboardLoading.collectAsStateWithLifecycle()
    val groupCorrections by viewModel.groupCorrections.collectAsStateWithLifecycle()
    val groupCorrectionsSnapshotGroupId by viewModel.groupCorrectionsSnapshotGroupId.collectAsStateWithLifecycle()
    val hasGroupCorrectionsSnapshot = isSnapshotForScope(groupCorrectionsSnapshotGroupId, activeGroupId)
    val scopedGroupCorrections = itemsForSnapshotScope(groupCorrections, groupCorrectionsSnapshotGroupId, activeGroupId)
    val groupCorrectionsError by viewModel.groupCorrectionsError.collectAsStateWithLifecycle()
    val isGroupCorrectionsLoading by viewModel.isGroupCorrectionsLoading.collectAsStateWithLifecycle()
    val communityDecisions by viewModel.communityDecisions.collectAsStateWithLifecycle()
    val communityDecisionsSnapshotGroupId by viewModel.communityDecisionsSnapshotGroupId.collectAsStateWithLifecycle()
    val hasCommunityDecisionsSnapshot = isSnapshotForScope(communityDecisionsSnapshotGroupId, activeGroupId)
    val scopedCommunityDecisions = itemsForSnapshotScope(communityDecisions, communityDecisionsSnapshotGroupId, activeGroupId)
    val communityDecisionsError by viewModel.communityDecisionsError.collectAsStateWithLifecycle()
    val isCommunityDecisionsLoading by viewModel.isCommunityDecisionsLoading.collectAsStateWithLifecycle()
    val groupMembers by viewModel.groupMembersState.collectAsStateWithLifecycle()
    val groupMembersError by viewModel.groupMembersError.collectAsStateWithLifecycle()
    val isGroupMembersLoading by viewModel.isGroupMembersLoading.collectAsStateWithLifecycle()

    var selectedSection by remember { mutableIntStateOf(0) } // 0: Corrections, 1: Decisions & Schedule Proposals, 2: Members
    var decisionFilter by remember { mutableStateOf("ALL") } // "ALL", "SCHEDULE", "COMMUNITY"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        // Moderator Header Card
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MidnightSurface,
                borderColor = WarmAmber.copy(alpha = 0.6f)
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
                                        Brush.linearGradient(listOf(WarmAmber.copy(alpha = 0.25f), CyanAccent.copy(alpha = 0.25f)))
                                    )
                                    .border(1.5.dp, WarmAmber, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🛡️", fontSize = tanweerTextSize(26f))
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "مركز إشراف الشعبة",
                                        fontSize = tanweerTextSize(17f),
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    GlassPill(
                                        text = "مسؤول الشعبة 🛡️",
                                        color = WarmAmber,
                                        bgColor = WarmAmber.copy(alpha = 0.15f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "تدقيق المحتوى، فض النزاعات، وإدارة حوكمة الشعبة",
                                    fontSize = tanweerTextSize(12f),
                                    color = TextSecondary
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                viewModel.loadModeratorDashboard()
                                viewModel.loadGroupCorrections(activeGroupId)
                                viewModel.loadCommunityDecisions(activeGroupId)
                                viewModel.loadGroupMembers(activeGroupId)
                            },
                            enabled = !(isDashboardLoading || isGroupCorrectionsLoading || isCommunityDecisionsLoading || isGroupMembersLoading),
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GlassSurface)
                        ) {
                            if (isDashboardLoading || isGroupCorrectionsLoading || isCommunityDecisionsLoading || isGroupMembersLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = CyanAccent)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "تحديث بيانات الإشراف", tint = CyanAccent, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Summary Stats Row
                    val stats = dashboardData?.stats
                    // Schedule proposals already exist as unified community decisions.
                    // Count each record once instead of counting both backing tables.
                    val totalDecisionsAndProposals = scopedCommunityDecisions.count { it.status == "PENDING" }
                    if (dashboardData != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TeacherStatCard(
                                title = "تصويبات معلقة",
                                count = if (hasGroupCorrectionsSnapshot && groupCorrectionsError.isNullOrBlank() && !isGroupCorrectionsLoading) "${stats?.pendingCorrectionsCount ?: scopedGroupCorrections.count { it.status == "PENDING" }}" else "—",
                                icon = "✍️",
                                color = WarmAmber,
                                modifier = Modifier.weight(1f)
                            )
                            TeacherStatCard(
                                title = "قرارات وجدول",
                                count = if (hasCommunityDecisionsSnapshot && communityDecisionsError.isNullOrBlank() && !isCommunityDecisionsLoading) "$totalDecisionsAndProposals" else "—",
                                icon = "🗳️",
                                color = EmeraldGreen,
                                modifier = Modifier.weight(1f)
                            )
                            TeacherStatCard(
                                title = "أعضاء الشعبة",
                                count = if (groupMembersError.isNullOrBlank() && !isGroupMembersLoading) "${stats?.totalMembers ?: (groupMembers?.members?.size ?: 0)}" else "—",
                                icon = "👥",
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        if (dashboardData == null && isDashboardLoading) {
            item { GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) { Text("جارٍ تحميل بيانات الإشراف...", color = TextSecondary, modifier = Modifier.padding(12.dp)) } }
        }
        if (!dashboardError.isNullOrBlank()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = MidnightSurface, borderColor = WarmAmber.copy(alpha = 0.55f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("تعذر تحميل بيانات لوحة الإشراف", color = WarmAmber, fontWeight = FontWeight.Bold)
                        Text(dashboardError ?: "", color = TextSecondary, fontSize = tanweerTextSize(11f))
                        GlassButton(text = "إعادة المحاولة", color = CyanAccent, textColor = TextOnAccent, onClick = { viewModel.loadModeratorDashboard() })
                    }
                }
            }
        }
        if (!groupCorrectionsError.isNullOrBlank()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = MidnightSurface, borderColor = WarmAmber.copy(alpha = 0.55f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("تعذر تحميل طلبات التصويب", color = WarmAmber, fontWeight = FontWeight.Bold)
                        Text(groupCorrectionsError ?: "", color = TextSecondary, fontSize = tanweerTextSize(11f))
                        GlassButton(text = "إعادة محاولة التصويبات", color = CyanAccent, textColor = TextOnAccent, onClick = { viewModel.loadGroupCorrections(activeGroupId) })
                    }
                }
            }
        }
        if (!communityDecisionsError.isNullOrBlank()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = MidnightSurface, borderColor = WarmAmber.copy(alpha = 0.55f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("تعذر تحميل القرارات الجماعية", color = WarmAmber, fontWeight = FontWeight.Bold)
                        Text(communityDecisionsError ?: "", color = TextSecondary, fontSize = tanweerTextSize(11f))
                        GlassButton(text = "إعادة محاولة القرارات", color = CyanAccent, textColor = TextOnAccent, onClick = { viewModel.loadCommunityDecisions(activeGroupId) })
                    }
                }
            }
        }

        if (isGroupMembersLoading && groupMembers == null) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = CyanAccent)
                        Text("جارٍ تحميل أعضاء الشعبة...", color = TextSecondary)
                    }
                }
            }
        }
        if (!groupMembersError.isNullOrBlank()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = MidnightSurface, borderColor = WarmAmber.copy(alpha = 0.55f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("تعذر تحميل أعضاء الشعبة", color = WarmAmber, fontWeight = FontWeight.Bold)
                        Text(groupMembersError ?: "", color = TextSecondary, fontSize = tanweerTextSize(11f))
                        GlassButton(text = "إعادة محاولة الأعضاء", color = CyanAccent, textColor = TextOnAccent, onClick = { viewModel.loadGroupMembers(activeGroupId) })
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
                    "التصويبات ✍️",
                    "القرارات والجدول 🗳️",
                    "الأعضاء 👥"
                ).forEachIndexed { index, title ->
                    val isSelected = selectedSection == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) WarmAmber else Color.Transparent)
                            .clickable { selectedSection = index }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = tanweerTextSize(12f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) TextOnAccent else TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Section Content
        when (selectedSection) {
            0 -> {
                // Corrections Queue
                val pending = scopedGroupCorrections.filter { it.status == "PENDING" }
                if (pending.isEmpty() && !hasGroupCorrectionsSnapshot) {
                    item {
                        PendingSyncEmptyState(
                            dataLabel = "طلبات تصويب الشعبة",
                            syncState = when {
                                isGroupCorrectionsLoading -> com.magd.tanweer.data.model.SyncState.Loading
                                !groupCorrectionsError.isNullOrBlank() -> com.magd.tanweer.data.model.SyncState.Error(groupCorrectionsError ?: "تعذر تحميل التصويبات", false)
                                else -> com.magd.tanweer.data.model.SyncState.Idle
                            }
                        )
                    }
                } else if (pending.isEmpty() && groupCorrectionsError.isNullOrBlank()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("✨", fontSize = tanweerTextSize(32f))
                                Text(
                                    text = "لا توجد طلبات تصحيح معلقة حالياً",
                                    fontSize = tanweerTextSize(14f),
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "جميع الدروس والشروحات مدققة ومحدثة في الشعبة.",
                                    fontSize = tanweerTextSize(12f),
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(pending) { corr ->
                        CorrectionItemCard(
                            corr = corr,
                            onApprove = {
                                viewModel.approveCorrection(corr.id) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onReject = {
                                viewModel.rejectCorrection(corr.id) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            1 -> {
                // One authoritative list: schedule proposals are filtered from community_decisions.
                val decisions = scopedCommunityDecisions
                val scheduleDecisions = decisions.filter {
                    it.requestType == "SCHEDULE" || it.requestType == "SCHEDULE_CHANGE"
                }
                val communityOnlyDecisions = decisions.filterNot {
                    it.requestType == "SCHEDULE" || it.requestType == "SCHEDULE_CHANGE"
                }
                val showSchedule = decisionFilter == "ALL" || decisionFilter == "SCHEDULE"
                val showCommunity = decisionFilter == "ALL" || decisionFilter == "COMMUNITY"

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("ALL", "كل القرارات", decisions.size),
                            Triple("SCHEDULE", "قرارات الجدول 📅", scheduleDecisions.size),
                            Triple("COMMUNITY", "القرارات الجماعية 🗳️", communityOnlyDecisions.size)
                        ).forEach { (key, label, count) ->
                            val isSel = decisionFilter == key
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSel) EmeraldGreen.copy(alpha = 0.25f) else GlassSurface)
                                    .border(1.dp, if (isSel) EmeraldGreen else GlassBorderSubtle, RoundedCornerShape(20.dp))
                                    .clickable { decisionFilter = key }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "$label ($count)",
                                    fontSize = tanweerTextSize(11f),
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) EmeraldGreen else TextSecondary
                                )
                            }
                        }
                    }
                }

                val visibleCount = when (decisionFilter) {
                    "SCHEDULE" -> scheduleDecisions.size
                    "COMMUNITY" -> communityOnlyDecisions.size
                    else -> decisions.size
                }

                if (visibleCount == 0 && !hasCommunityDecisionsSnapshot) {
                    item {
                        PendingSyncEmptyState(
                            dataLabel = "قرارات الشعبة",
                            syncState = when {
                                isCommunityDecisionsLoading -> com.magd.tanweer.data.model.SyncState.Loading
                                !communityDecisionsError.isNullOrBlank() -> com.magd.tanweer.data.model.SyncState.Error(communityDecisionsError ?: "تعذر تحميل القرارات", false)
                                else -> com.magd.tanweer.data.model.SyncState.Idle
                            }
                        )
                    }
                } else if (visibleCount == 0 && communityDecisionsError.isNullOrBlank()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🗳️", fontSize = tanweerTextSize(32f))
                                Text(
                                    text = "لا توجد قرارات في هذا التصنيف",
                                    fontSize = tanweerTextSize(14f),
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "تظهر مقترحات الجدول هنا ضمن محرك القرارات الموحد، مع التصويت والحالة والنتيجة في سجل واحد.",
                                    fontSize = tanweerTextSize(12f),
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    if (showSchedule && scheduleDecisions.isNotEmpty()) {
                        item {
                            Text(
                                text = "📅 قرارات ومقترحات الجدول (${scheduleDecisions.size}):",
                                fontSize = tanweerTextSize(13f),
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        items(scheduleDecisions, key = { it.id }) { decision ->
                            CommunityDecisionModeratorCard(
                                decision = decision,
                                onVote = { choice ->
                                    viewModel.voteCommunityDecision(decision.id, choice) { _, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }

                    if (showCommunity && communityOnlyDecisions.isNotEmpty()) {
                        item {
                            Text(
                                text = "🗳️ القرارات الجماعية (${communityOnlyDecisions.size}):",
                                fontSize = tanweerTextSize(13f),
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(communityOnlyDecisions, key = { it.id }) { decision ->
                            CommunityDecisionModeratorCard(
                                decision = decision,
                                onVote = { choice ->
                                    viewModel.voteCommunityDecision(decision.id, choice) { _, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            2 -> {
                // Group Members Management Shortcut
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MidnightSurface,
                        onClick = { viewModel.setSubScreen(SubScreen.GROUPS) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("👥", fontSize = tanweerTextSize(24f))
                                Column {
                                    Text(
                                        text = "إدارة صلاحيات ورتب أعضاء الشعبة",
                                        fontSize = tanweerTextSize(14f),
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "الترقيات، معالجة طلبات الانضمام، وحظر الحسابات المسيئة",
                                        fontSize = tanweerTextSize(11f),
                                        color = TextSecondary
                                    )
                                }
                            }
                            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = CyanAccent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CorrectionItemCard(
    corr: ContentCorrectionItem,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MidnightSurface,
        borderColor = WarmAmber.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("✍️", fontSize = tanweerTextSize(18f))
                    Text(
                        text = "طلب تصويب: ${corr.fieldName}",
                        fontSize = tanweerTextSize(14f),
                        fontWeight = FontWeight.Bold,
                        color = WarmAmber
                    )
                }
                GlassPill(
                    text = "قيد المراجعة ⏳",
                    color = WarmAmber,
                    bgColor = WarmAmber.copy(alpha = 0.15f)
                )
            }

            if (!corr.contentTitle.isNullOrBlank()) {
                Text(
                    text = "📚 على الدرس: ${corr.contentTitle}",
                    fontSize = tanweerTextSize(12f),
                    fontWeight = FontWeight.SemiBold,
                    color = CyanAccent
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MidnightBackground.copy(alpha = 0.7f))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "القيمة المصوبة المقترحة:",
                        fontSize = tanweerTextSize(11f),
                        color = TextMuted
                    )
                    Text(
                        text = corr.proposedValue,
                        fontSize = tanweerTextSize(13f),
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "سبب التصويب: ${corr.reason}",
                        fontSize = tanweerTextSize(11f),
                        color = TextSecondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "بواسطة: ${corr.authorName ?: "طالب في الشعبة"}",
                    fontSize = tanweerTextSize(11f),
                    color = TextMuted
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassOutlinedButton(
                        text = "رفض ❌",
                        borderColor = RubyRed,
                        textColor = RubyRed,
                        onClick = onReject,
                        modifier = Modifier.height(34.dp)
                    )
                    GlassButton(
                        text = "اعتماد وتطبيق ✅",
                        color = EmeraldGreen,
                        textColor = TextOnAccent,
                        onClick = onApprove,
                        modifier = Modifier.height(34.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CommunityDecisionModeratorCard(
    decision: CommunityDecisionItem,
    onVote: (Int) -> Unit
) {
    val reqTypeLabel = when (decision.requestType) {
        "DELETION", "CONTENT_DELETION" -> "طلب حذف محتوى 🗑️"
        "CORRECTION" -> "تصويب جماعي ✍️"
        "SCHEDULE", "SCHEDULE_CHANGE" -> "مقترح تعديل الجدول 📅"
        else -> "قرار جماعي 🗳️"
    }

    val totalVotes = decision.votesFor + decision.votesAgainst
    val calculatedThreshold = kotlin.math.ceil(
        (decision.totalEligibleVoters * decision.thresholdPercent) / 100.0
    ).toInt()
    // Keep the UI identical to the server quorum rule: at least two approvals
    // whenever more than one eligible voter exists.
    val requiredVotes = if (decision.totalEligibleVoters > 1) {
        maxOf(2, calculatedThreshold)
    } else {
        1
    }
    val progress = if (requiredVotes > 0) (decision.votesFor.toFloat() / requiredVotes.toFloat()).coerceIn(0f, 1f) else 0f

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MidnightSurface,
        borderColor = if (decision.status == "APPLIED" || decision.status == "APPROVED") EmeraldGreen.copy(alpha = 0.5f) else GlassBorder
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = reqTypeLabel,
                    fontSize = tanweerTextSize(14f),
                    fontWeight = FontWeight.Bold,
                    color = WarmAmber
                )
                val statusColor = when (decision.status) {
                    "APPLIED", "APPROVED" -> EmeraldGreen
                    "REJECTED" -> RubyRed
                    "EXPIRED" -> TextMuted
                    else -> WarmAmber
                }
                val statusText = when (decision.status) {
                    "APPLIED" -> "مقبول وتم التنفيذ ✅"
                    "APPROVED" -> "مقبول بالأغلبية ✅"
                    "REJECTED" -> "مرفوض ❌"
                    "EXPIRED" -> "انتهت مهلة التصويت ⌛"
                    else -> "قيد التصويت ⏳"
                }
                GlassPill(text = statusText, color = statusColor, bgColor = statusColor.copy(alpha = 0.15f))
            }

            Text(
                text = decision.title,
                fontSize = tanweerTextSize(13f),
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            if (!decision.description.isNullOrBlank()) {
                Text(
                    text = decision.description,
                    fontSize = tanweerTextSize(11f),
                    color = TextSecondary,
                    lineHeight = tanweerLineHeight(15f)
                )
            }

            // Unified voting totals and quorum progress
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "مؤيد 👍 ${decision.votesFor}",
                        fontSize = tanweerTextSize(10f),
                        color = EmeraldGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "معارض 👎 ${decision.votesAgainst}",
                        fontSize = tanweerTextSize(10f),
                        color = RubyRed,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "الإجمالي $totalVotes / ${decision.totalEligibleVoters}",
                        fontSize = tanweerTextSize(10f),
                        color = TextMuted
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "النصاب المطلوب: $requiredVotes صوت موافق",
                        fontSize = tanweerTextSize(10f),
                        color = CyanAccent
                    )
                    Text(
                        text = "نسبة النصاب: ${(progress * 100).toInt()}%",
                        fontSize = tanweerTextSize(10f),
                        color = TextMuted
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (progress >= 1f) EmeraldGreen else CyanAccent,
                    trackColor = GlassSurface
                )
            }

            if (decision.status == "PENDING") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassButton(
                        text = "أؤيد القرار 👍 (${decision.votesFor})",
                        color = if (decision.myVote == 1) EmeraldGreen else GlassSurface,
                        textColor = if (decision.myVote == 1) TextOnAccent else EmeraldGreen,
                        onClick = { onVote(1) },
                        modifier = Modifier.weight(1f).height(36.dp)
                    )
                    GlassOutlinedButton(
                        text = "أعارض 👎 (${decision.votesAgainst})",
                        borderColor = if (decision.myVote == 0) RubyRed else GlassBorder,
                        textColor = if (decision.myVote == 0) RubyRed else TextSecondary,
                        onClick = { onVote(0) },
                        modifier = Modifier.weight(1f).height(36.dp)
                    )
                }
            }
        }
    }
}
