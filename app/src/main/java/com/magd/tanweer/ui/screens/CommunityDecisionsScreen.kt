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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.CommunityDecisionItem
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*

@Composable
fun CommunityDecisionsScreen(
    viewModel: TanweerViewModel
) {
    val context = LocalContext.current
    val decisions by viewModel.communityDecisions.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadCommunityDecisions()
    }

    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "PENDING", "COMPLETED"

    val filteredList = when (selectedFilter) {
        "PENDING" -> decisions.filter { it.status == "PENDING" }
        "COMPLETED" -> decisions.filter { it.status != "PENDING" }
        else -> decisions
    }

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
                                Text("🗳️", fontSize = 26.sp)
                            }
                            Column {
                                Text(
                                    text = "القرارات والتصويتات الجماعية",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "حوكمة تشاركية للدروس، الجداول، وطلبات الحذف",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                viewModel.loadCommunityDecisions()
                                Toast.makeText(context, "تم تحديث القرارات والتصويتات 🔄", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GlassSurface)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = CyanAccent, modifier = Modifier.size(20.dp))
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
                            fontSize = 11.sp,
                            color = TextMuted,
                            lineHeight = 15.sp
                        )
                    }
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
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) TextOnAccent else TextSecondary
                        )
                    }
                }
            }
        }

        // Decisions List
        if (filteredList.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("✨", fontSize = 32.sp)
                        Text(
                            text = "لا توجد قرارات مطابقة للفلتر المحدد",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
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
