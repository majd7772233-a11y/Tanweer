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
import com.magd.tanweer.data.model.ScheduleProposalItem
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*

@Composable
fun ModeratorDashboardScreen(
    viewModel: TanweerViewModel
) {
    val context = LocalContext.current
    val dashboardData by viewModel.moderatorDashboard.collectAsStateWithLifecycle()
    val groupCorrections by viewModel.groupCorrections.collectAsStateWithLifecycle()
    val communityDecisions by viewModel.communityDecisions.collectAsStateWithLifecycle()
    val scheduleProposals by viewModel.scheduleProposals.collectAsStateWithLifecycle()
    val groupMembers by viewModel.groupMembersState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadModeratorDashboard()
        viewModel.loadGroupCorrections()
        viewModel.loadCommunityDecisions()
        viewModel.loadScheduleProposals()
        viewModel.loadGroupMembers()
    }

    var selectedSection by remember { mutableIntStateOf(0) } // 0: Corrections, 1: Schedule Proposals, 2: Community Decisions, 3: Members

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
                                Text("🛡️", fontSize = 26.sp)
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "مركز إشراف الشعبة",
                                        fontSize = 17.sp,
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
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                viewModel.loadModeratorDashboard()
                                viewModel.loadGroupCorrections()
                                viewModel.loadCommunityDecisions()
                                viewModel.loadScheduleProposals()
                                viewModel.loadGroupMembers()
                                Toast.makeText(context, "تم تحديث بيانات مركز الإشراف 🔄", Toast.LENGTH_SHORT).show()
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

                    // Summary Stats Row
                    val stats = dashboardData?.stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TeacherStatCard(
                            title = "تصويبات معلقة",
                            count = "${stats?.pendingCorrectionsCount ?: groupCorrections.count { it.status == "PENDING" }}",
                            icon = "✍️",
                            color = WarmAmber,
                            modifier = Modifier.weight(1f)
                        )
                        TeacherStatCard(
                            title = "مقترحات جدول",
                            count = "${stats?.pendingProposalsCount ?: scheduleProposals.count { it.status == "PENDING" }}",
                            icon = "📅",
                            color = CyanAccent,
                            modifier = Modifier.weight(1f)
                        )
                        TeacherStatCard(
                            title = "قرارات وتصويت",
                            count = "${stats?.pendingDecisionsCount ?: communityDecisions.count { it.status == "PENDING" }}",
                            icon = "🗳️",
                            color = EmeraldGreen,
                            modifier = Modifier.weight(1f)
                        )
                        TeacherStatCard(
                            title = "أعضاء الشعبة",
                            count = "${stats?.totalMembers ?: (groupMembers?.members?.size ?: 0)}",
                            icon = "👥",
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
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
                    "مقترحات الجدول 📅",
                    "القرارات الجماعية 🗳️",
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
                            fontSize = 11.sp,
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
                val pending = groupCorrections.filter { it.status == "PENDING" }
                if (pending.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("✨", fontSize = 32.sp)
                                Text(
                                    text = "لا توجد طلبات تصحيح معلقة حالياً",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "جميع الدروس والشروحات مدققة ومحدثة في الشعبة.",
                                    fontSize = 12.sp,
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
                // Schedule Proposals
                val pendingProposals = scheduleProposals.filter { it.status == "PENDING" }
                if (pendingProposals.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("📅", fontSize = 32.sp)
                                Text(
                                    text = "لا توجد مقترحات جدول معلقة",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "الجدول الأسبوعي مستقر ولا توجد مقترحات تصويت قيد الانتظار.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(pendingProposals) { prop ->
                        ScheduleProposalReviewCard(
                            prop = prop,
                            onApprove = {
                                viewModel.approveScheduleProposal(prop.id) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onReject = {
                                viewModel.rejectScheduleProposal(prop.id) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            2 -> {
                // Community Decisions & Deletions
                val decisions = communityDecisions
                if (decisions.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🗳️", fontSize = 32.sp)
                                Text(
                                    text = "لا توجد قرارات تصويت جماعية مفتوحة",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "القرارات الجماعية تشمل طلبات حذف الدروس والتصويتات التشاركية للشعبة.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(decisions) { decision ->
                        CommunityDecisionModeratorCard(
                            decision = decision,
                            onVote = { choice ->
                                viewModel.voteCommunityDecision(decision.id, choice) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            3 -> {
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
                                Text("👥", fontSize = 24.sp)
                                Column {
                                    Text(
                                        text = "إدارة صلاحيات ورتب أعضاء الشعبة",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "الترقيات، معالجة طلبات الانضمام، وحظر الحسابات المسيئة",
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
                    Text("✍️", fontSize = 18.sp)
                    Text(
                        text = "طلب تصويب: ${corr.fieldName}",
                        fontSize = 14.sp,
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
                    fontSize = 12.sp,
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
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Text(
                        text = corr.proposedValue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "سبب التصويب: ${corr.reason}",
                        fontSize = 11.sp,
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
                    fontSize = 11.sp,
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
fun ScheduleProposalReviewCard(
    prop: ScheduleProposalItem,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val dayName = when (prop.dayOfWeek) {
        0 -> "الأحد"
        1 -> "الإثنين"
        2 -> "الثلاثاء"
        3 -> "الأربعاء"
        4 -> "الخميس"
        else -> "يوم دراسي"
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MidnightSurface,
        borderColor = CyanAccent.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📅 مقترح تعديل: $dayName — الحصة ${prop.slotOrder}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanAccent
                )
                GlassPill(
                    text = "${prop.votesFor + prop.votesAgainst} أصوات",
                    color = CyanAccent,
                    bgColor = CyanAccent.copy(alpha = 0.15f)
                )
            }

            Text(
                text = "المادة المقترحة: ${prop.newSubjectName} (سبب التغيير: ${prop.reason})",
                fontSize = 12.sp,
                color = TextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "بواسطة: ${prop.proposerName}",
                    fontSize = 11.sp,
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
                        text = "اعتماد في الجدول ✅",
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
        "DELETION" -> "طلب حذف محتوى 🗑️"
        "CORRECTION" -> "تصويب جماعي ✍️"
        "SCHEDULE" -> "تعديل جدول 📅"
        else -> "قرار جماعي 🗳️"
    }

    val totalVotes = decision.votesFor + decision.votesAgainst
    val requiredVotes = maxOf(1, kotlin.math.ceil((decision.totalEligibleVoters * decision.thresholdPercent) / 100.0).toInt())
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = WarmAmber
                )
                val statusColor = when (decision.status) {
                    "APPLIED", "APPROVED" -> EmeraldGreen
                    "REJECTED" -> RubyRed
                    else -> WarmAmber
                }
                val statusText = when (decision.status) {
                    "APPLIED" -> "تم التنفيذ تلقائياً ✅"
                    "APPROVED" -> "معتمد بالأغلبية ✨"
                    "REJECTED" -> "مرفوض ❌"
                    else -> "تصويت جاري ⏳"
                }
                GlassPill(text = statusText, color = statusColor, bgColor = statusColor.copy(alpha = 0.15f))
            }

            Text(
                text = decision.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            if (!decision.description.isNullOrBlank()) {
                Text(
                    text = decision.description,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }

            // Voting Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "الأصوات المؤيدة: ${decision.votesFor} من أصل $requiredVotes مطلوبة",
                        fontSize = 10.sp,
                        color = CyanAccent
                    )
                    Text(
                        text = "نسبة النصاب: ${(progress * 100).toInt()}%",
                        fontSize = 10.sp,
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
