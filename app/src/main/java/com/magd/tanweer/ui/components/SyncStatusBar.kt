package com.magd.tanweer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magd.tanweer.data.model.SyncState
import com.magd.tanweer.ui.theme.*

@Composable
fun SyncStatusBar(
    syncState: SyncState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBusy = syncState.isBusy()

    val infiniteTransition = rememberInfiniteTransition(label = "syncRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier.fillMaxWidth()
    ) {
        val (bgColor, borderColor, textColor, icon, labelText) = when (syncState) {
            is SyncState.Loading, is SyncState.Refreshing -> {
                Quintet(
                    Color(0x2200E5FF),
                    CyanAccent.copy(alpha = 0.5f),
                    CyanAccent,
                    Icons.Default.Refresh,
                    "جاري المزامنة مع السيرفر وتحديث البيانات..."
                )
            }
            is SyncState.OfflineCached -> {
                val timeStr = formatRelativeTime(syncState.lastSyncedAt)
                Quintet(
                    Color(0x22FFB300),
                    WarmAmber.copy(alpha = 0.6f),
                    WarmAmber,
                    Icons.Default.CloudOff,
                    "⚡ وضع الأوفلاين • آخر تحديث: $timeStr"
                )
            }
            is SyncState.Error -> {
                Quintet(
                    Color(0x22FF3366),
                    RubyRed.copy(alpha = 0.6f),
                    RubyRed,
                    Icons.Default.Warning,
                    if (syncState.isOffline) "⚡ تعذر الاتصال بالسيرفر • تعمل محلياً" else syncState.message
                )
            }
            is SyncState.Success -> {
                val timeStr = formatRelativeTime(syncState.lastSyncedAt)
                Quintet(
                    Color(0x1800E676),
                    EmeraldGreen.copy(alpha = 0.4f),
                    EmeraldGreen,
                    Icons.Default.Done,
                    "مُحدّث • آخر مزامنة: $timeStr"
                )
            }
            is SyncState.Idle -> {
                Quintet(
                    GlassSurface,
                    GlassBorderSubtle,
                    TextSecondary,
                    Icons.Default.Refresh,
                    "جاهز للمزامنة"
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier
                            .size(16.dp)
                            .then(if (isBusy) Modifier.rotate(rotation) else Modifier)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = labelText,
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Refresh Action Button
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = !isBusy) { onRefresh() }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تحديث الآن",
                        tint = if (isBusy) textColor.copy(alpha = 0.4f) else textColor,
                        modifier = Modifier
                            .size(14.dp)
                            .then(if (isBusy) Modifier.rotate(rotation) else Modifier)
                    )
                }
            }
        }
    }
}

private data class Quintet<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60

    return when {
        diff < 0 -> "الآن"
        seconds < 60 -> "منذ لحظات"
        minutes < 60 -> "منذ $minutes دقيقة"
        hours < 24 -> "منذ $hours ساعة"
        else -> "منذ أكثر من يوم"
    }
}
