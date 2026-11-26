package com.magd.tanweer.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.R
import com.magd.tanweer.data.model.AppNotification
import com.magd.tanweer.data.model.SyncState
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*
import com.magd.tanweer.util.NotificationInboxFilter
import com.magd.tanweer.util.NotificationInboxPolicy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationsScreen(viewModel: TanweerViewModel) {
    val context = LocalContext.current
    val activeNotifications by viewModel.myNotifications.collectAsStateWithLifecycle()
    val archivedNotifications by viewModel.myArchivedNotifications.collectAsStateWithLifecycle()
    val activeSyncState by viewModel.myNotificationsSyncState.collectAsStateWithLifecycle()
    val archivedSyncState by viewModel.myArchivedNotificationsSyncState.collectAsStateWithLifecycle()
    var showingArchive by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(NotificationInboxFilter.ALL) }

    val visibleNotifications = remember(activeNotifications, archivedNotifications, showingArchive, filter) {
        if (showingArchive) {
            archivedNotifications
                .filterNot { it.type == "RECOVERY_CODE_RESET" }
                .sortedByDescending { it.createdAt }
        } else {
            NotificationInboxPolicy.filter(activeNotifications, filter)
        }
    }
    val syncState = if (showingArchive) archivedSyncState else activeSyncState

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MidnightSurface,
                borderColor = CyanAccent.copy(alpha = 0.32f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(CyanAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = CyanAccent)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.notifications_title),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = tanweerTextSize(16f)
                        )
                        val unreadCount = NotificationInboxPolicy.unreadCount(activeNotifications)
                        Text(
                            context.resources.getQuantityString(R.plurals.notifications_unread_count, unreadCount, unreadCount),
                            color = TextSecondary,
                            fontSize = tanweerTextSize(11f)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.loadMyNotifications(archived = showingArchive) },
                        enabled = syncState !is SyncState.Loading && syncState !is SyncState.Refreshing
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.notifications_retry), tint = CyanAccent)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !showingArchive,
                    onClick = { showingArchive = false; filter = NotificationInboxFilter.ALL },
                    label = { Text(stringResource(R.string.notifications_inbox_tab)) },
                    leadingIcon = if (!showingArchive) {
                        { Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = showingArchive,
                    onClick = { showingArchive = true },
                    label = { Text(stringResource(R.string.notifications_archive_tab)) },
                    leadingIcon = if (showingArchive) {
                        { Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }

        if (!showingArchive) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = filter == NotificationInboxFilter.ALL,
                        onClick = { filter = NotificationInboxFilter.ALL },
                        label = { Text(stringResource(R.string.notifications_filter_all)) }
                    )
                    FilterChip(
                        selected = filter == NotificationInboxFilter.UNREAD,
                        onClick = { filter = NotificationInboxFilter.UNREAD },
                        label = { Text(stringResource(R.string.notifications_filter_unread)) }
                    )
                    FilterChip(
                        selected = filter == NotificationInboxFilter.READ,
                        onClick = { filter = NotificationInboxFilter.READ },
                        label = { Text(stringResource(R.string.notifications_filter_read)) }
                    )
                }
            }
        }

        if (syncState is SyncState.Error && visibleNotifications.isNotEmpty()) {
            item {
                Text(
                    text = syncState.message,
                    color = WarmAmber,
                    fontSize = tanweerTextSize(11f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                )
            }
        }

        when {
            visibleNotifications.isEmpty() && (syncState is SyncState.Loading || syncState is SyncState.Refreshing) -> {
                item {
                    InboxStateCard(
                        text = stringResource(R.string.notifications_loading),
                        showRetry = false,
                        onRetry = {}
                    )
                }
            }
            visibleNotifications.isEmpty() && syncState is SyncState.Error -> {
                item {
                    InboxStateCard(
                        text = syncState.message,
                        showRetry = true,
                        onRetry = { viewModel.loadMyNotifications(archived = showingArchive) }
                    )
                }
            }
            visibleNotifications.isEmpty() -> {
                item {
                    InboxStateCard(
                        text = stringResource(
                            if (showingArchive) R.string.notifications_empty_archive else R.string.notifications_empty_inbox
                        ),
                        showRetry = false,
                        onRetry = {}
                    )
                }
            }
            else -> {
                items(visibleNotifications, key = { it.id }) { notification ->
                    NotificationInboxCard(
                        notification = notification,
                        archived = showingArchive,
                        onMarkRead = {
                            viewModel.markMyNotificationRead(notification.id) { success, message ->
                                Toast.makeText(
                                    context,
                                    if (success) context.getString(R.string.notifications_marked_read) else message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onArchive = {
                            viewModel.archiveMyNotification(notification.id) { success, message ->
                                Toast.makeText(
                                    context,
                                    if (success) context.getString(R.string.notifications_archived_success) else message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onRestore = {
                            viewModel.restoreMyNotification(notification.id) { success, message ->
                                Toast.makeText(
                                    context,
                                    if (success) context.getString(R.string.notifications_restored_success) else message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InboxStateCard(
    text: String,
    showRetry: Boolean,
    onRetry: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = MidnightSurface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text, color = TextSecondary, fontSize = tanweerTextSize(13f), textAlign = TextAlign.Center)
            if (showRetry) {
                GlassOutlinedButton(
                    text = stringResource(R.string.notifications_retry),
                    onClick = onRetry,
                    modifier = Modifier.height(38.dp)
                )
            }
        }
    }
}

@Composable
private fun NotificationInboxCard(
    notification: AppNotification,
    archived: Boolean,
    onMarkRead: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit
) {
    val isRead = notification.readAt != null
    val dateLabel = remember(notification.createdAt) {
        if (notification.createdAt > 0) {
            SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(notification.createdAt))
        } else ""
    }
    val accent = when (notification.type) {
        "GROUP_JOIN_REQUEST" -> WarmAmber
        "GROUP_JOIN_ACCEPTED" -> EmeraldGreen
        "GROUP_JOIN_REJECTED" -> RubyRed
        else -> CyanAccent
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MidnightSurface,
        borderColor = if (isRead) GlassBorderSubtle else accent.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isRead) TextMuted else accent)
                    )
                    Text(
                        notification.title,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = tanweerTextSize(13f)
                    )
                }
                GlassPill(
                    text = stringResource(if (isRead) R.string.notifications_status_read else R.string.notifications_status_unread),
                    color = if (isRead) TextSecondary else accent,
                    bgColor = (if (isRead) TextMuted else accent).copy(alpha = 0.14f)
                )
            }
            Text(
                notification.body,
                color = TextSecondary,
                fontSize = tanweerTextSize(12f),
                lineHeight = tanweerLineHeight(17f)
            )
            if (dateLabel.isNotBlank()) {
                Text(dateLabel, color = TextMuted, fontSize = tanweerTextSize(10f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (archived) {
                    GlassOutlinedButton(
                        text = stringResource(R.string.notifications_restore_action),
                        onClick = onRestore,
                        modifier = Modifier.height(36.dp)
                    )
                } else {
                    if (!isRead) {
                        GlassOutlinedButton(
                            text = stringResource(R.string.notifications_mark_read),
                            onClick = onMarkRead,
                            modifier = Modifier.height(36.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    GlassOutlinedButton(
                        text = stringResource(R.string.notifications_archive_action),
                        onClick = onArchive,
                        modifier = Modifier.height(36.dp)
                    )
                }
            }
        }
    }
}
