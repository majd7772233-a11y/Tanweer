package com.magd.tanweer.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.CommunityDecisionItem
import com.magd.tanweer.data.model.SyncState
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*

@Composable
fun CommunityDecisionsScreen(
    viewModel: TanweerViewModel
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId.orEmpty() }
    val decisions by viewModel.communityDecisions.collectAsStateWithLifecycle()
    val isLoading by viewModel.isCommunityDecisionsLoading.collectAsStateWithLifecycle()
    val loadError by viewModel.communityDecisionsError.collectAsStateWithLifecycle()
    val snapshotGroupId by viewModel.communityDecisionsSnapshotGroupId.collectAsStateWithLifecycle()
    val hasDecisionSnapshot = activeGroupId.isNotBlank() && snapshotGroupId == activeGroupId

    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "PENDING", "COMPLETED"

    val scopedDecisions = itemsForSnapshotScope(decisions, snapshotGroupId, activeGroupId)
    val filteredList = when (selectedFilter) {
        "PENDING" -> scopedDecisions.filter { it.status == "PENDING" }
        "COMPLETED" -> scopedDecisions.filter { it.status != "PENDING" }
        else -> scopedDecisions
    }
    val reviewRequiredCount = scopedDecisions.count { it.status == "REVIEW_REQUIRED" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        // Hero Header
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MidnightSurface,
                borderColor = CyanAccent.copy(alpha = 0.6f)
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
                                    .background(CyanGlow),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🗳️", fontSize = tanweerTextSize(26f))
                            }
                            Column {
                                Text(
                                    text = "القرارات والتصويتات الجماعية",
                                    fontSize = tanweerTextSize(17f),
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "حوكمة تشاركية للدروس، الجداول، وطلبات الحذف",
                                    fontSize = tanweerTextSize(12f),
                                    color = TextSecondary
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.loadCommunityDecisions(activeGroupId) },
                            enabled = !isLoading,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GlassSurface)
                        ) {
                            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = CyanAccent)
                            else Icon(Icons.Default.Refresh, contentDescription = "تحديث القرارات", tint = CyanAccent, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MidnightBackground.copy(alpha = 0.6f))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "⚖️ نظام القرارات الجماعية: عند اكتمال النصاب القانوني المطلوب من أصوات الشعبة، يتم تنفيذ القرار فورياً وتلقائياً عبر الخادم (مثل حذف منشور مكرر أو اعتماد تعديل).",
                            fontSize = tanweerTextSize(11f),
                            color = TextMuted,
                            lineHeight = tanweerLineHeight(15f)
                        )
                    }
                }
            }
        }

        if (reviewRequiredCount > 0) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = WarmAmber.copy(alpha = 0.08f),
                    borderColor = WarmAmber.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = stringResource(R.string.community_decisions_review_required_summary, reviewRequiredCount),
                        color = WarmAmber,
                        fontSize = tanweerTextSize(11f),
                        lineHeight = tanweerLineHeight(16f)
                    )
                }
            }
        }

        // Filter Pills
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("الكل 📋" to "ALL", "قيد التصويت ⏳" to "PENDING", "المكتملة ✅" to "COMPLETED").forEach { (label, key) ->
                    val isSelected = selectedFilter == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) CyanAccent else GlassSurface)
                            .clickable { selectedFilter = key }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = tanweerTextSize(12f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) TextOnAccent else TextSecondary
                        )
                    }
                }
            }
        }

        // Decisions List
        if (loadError != null) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = MidnightSurface, borderColor = RubyRed.copy(alpha = 0.5f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("تعذر تحميل القرارات والتصويتات", color = RubyRed, fontWeight = FontWeight.Bold)
                        Text(loadError ?: "", color = TextSecondary, fontSize = tanweerTextSize(11f))
                        TextButton(onClick = { viewModel.loadCommunityDecisions(activeGroupId) }) {
                            Text("إعادة المحاولة", color = CyanAccent)
                        }
                    }
                }
            }
        }

        if (filteredList.isEmpty()) {
            item {
                if (!hasDecisionSnapshot) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PendingSyncEmptyState(
                            dataLabel = "القرارات والتصويتات الجماعية",
                            syncState = when {
                                isLoading -> SyncState.Loading
                                loadError != null -> SyncState.Error(loadError ?: "تعذر تحميل القرارات", false)
                                else -> SyncState.Idle
                            }
                        )
                        TextButton(
                            onClick = { viewModel.loadCommunityDecisions(activeGroupId) },
                            enabled = !isLoading
                        ) {
                            Text("التحقق من القرارات", color = CyanAccent)
                        }
                    }
                } else {
                    GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("✨", fontSize = tanweerTextSize(32f))
                            Text(
                                text = "لا توجد قرارات مطابقة للفلتر المحدد",
                                fontSize = tanweerTextSize(14f),
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        } else {
            items(filteredList) { decision ->
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
}
