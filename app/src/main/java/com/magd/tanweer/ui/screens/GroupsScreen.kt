package com.magd.tanweer.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.R
import com.magd.tanweer.data.model.ChatMessageItem
import com.magd.tanweer.data.model.GroupItem
import com.magd.tanweer.data.model.Role
import com.magd.tanweer.data.model.getRoleEnum
import com.magd.tanweer.data.remote.ConnectionStatus
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*
import kotlinx.coroutines.launch

enum class GroupsTab(val label: String) {
    MY_GROUPS("مجموعاتي والشعبة"),
    DISCOVER("اكتشف المجموعات 🔍")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupsScreen(
    viewModel: TanweerViewModel
) {
    val context = LocalContext.current
    val myGroups by viewModel.repository.getMyGroups().collectAsStateWithLifecycle(initialValue = emptyList())
    val discoverGroups by viewModel.repository.getDiscoverGroups().collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val chatStatus by viewModel.chatConnectionStatus.collectAsStateWithLifecycle()
    val chatHistorySyncState by viewModel.chatHistorySyncState.collectAsStateWithLifecycle()
    val chatHistorySyncGroupId by viewModel.chatHistorySyncGroupId.collectAsStateWithLifecycle()
    val groupsSyncState by viewModel.groupsSyncState.collectAsStateWithLifecycle()
    val groupMembersState by viewModel.groupMembersState.collectAsStateWithLifecycle()
    val isGroupMembersLoading by viewModel.isGroupMembersLoading.collectAsStateWithLifecycle()
    val groupMembersError by viewModel.groupMembersError.collectAsStateWithLifecycle()
    val userRole = currentUser?.getRoleEnum() ?: Role.STUDENT
    val canManageMembers = userRole == Role.ADMIN || userRole == Role.TEACHER || userRole == Role.MODERATOR ||
        currentUser?.role == "SYSTEM_OWNER"
    val canManageGroupMembers: (GroupItem) -> Boolean = { group ->
        canManageMembers || group.role in setOf("MODERATOR", "TEACHER", "ADMIN")
    }

    var activeTab by remember { mutableStateOf(GroupsTab.MY_GROUPS) }
    var groupSearchQuery by remember { mutableStateOf("") }
    val visibleMyGroups = remember(myGroups, groupSearchQuery) {
        val query = groupSearchQuery.trim()
        if (query.isBlank()) myGroups else myGroups.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.description.orEmpty().contains(query, ignoreCase = true)
        }
    }
    val visibleDiscoverGroups = remember(discoverGroups, groupSearchQuery) {
        val query = groupSearchQuery.trim()
        if (query.isBlank()) discoverGroups else discoverGroups.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.description.orEmpty().contains(query, ignoreCase = true)
        }
    }
    var activeChatGroup by remember { mutableStateOf<GroupItem?>(null) }
    val scopedChatHistorySyncState = syncStateForScope(chatHistorySyncState, chatHistorySyncGroupId, activeChatGroup?.id.orEmpty())
    var managingGroup by remember { mutableStateOf<GroupItem?>(null) }
    var joinRequestGroup by remember { mutableStateOf<GroupItem?>(null) }
    var joinReasonText by remember { mutableStateOf("") }
    var isSubmittingJoin by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    val activeChatGroupCanSend = activeChatGroup?.let { group ->
        group.chatPolicy != "STAFF_ONLY" ||
            userRole == Role.TEACHER ||
            userRole == Role.ADMIN ||
            currentUser?.role == "SYSTEM_OWNER" ||
            group.role == "TEACHER" ||
            group.role == "ADMIN"
    } ?: false
    val chatMessages by viewModel.repository.getChatMessages(activeChatGroup?.id ?: "")
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val activeChatGroupId = activeChatGroup?.id
    // A previous group's last Room emission must never flash or affect the new group's scroll state.
    val visibleChatMessages = remember(chatMessages, activeChatGroupId) {
        chatMessages.filter { it.groupId == activeChatGroupId }
    }
    val chatMessageIds = remember(visibleChatMessages) { visibleChatMessages.map { it.id } }
    var chatScrollInitialized by remember(activeChatGroupId) { mutableStateOf(false) }
    var knownChatMessageIds by remember(activeChatGroupId) { mutableStateOf(emptyList<String>()) }
    var unreadChatMessageIds by remember(activeChatGroupId) { mutableStateOf(emptySet<String>()) }
    var chatInputText by remember { mutableStateOf("") }
    var isSendingChat by remember { mutableStateOf(false) }
    var pendingDeleteMessage by remember { mutableStateOf<ChatMessageItem?>(null) }
    var deletingMessageId by remember { mutableStateOf<String?>(null) }

    val pendingChatGroupId by viewModel.pendingDeepLinkChatGroupId.collectAsStateWithLifecycle()
    val pendingMessageId by viewModel.pendingDeepLinkMessageId.collectAsStateWithLifecycle()
    val chatListState = remember(activeChatGroupId) { LazyListState() }
    val chatScrollScope = rememberCoroutineScope()
    val chatBottomThresholdPx = with(LocalDensity.current) { 80.dp.roundToPx() }

    // Handle incoming deep link from Search
    LaunchedEffect(pendingChatGroupId, myGroups, discoverGroups) {
        val targetGId = pendingChatGroupId
        if (!targetGId.isNullOrBlank()) {
            val targetGroup = myGroups.find { it.id == targetGId }
                ?: discoverGroups.find { it.id == targetGId }
                ?: GroupItem(id = targetGId, name = "نقاش المجموعة", type = "CLASS")
            activeChatGroup = targetGroup
        }
    }

    var highlightedMessageId by remember { mutableStateOf<String?>(null) }

    // Scroll to a message reached through Search without letting initial auto-follow override it.
    LaunchedEffect(pendingMessageId, visibleChatMessages) {
        val targetMsgId = pendingMessageId
        if (!targetMsgId.isNullOrBlank() && visibleChatMessages.isNotEmpty()) {
            val index = visibleChatMessages.indexOfFirst { it.id == targetMsgId }
            if (index != -1) {
                highlightedMessageId = targetMsgId
                chatListState.animateScrollToItem(index)
                viewModel.clearPendingDeepLink()
                kotlinx.coroutines.delay(2500)
                highlightedMessageId = null
            }
        }
    }

    // Initialize at the latest message once Room has data (or the server confirms an empty history).
    // Later additions follow only when the user was already at the previous end of the list.
    LaunchedEffect(
        activeChatGroupId,
        chatMessageIds,
        scopedChatHistorySyncState,
        pendingMessageId,
        highlightedMessageId
    ) {
        if (activeChatGroupId.isNullOrBlank()) return@LaunchedEffect

        if (!chatScrollInitialized) {
            val hasInitialHistory = chatMessageIds.isNotEmpty() ||
                hasAuthoritativeSnapshot(scopedChatHistorySyncState)
            if (!hasInitialHistory) return@LaunchedEffect

            val deepLinkOwnsPosition = !highlightedMessageId.isNullOrBlank() ||
                (!pendingMessageId.isNullOrBlank() && chatMessageIds.contains(pendingMessageId))
            chatScrollInitialized = true
            knownChatMessageIds = chatMessageIds
            unreadChatMessageIds = emptySet()
            if (!deepLinkOwnsPosition && chatMessageIds.isNotEmpty()) {
                chatListState.scrollToItem(chatMessageIds.lastIndex)
            }
            return@LaunchedEffect
        }

        val delta = GroupChatAutoScrollPolicy.calculateDelta(
            previousIds = knownChatMessageIds,
            currentIds = chatMessageIds
        )
        val currentIds = chatMessageIds.toSet()
        val previousLastIndex = knownChatMessageIds.lastOrNull()
            ?.let(chatMessageIds::indexOf)
            ?: -1
        val layoutInfo = chatListState.layoutInfo
        val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
        val shouldFollowLatest = delta.addedIds.isNotEmpty() &&
            GroupChatAutoScrollPolicy.shouldAutoFollow(
                previousLastMessageIndex = previousLastIndex,
                lastVisibleItemIndex = lastVisibleItem?.index,
                lastVisibleItemEnd = lastVisibleItem?.let { it.offset + it.size },
                viewportEndOffset = layoutInfo.viewportEndOffset,
                thresholdPx = chatBottomThresholdPx
            )

        unreadChatMessageIds = GroupChatAutoScrollPolicy.mergeUnreadMessageIds(
            previousUnreadIds = unreadChatMessageIds,
            delta = delta,
            currentMessageIds = currentIds,
            shouldAutoFollow = shouldFollowLatest
        )

        if (shouldFollowLatest && chatMessageIds.isNotEmpty()) {
            chatListState.animateScrollToItem(chatMessageIds.lastIndex)
        }
        knownChatMessageIds = chatMessageIds
    }

    // Reading to the latest item manually also clears the unread affordance.
    val isLatestChatMessageVisible by remember(chatListState, chatMessageIds, chatBottomThresholdPx) {
        derivedStateOf {
            if (chatMessageIds.isEmpty()) {
                true
            } else {
                val layoutInfo = chatListState.layoutInfo
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                lastVisibleItem != null &&
                    lastVisibleItem.index == chatMessageIds.lastIndex &&
                    layoutInfo.viewportEndOffset - (lastVisibleItem.offset + lastVisibleItem.size) <= chatBottomThresholdPx
            }
        }
    }
    LaunchedEffect(isLatestChatMessageVisible) {
        if (isLatestChatMessageVisible) unreadChatMessageIds = emptySet()
    }

    // Connect WebSocket when inside chat, disconnect cleanly when activeChatGroup becomes null
    LaunchedEffect(activeChatGroup?.id) {
        val g = activeChatGroup
        if (g != null) {
            viewModel.connectGroupChat(g.id)
        } else {
            viewModel.disconnectGroupChat()
        }
    }

    // Ensure disconnect when GroupsScreen is entirely disposed/left
    DisposableEffect(Unit) {
        onDispose {
            viewModel.disconnectGroupChat()
            viewModel.clearPendingDeepLink()
        }
    }

    if (activeChatGroup != null) {
        BackHandler {
            activeChatGroup = null
            viewModel.disconnectGroupChat()
        }

        // -------------------------------------------------------------
        // GROUP CHAT VIEW (Live WebSocket + REST Sync + Room Persistence)
        // -------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MidnightBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MidnightSurface,
                    borderColor = CyanGlow
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                activeChatGroup = null
                                viewModel.disconnectGroupChat()
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "عودة",
                                    tint = CyanAccent
                                )
                            }
                            Text(text = activeChatGroup?.icon ?: "💬", fontSize = tanweerTextSize(22f))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = activeChatGroup?.name ?: "نقاش المجموعة",
                                    fontSize = tanweerTextSize(15f),
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "نقاشات مباشرة ولن تضيع الدروس والواجبات",
                                    fontSize = tanweerTextSize(11f),
                                    color = TextSecondary
                                )
                            }
                        }

                        when (chatStatus) {
                            ConnectionStatus.CONNECTED -> GlassPill(
                                text = "متصل 🟢",
                                color = EmeraldGreen,
                                bgColor = Color(0x2200E676)
                            )
                            ConnectionStatus.CONNECTING -> GlassPill(
                                text = "اتصال... 🟡",
                                color = WarmAmber,
                                bgColor = AmberGlow
                            )
                            ConnectionStatus.DISCONNECTED -> GlassPill(
                                text = "أوفلاين 🔴",
                                color = RubyRed,
                                bgColor = Color(0x22FF3366)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!hasAuthoritativeSnapshot(scopedChatHistorySyncState)) {
                    SyncStatusBar(
                        syncState = scopedChatHistorySyncState,
                        onRefresh = { activeChatGroup?.id?.let(viewModel::connectGroupChat) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Chat Messages List
                LazyColumn(
                    state = chatListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (visibleChatMessages.isEmpty() && hasAuthoritativeSnapshot(scopedChatHistorySyncState)) {
                        item {
                            EmptyStateGlass(
                                title = stringResource(com.magd.tanweer.R.string.chat_empty_title),
                                subtitle = stringResource(com.magd.tanweer.R.string.chat_empty_subtitle),
                                icon = "💬"
                            )
                        }
                    } else if (visibleChatMessages.isEmpty()) {
                        item {
                            PendingSyncEmptyState(
                                dataLabel = stringResource(com.magd.tanweer.R.string.chat_history_label),
                                syncState = scopedChatHistorySyncState
                            )
                        }
                    } else {
                        items(visibleChatMessages, key = { it.id }) { msg ->
                            val isMe = msg.senderId == currentUser?.id || msg.isMe
                            val isHighlighted = msg.id == highlightedMessageId
                            var isMessageMenuExpanded by remember(msg.id) { mutableStateOf(false) }
                            val canDeleteMessage = GroupChatMessageDeletePolicy.canDelete(
                                message = msg,
                                user = currentUser,
                                group = activeChatGroup
                            )
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth(0.82f)
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 16.dp,
                                                topEnd = 16.dp,
                                                bottomStart = if (isMe) 16.dp else 4.dp,
                                                bottomEnd = if (isMe) 4.dp else 16.dp
                                            )
                                        )
                                        .background(
                                            if (isHighlighted) WarmAmber.copy(alpha = 0.25f)
                                            else if (isMe) CyanGlow
                                            else MidnightSurface
                                        )
                                        .border(
                                            if (isHighlighted) 2.dp else 1.dp,
                                            if (isHighlighted) WarmAmber
                                            else if (isMe) CyanAccent.copy(alpha = 0.4f)
                                            else GlassBorderSubtle,
                                            RoundedCornerShape(16.dp)
                                        )
                                        .combinedClickable(
                                            enabled = canDeleteMessage,
                                            onClick = {},
                                            onLongClick = { isMessageMenuExpanded = true }
                                        )
                                        .padding(10.dp)
                                ) {
                                    if (!isMe) {
                                        Text(
                                            text = "${msg.senderName} (${msg.senderGradeSection})",
                                            fontSize = tanweerTextSize(11f),
                                            fontWeight = FontWeight.Bold,
                                            color = WarmAmber
                                        )
                                    }

                                    Text(
                                        text = msg.text,
                                        fontSize = tanweerTextSize(13f),
                                        color = TextPrimary,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )

                                    // Delivery Status indicator for me
                                    if (isMe) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            when (msg.status) {
                                                "SENDING" -> {
                                                    Text("⏳ جاري الإرسال...", fontSize = tanweerTextSize(9f), color = TextMuted)
                                                }
                                                "SENT" -> {
                                                    Text("✓✓ تم الإرسال", fontSize = tanweerTextSize(9f), color = CyanAccent)
                                                }
                                                "FAILED" -> {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.clickable {
                                                            viewModel.retryChatMessage(msg)
                                                            Toast.makeText(context, "إعادة إرسال الرسالة...", Toast.LENGTH_SHORT).show()
                                                        }
                                                    ) {
                                                        Text("⚠️ فشل الإرسال (اضغط للإعادة)", fontSize = tanweerTextSize(9f), color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = isMessageMenuExpanded,
                                        onDismissRequest = { isMessageMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(com.magd.tanweer.R.string.chat_message_delete_action)) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = null,
                                                    tint = RubyRed
                                                )
                                            },
                                            onClick = {
                                                isMessageMenuExpanded = false
                                                if (canDeleteMessage && msg.status.equals("SENT", ignoreCase = true)) {
                                                    pendingDeleteMessage = msg
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (activeChatGroupCanSend) {
                    // Server enforces this same permission on REST and WebSocket.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassTextField(
                            value = chatInputText,
                            onValueChange = { chatInputText = it },
                            label = "",
                            placeholder = "اكتب رسالة نقاش للمجموعة...",
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val text = chatInputText.trim()
                                val gId = activeChatGroup?.id
                                if (text.isNotBlank() && gId != null && !isSendingChat) {
                                    isSendingChat = true
                                    val sentText = text
                                    chatInputText = ""
                                    viewModel.sendChatMessage(sentText, targetGroupId = gId) { success ->
                                        isSendingChat = false
                                        if (!success) {
                                            chatInputText = sentText
                                            android.widget.Toast.makeText(context, "تعذر إرسال الرسالة، تم استعادة النص في حقل الكتابة", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isSendingChat && chatInputText.isNotBlank(),
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(if (chatInputText.isNotBlank() && !isSendingChat) CyanAccent else CyanAccent.copy(alpha = 0.5f))
                        ) {
                            if (isSendingChat) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = TextOnAccent,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Send, contentDescription = "إرسال", tint = TextOnAccent)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "🔒 هذه المجموعة للقراءة فقط للطلاب. الإرسال متاح للأساتذة وإدارة المدرسة.",
                        color = WarmAmber,
                        fontSize = tanweerTextSize(11f),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    )
                }
            }
            AnimatedVisibility(
                visible = unreadChatMessageIds.isNotEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (activeChatGroupCanSend) 72.dp else 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(CyanAccent)
                        .clickable {
                            val latestIndex = chatMessageIds.lastIndex
                            if (latestIndex >= 0) {
                                chatScrollScope.launch {
                                    chatListState.animateScrollToItem(latestIndex)
                                    unreadChatMessageIds = emptySet()
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = pluralStringResource(
                            com.magd.tanweer.R.plurals.chat_new_messages_count,
                            unreadChatMessageIds.size,
                            unreadChatMessageIds.size
                        ),
                        color = TextOnAccent,
                        fontSize = tanweerTextSize(12f),
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextOnAccent
                    )
                }
            }
        }
        pendingDeleteMessage?.let { message ->
            AlertDialog(
                onDismissRequest = {
                    if (deletingMessageId != message.id) pendingDeleteMessage = null
                },
                title = {
                    Text(stringResource(com.magd.tanweer.R.string.chat_message_delete_title))
                },
                text = {
                    Text(stringResource(com.magd.tanweer.R.string.chat_message_delete_message))
                },
                confirmButton = {
                    TextButton(
                        enabled = deletingMessageId != message.id,
                        onClick = {
                            if (deletingMessageId == null) {
                                deletingMessageId = message.id
                                viewModel.deleteGroupChatMessage(message) { success ->
                                    deletingMessageId = null
                                    if (success) {
                                        if (pendingDeleteMessage?.id == message.id) pendingDeleteMessage = null
                                        Toast.makeText(
                                            context,
                                            context.getString(com.magd.tanweer.R.string.chat_message_delete_success),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(com.magd.tanweer.R.string.chat_message_delete_failed),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        }
                    ) {
                        Text(
                            stringResource(
                                if (deletingMessageId == message.id)
                                    com.magd.tanweer.R.string.chat_message_delete_in_progress
                                else
                                    com.magd.tanweer.R.string.chat_message_delete_confirm
                            )
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = deletingMessageId != message.id,
                        onClick = { pendingDeleteMessage = null }
                    ) {
                        Text(stringResource(com.magd.tanweer.R.string.chat_message_delete_cancel))
                    }
                }
            )
        }

        return
    }

    // -------------------------------------------------------------
    // MAIN GROUPS LIST (Tabs: مجموعاتي vs اكتشف)
    // -------------------------------------------------------------
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Text(
                    text = "👥 المجموعات والشعب الدراسية",
                    fontSize = tanweerTextSize(22f),
                    fontWeight = FontWeight.Black,
                    color = CyanAccent
                )
                Text(
                    text = "شعبتك الرسمية المعتمدة والمجموعات الدراسية المشتركة",
                    fontSize = tanweerTextSize(12f),
                    color = TextSecondary
                )
            }

            // Tabs Selector
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MidnightSurface)
                        .border(1.dp, GlassBorderSubtle, RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    GroupsTab.values().forEach { tab ->
                        val isSelected = activeTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) CyanAccent else Color.Transparent)
                                .clickable { activeTab = tab }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.label,
                                fontSize = tanweerTextSize(12f),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TextOnAccent else TextSecondary
                            )
                        }
                    }
                }
            }

            // Search applies to both joined clubs and discoverable groups.
            item(key = "groups-search") {
                OutlinedTextField(
                    value = groupSearchQuery,
                    onValueChange = { groupSearchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = CyanAccent)
                    },
                    trailingIcon = {
                        if (groupSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { groupSearchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "مسح البحث", tint = TextSecondary)
                            }
                        }
                    },
                    placeholder = {
                        Text(
                            if (activeTab == GroupsTab.MY_GROUPS) "ابحث في مجموعاتك وأنديتك…" else "ابحث عن مجموعة أو نادٍ…",
                            fontSize = tanweerTextSize(11f),
                            color = TextMuted
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = GlassBorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = CyanAccent
                    ),
                    shape = RoundedCornerShape(13.dp)
                )
            }

            if (activeTab == GroupsTab.MY_GROUPS) {
                // Tab 1: MY GROUPS
                val officialClassGroup = visibleMyGroups.find {
                    it.type == "CLASS" || it.id.startsWith("class_") || it.role == "OFFICIAL"
                }
                val sharedGroups = visibleMyGroups.filter { it.id != officialClassGroup?.id }

                // 1. Official Section Group (الشعبة الرسمية الأساسية) - Distinguished Hero Card
                if (officialClassGroup != null) {
                    item {

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Text("👑", fontSize = tanweerTextSize(16f))
                                Text(
                                    text = "الشعبة الرسمية الأساسية المعتمدة",
                                    fontSize = tanweerTextSize(13f),
                                    fontWeight = FontWeight.Black,
                                    color = WarmAmber
                                )
                            }

                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                borderColor = WarmAmber.copy(alpha = 0.8f),
                                backgroundColor = WarmAmber.copy(alpha = 0.06f)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(WarmAmber.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = officialClassGroup.icon ?: "🏫", fontSize = tanweerTextSize(24f))
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = officialClassGroup.name,
                                                    fontSize = tanweerTextSize(16f),
                                                    fontWeight = FontWeight.Black,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = "${officialClassGroup.memberCount} طالب وطالبة • الصف والشعبة الرسمية",
                                                    fontSize = tanweerTextSize(12f),
                                                    color = WarmAmber
                                                )
                                            }
                                        }
                                        GlassPill(
                                            text = "شعبة نشطة ✓",
                                            color = EmeraldGreen,
                                            bgColor = EmeraldGreen.copy(alpha = 0.14f)
                                        )
                                    }

                                    Text(
                                        text = officialClassGroup.description ?: "الشعبة الرسمية المرتبطة بمدرستك والتي يُنشر فيها الجدول والواجبات والاختبارات المعتمدة.",
                                        fontSize = tanweerTextSize(12f),
                                        color = TextSecondary,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        GlassButton(
                                            text = "نقاش الشعبة 💬",
                                            icon = Icons.Default.Chat,
                                            onClick = { activeChatGroup = officialClassGroup },
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (canManageGroupMembers(officialClassGroup)) {
                                            GlassOutlinedButton(
                                                text = "إدارة الأعضاء 👥",
                                                onClick = {
                                                    managingGroup = officialClassGroup
                                                    viewModel.loadGroupMembers(officialClassGroup.id)
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Shared & Optional Groups
                item {
                    Text(
                        text = "المجموعات المشتركة والأندية (${sharedGroups.size}):",
                        fontSize = tanweerTextSize(14f),
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                if (sharedGroups.isEmpty() && !hasAuthoritativeSnapshot(groupsSyncState)) {
                    item {
                        PendingSyncEmptyState(dataLabel = "المجموعات المشتركة", syncState = groupsSyncState)
                    }
                } else if (sharedGroups.isEmpty()) {
                    item {
                        EmptyStateGlass(
                            title = if (groupSearchQuery.isBlank()) "لا توجد مجموعات مشتركة بعد" else "لا توجد مجموعات تطابق البحث",
                            subtitle = if (groupSearchQuery.isBlank()) "يمكنك استكشاف المجموعات العامة والأندية المدرسية من تبويب 'اكتشف المجموعات'." else "جرّب كلمة أخرى أو امسح البحث لعرض جميع مجموعاتك.",
                            icon = "👥"
                        )
                    }
                } else {
                    items(sharedGroups, key = { it.id }) { group ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = if (group.visibility == "PRIVATE") WarmAmber.copy(alpha = 0.45f) else GlassBorderSubtle,
                            backgroundColor = GlassSurface
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(CyanGlow),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = group.icon ?: "👥", fontSize = tanweerTextSize(18f))
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = group.name,
                                                fontSize = tanweerTextSize(14f),
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "${group.memberCount} عضو • ${if (group.visibility == "PRIVATE") "خاصة بموافقة" else "عامة"}",
                                                fontSize = tanweerTextSize(11f),
                                                color = TextSecondary
                                            )
                                            Text(
                                                text = if (group.chatPolicy == "STAFF_ONLY") "🔒 الرسائل للهيئة التعليمية والإدارة فقط" else "💬 النقاش متاح للأعضاء",
                                                fontSize = tanweerTextSize(10f),
                                                color = TextMuted
                                            )
                                        }
                                    }
                                    GlassPill(
                                        text = "عضوية نشطة ✓",
                                        color = EmeraldGreen,
                                        bgColor = EmeraldGreen.copy(alpha = 0.14f)
                                    )
                                }

                                if (!group.description.isNullOrBlank()) {
                                    Text(
                                        text = group.description,
                                        fontSize = tanweerTextSize(12f),
                                        color = TextMuted,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    GlassButton(
                                        text = "فتح النقاش 💬",
                                        icon = Icons.Default.Chat,
                                        onClick = { activeChatGroup = group },
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (canManageGroupMembers(group)) {
                                        GlassOutlinedButton(
                                            text = "إدارة الأعضاء",
                                            onClick = {
                                                managingGroup = group
                                                viewModel.loadGroupMembers(group.id)
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            viewModel.leaveGroup(group.id) { success, message ->
                                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                                if (success) {
                                                    if (activeChatGroup?.id == group.id) activeChatGroup = null
                                                    if (selectedGroupId == group.id) activeTab = GroupsTab.MY_GROUPS
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "مغادرة المجموعة",
                                            fontSize = tanweerTextSize(10f),
                                            color = RubyRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Tab 2: DISCOVER PUBLIC GROUPS
                if (canManageMembers) {
                    item {
                        GlassButton(
                            text = "+ إنشاء مجموعة عامة أو خاصة 👥",
                            icon = Icons.Default.Add,
                            onClick = { showCreateGroupDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (visibleDiscoverGroups.isEmpty() && !hasAuthoritativeSnapshot(groupsSyncState)) {
                    item {
                        PendingSyncEmptyState(dataLabel = "المجموعات المتاحة للاكتشاف", syncState = groupsSyncState)
                    }
                } else if (visibleDiscoverGroups.isEmpty()) {
                    item {
                        EmptyStateGlass(
                            title = if (groupSearchQuery.isBlank()) "لا توجد مجموعات جديدة" else "لا توجد نتائج مطابقة",
                            subtitle = if (groupSearchQuery.isBlank()) "أنت منضم بالفعل إلى كل المجموعات المتاحة لك حالياً." else "غيّر عبارة البحث أو امسحها لاستعراض المجموعات المتاحة.",
                            icon = "🔍"
                        )
                    }
                } else {
                    items(visibleDiscoverGroups, key = { it.id }) { group ->
                        val hasPendingRequest = group.membershipStatus == "PENDING"
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = MidnightSurface,
                            borderColor = GlassBorderSubtle
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(CyanGlow),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = group.icon ?: "🌐", fontSize = tanweerTextSize(20f))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = group.name,
                                                fontSize = tanweerTextSize(15f),
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            val groupTypeLabel = when (group.visibility) {
                                                "PRIVATE" -> "مجموعة خاصة • لا تنضم إلا بعد مراجعة طلبك"
                                                else -> if (group.chatPolicy == "STAFF_ONLY") "مجموعة الهيئة التعليمية • قراءة للطلاب" else "مجموعة عامة • دخول مباشر"
                                            }
                                            Text(
                                                text = "${group.memberCount} عضو • $groupTypeLabel",
                                                fontSize = tanweerTextSize(12f),
                                                color = CyanAccent
                                            )
                                            Text(
                                                text = if (group.chatPolicy == "STAFF_ONLY") "🔒 الرسائل للمعلمين والإدارة فقط" else "💬 النقاش متاح للأعضاء",
                                                fontSize = tanweerTextSize(10f),
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    GlassPill(
                                        text = when {
                                            hasPendingRequest -> "بانتظار الموافقة ⏳"
                                            group.visibility == "PRIVATE" -> "خاصة 🔐"
                                            else -> "عامة 🌐"
                                        },
                                        color = if (hasPendingRequest || group.visibility == "PRIVATE") WarmAmber else EmeraldGreen,
                                        bgColor = if (hasPendingRequest || group.visibility == "PRIVATE") AmberGlow else EmeraldGreen.copy(alpha = 0.15f)
                                    )
                                }

                                if (!group.description.isNullOrBlank()) {
                                    Text(
                                        text = group.description,
                                        fontSize = tanweerTextSize(12f),
                                        color = TextSecondary,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                val requiresApproval = group.visibility == "PRIVATE" || group.type == "CLASS"
                                val rejectedRequest = group.membershipStatus == "REJECTED"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    GlassButton(
                                        text = when {
                                            hasPendingRequest -> "طلبك قيد المراجعة"
                                            rejectedRequest -> "إعادة طلب الانضمام"
                                            requiresApproval -> "طلب الانضمام إلى المجموعة 🔐"
                                            else -> "انضمام مباشر ➕"
                                        },
                                        icon = Icons.Default.GroupAdd,
                                        enabled = !hasPendingRequest,
                                        onClick = {
                                            if (requiresApproval) {
                                                joinRequestGroup = group
                                                joinReasonText = ""
                                            } else {
                                                viewModel.joinGroup(
                                                    groupId = group.id,
                                                    onResult = { isSuccess, status, message ->
                                                        if (isSuccess) {
                                                            if (status == "ACTIVE") {
                                                                Toast.makeText(context, "تم الانضمام إلى ${group.name} بنجاح! 🎉", Toast.LENGTH_SHORT).show()
                                                                activeTab = GroupsTab.MY_GROUPS
                                                            } else {
                                                                Toast.makeText(context, "تم إرسال طلب الانضمام إلى ${group.name}، بانتظار موافقة المشرفين ⏳", Toast.LENGTH_LONG).show()
                                                            }
                                                        } else {
                                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                )
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (canManageGroupMembers(group)) {
                                        GlassOutlinedButton(
                                            text = "إدارة الطلبات",
                                            onClick = {
                                                managingGroup = group
                                                viewModel.loadGroupMembers(group.id)
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Join Reason Dialog for CLASS groups
        joinRequestGroup?.let { targetGroup ->
            AlertDialog(
                onDismissRequest = { if (!isSubmittingJoin) joinRequestGroup = null },
                title = {
                    Text("طلب الانضمام إلى ${targetGroup.name}", color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = tanweerTextSize(16f))
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "هذه مجموعة خاصة أو شعبة تتطلب موافقة. اكتب سبب الانضمام ليتمكن مشرفو وأساتذة المجموعة من مراجعة طلبك:",
                            fontSize = tanweerTextSize(12f),
                            color = TextSecondary
                        )
                        OutlinedTextField(
                            value = joinReasonText,
                            onValueChange = { value ->
                                if (value.length <= 500) joinReasonText = value
                            },
                            label = { Text("سبب طلب الانضمام *", fontSize = tanweerTextSize(11f)) },
                            supportingText = {
                                Text(
                                    text = "${joinReasonText.length}/500",
                                    fontSize = tanweerTextSize(9f),
                                    color = if (joinReasonText.length >= 500) WarmAmber else TextMuted
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                unfocusedBorderColor = GlassBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            maxLines = 3
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (joinReasonText.isBlank()) {
                                Toast.makeText(context, "يرجى كتابة سبب الانضمام", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSubmittingJoin = true
                            viewModel.joinGroup(
                                groupId = targetGroup.id,
                                reason = joinReasonText.trim(),
                                onResult = { isSuccess, status, message ->
                                    isSubmittingJoin = false
                                    if (isSuccess) {
                                        joinRequestGroup = null
                                        if (status == "ACTIVE") {
                                            Toast.makeText(context, "تم الانضمام بنجاح! 🎉", Toast.LENGTH_SHORT).show()
                                            activeTab = GroupsTab.MY_GROUPS
                                        } else {
                                            Toast.makeText(context, "تم إرسال طلب الانضمام ومبرر الطلب بنجاح، بانتظار موافقة مشرف الشعبة ⏳", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        },
                        enabled = !isSubmittingJoin &&
                            joinReasonText.isNotBlank() &&
                            joinReasonText.trim().isNotEmpty() &&
                            joinReasonText.length <= 500,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                    ) {
                        if (isSubmittingJoin) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TextOnAccent, strokeWidth = 2.dp)
                        } else {
                            Text("إرسال الطلب 📤", color = TextOnAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { joinRequestGroup = null },
                        enabled = !isSubmittingJoin
                    ) {
                        Text("إلغاء", color = TextSecondary)
                    }
                },
                containerColor = MidnightSurface,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Create Public Group Dialog
        if (showCreateGroupDialog) {
            var newGroupName by remember { mutableStateOf("") }
            var newGroupDesc by remember { mutableStateOf("") }
            var newGroupIcon by remember { mutableStateOf("👥") }
            var newGroupVisibility by remember { mutableStateOf("PUBLIC") }
            var newGroupChatPolicy by remember { mutableStateOf("MEMBERS") }
            var isCreating by remember { mutableStateOf(false) }
            val iconsList = listOf("👥", "🔬", "📖", "🎨", "⚽", "💻", "💡", "🌐")

            AlertDialog(
                onDismissRequest = { if (!isCreating) showCreateGroupDialog = false },
                title = {
                    Text("إنشاء مجموعة جديدة 👥", color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = tanweerTextSize(16f))
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("اختر أيقونة للمجموعة:", fontSize = tanweerTextSize(12f), color = TextSecondary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            iconsList.forEach { ic ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (newGroupIcon == ic) CyanGlow else MidnightBackground)
                                        .border(1.dp, if (newGroupIcon == ic) CyanAccent else GlassBorderSubtle, RoundedCornerShape(8.dp))
                                        .clickable { newGroupIcon = ic },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(ic, fontSize = tanweerTextSize(18f))
                                }
                            }
                        }

                        OutlinedTextField(
                            value = newGroupName,
                            onValueChange = { newGroupName = it },
                            label = { Text("اسم المجموعة *", fontSize = tanweerTextSize(11f)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                unfocusedBorderColor = GlassBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            singleLine = true
                        )

                        Text("نوع المجموعة:", fontSize = tanweerTextSize(12f), color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf(
                                "PUBLIC" to "عامة • دخول مباشر",
                                "PRIVATE" to "خاصة • طلب موافقة"
                            ).forEach { (value, label) ->
                                Row(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                        .background(if (newGroupVisibility == value) CyanGlow else MidnightBackground)
                                        .border(1.dp, if (newGroupVisibility == value) CyanAccent else GlassBorderSubtle, RoundedCornerShape(10.dp))
                                        .clickable { newGroupVisibility = value }.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = newGroupVisibility == value,
                                        onClick = { newGroupVisibility = value },
                                        colors = RadioButtonDefaults.colors(selectedColor = CyanAccent)
                                    )
                                    Text(label, fontSize = tanweerTextSize(10f), color = TextPrimary)
                                }
                            }
                        }

                        Text("من يمكنه المشاركة في النقاش؟", fontSize = tanweerTextSize(12f), color = TextSecondary)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "MEMBERS" to "جميع الأعضاء يمكنهم إرسال الرسائل",
                                "STAFF_ONLY" to "المعلمون والإدارة يرسلون، والطلاب يقرؤون فقط"
                            ).forEach { (value, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (newGroupChatPolicy == value) CyanGlow else MidnightBackground)
                                        .border(
                                            1.dp,
                                            if (newGroupChatPolicy == value) CyanAccent else GlassBorderSubtle,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { newGroupChatPolicy = value }
                                        .padding(horizontal = 9.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = newGroupChatPolicy == value,
                                        onClick = { newGroupChatPolicy = value },
                                        colors = RadioButtonDefaults.colors(selectedColor = CyanAccent)
                                    )
                                    Text(
                                        text = label,
                                        fontSize = tanweerTextSize(11f),
                                        color = TextPrimary
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = newGroupDesc,
                            onValueChange = { newGroupDesc = it },
                            label = { Text("وصف المجموعة وأهدافها", fontSize = tanweerTextSize(11f)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                unfocusedBorderColor = GlassBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            maxLines = 3
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newGroupName.isBlank()) {
                                Toast.makeText(context, "يرجى كتابة اسم المجموعة", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isCreating = true
                            viewModel.createPublicGroup(
                                name = newGroupName.trim(),
                                description = newGroupDesc.trim().ifEmpty { null },
                                icon = newGroupIcon,
                                visibility = newGroupVisibility,
                                chatPolicy = newGroupChatPolicy
                            ) { success, msg ->
                                isCreating = false
                                if (success) {
                                    showCreateGroupDialog = false
                                    activeTab = GroupsTab.MY_GROUPS
                                    groupSearchQuery = ""
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isCreating,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TextOnAccent, strokeWidth = 2.dp)
                        } else {
                            Text(
                                if (newGroupVisibility == "PRIVATE") "إنشاء المجموعة الخاصة 🔒" else "إنشاء المجموعة العامة ✅",
                                color = TextOnAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showCreateGroupDialog = false },
                        enabled = !isCreating
                    ) {
                        Text("إلغاء", color = TextSecondary)
                    }
                },
                containerColor = MidnightSurface,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Group Members Management Dialog
        managingGroup?.let { grp ->
            GroupMembersManagementDialog(
                group = grp,
                membersResponse = groupMembersState,
                isLoading = isGroupMembersLoading,
                errorMessage = groupMembersError,
                onRetry = { viewModel.loadGroupMembers(grp.id) },
                onDismiss = { managingGroup = null },
                onUpdateRole = { userId, newRole ->
                    viewModel.updateGroupMemberRole(userId, newRole, grp.id) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                onRemove = { userId, isBan ->
                    viewModel.removeGroupMember(userId, isBan, grp.id) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                onApproveJoin = { userId ->
                    viewModel.approveGroupJoinRequest(userId, grp.id) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                onRejectJoin = { userId ->
                    viewModel.rejectGroupJoinRequest(userId, grp.id) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

// -------------------------------------------------------------
// GROUP MEMBERS MANAGEMENT DIALOG
// -------------------------------------------------------------
@Composable
fun GroupMembersManagementDialog(
    group: GroupItem,
    membersResponse: com.magd.tanweer.data.model.GroupMembersResponse?,
    isLoading: Boolean = membersResponse == null,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    onDismiss: () -> Unit,
    onUpdateRole: (userId: String, newRole: String) -> Unit,
    onRemove: (userId: String, isBan: Boolean) -> Unit,
    onApproveJoin: (userId: String) -> Unit,
    onRejectJoin: (userId: String) -> Unit = {}
) {
    val members = membersResponse?.members.orEmpty()
    val canManage = membersResponse?.canManageMembers == true
    val pendingMembers = members.filter { it.status == "PENDING" }
    val activeMembers = members.filter { it.status == "ACTIVE" }
    val bannedMembers = members.filter { it.status == "BANNED" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(group.icon ?: "👥", fontSize = tanweerTextSize(22f))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "إدارة أعضاء ${group.name}",
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = tanweerTextSize(17f)
                    )
                    Text(
                        text = "${activeMembers.size} عضو معتمد • ${pendingMembers.size} طلب معلق",
                        color = TextSecondary,
                        fontSize = tanweerTextSize(11f)
                    )
                }
            }
        },
        text = {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = CyanAccent,
                            strokeWidth = 2.dp
                        )
                    }
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Text(errorMessage, color = RubyRed, fontSize = tanweerTextSize(12f))
                        GlassButton(text = "إعادة المحاولة", onClick = onRetry)
                    }
                }
                membersResponse == null -> {
                    Text(
                        "لم تُحمّل قائمة الأعضاء بعد.",
                        color = TextSecondary,
                        fontSize = tanweerTextSize(12f),
                        modifier = Modifier.padding(18.dp)
                    )
                }
                members.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "لا يوجد أعضاء أو طلبات انضمام حالياً.",
                            fontSize = tanweerTextSize(12f),
                            color = TextSecondary
                        )
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (members.size > 3) {
                            Text(
                                text = stringResource(R.string.group_members_scroll_hint),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("group_members_scroll_hint"),
                                color = TextMuted,
                                fontSize = tanweerTextSize(10f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 430.dp)
                                .testTag("group_members_list"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (canManage && pendingMembers.isNotEmpty()) {
                            item(key = "pending-requests-heading") {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "طلبات الانضمام المعلقة",
                                            color = WarmAmber,
                                            fontSize = tanweerTextSize(13f),
                                            fontWeight = FontWeight.Bold
                                        )
                                        GlassPill(
                                            text = "${pendingMembers.size} طلب",
                                            color = WarmAmber,
                                            bgColor = AmberGlow
                                        )
                                    }
                                    Text(
                                        "راجع سبب كل طلب ثم وافق أو ارفض. سيصل إشعار للطالب بالنتيجة.",
                                        color = TextSecondary,
                                        fontSize = tanweerTextSize(10f)
                                    )
                                }
                            }
                            items(pendingMembers, key = { "pending-${it.userId}" }) { member ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(AmberGlow.copy(alpha = 0.42f))
                                        .border(1.dp, WarmAmber.copy(alpha = 0.55f), RoundedCornerShape(11.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                member.fullName,
                                                fontSize = tanweerTextSize(13f),
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            member.phoneNumber?.let {
                                                Text(it, fontSize = tanweerTextSize(10f), color = TextMuted)
                                            }
                                            Text(
                                                "الصف ${member.gradeId} • الشعبة ${member.sectionId}",
                                                fontSize = tanweerTextSize(10f),
                                                color = TextSecondary
                                            )
                                        }
                                        GlassPill(
                                            text = "بانتظارك ⏳",
                                            color = WarmAmber,
                                            bgColor = AmberGlow
                                        )
                                    }
                                    if (!member.requestReason.isNullOrBlank()) {
                                        Text(
                                            text = "سبب الطلب: ${member.requestReason}",
                                            fontSize = tanweerTextSize(11f),
                                            color = WarmAmber,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(MidnightBackground.copy(alpha = 0.6f))
                                                .padding(8.dp)
                                        )
                                    } else {
                                        Text(
                                            "لم يضف الطالب سبباً للانضمام.",
                                            fontSize = tanweerTextSize(10f),
                                            color = TextMuted
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { onApproveJoin(member.userId) },
                                            modifier = Modifier.weight(1f).height(34.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                                        ) {
                                            Text("قبول الطلب ✓", fontSize = tanweerTextSize(11f), color = Color.White)
                                        }
                                        OutlinedButton(
                                            onClick = { onRejectJoin(member.userId) },
                                            modifier = Modifier.weight(1f).height(34.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, RubyRed.copy(alpha = 0.7f))
                                        ) {
                                            Text("رفض الطلب", fontSize = tanweerTextSize(11f), color = RubyRed)
                                        }
                                    }
                                }
                            }
                        }

                        if (activeMembers.isNotEmpty()) {
                            item(key = "active-members-heading") {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    HorizontalDivider(color = GlassBorderSubtle, thickness = 0.7.dp)
                                    Text(
                                        "الأعضاء المعتمدون (${activeMembers.size})",
                                        color = CyanAccent,
                                        fontSize = tanweerTextSize(13f),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 5.dp)
                                    )
                                }
                            }
                            items(activeMembers, key = { "active-${it.userId}" }) { member ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MidnightBackground)
                                        .border(1.dp, GlassBorderSubtle, RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                member.fullName,
                                                fontSize = tanweerTextSize(13f),
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            member.phoneNumber?.let {
                                                Text(it, fontSize = tanweerTextSize(10f), color = TextMuted)
                                            }
                                        }
                                        val roleLabel = when (member.memberRole) {
                                            "ADMIN" -> "👑 مدير"
                                            "TEACHER" -> "🎓 أستاذ"
                                            "MODERATOR" -> "🛡️ مشرف"
                                            else -> "👤 طالب"
                                        }
                                        GlassPill(
                                            text = roleLabel,
                                            color = CyanAccent,
                                            bgColor = CyanGlow
                                        )
                                    }
                                    if (canManage) {
                                        HorizontalDivider(color = GlassBorderSubtle, thickness = 0.5.dp)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("group_member_actions_${member.userId}"),
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            if (member.memberRole == "STUDENT") {
                                                OutlinedButton(
                                                    onClick = { onUpdateRole(member.userId, "MODERATOR") },
                                                    modifier = Modifier.weight(1f).height(30.dp),
                                                    contentPadding = PaddingValues(horizontal = 3.dp),
                                                    shape = RoundedCornerShape(7.dp)
                                                ) {
                                                    Text("ترقية لمشرف", fontSize = tanweerTextSize(9f), color = CyanAccent)
                                                }
                                            } else if (member.memberRole == "MODERATOR") {
                                                OutlinedButton(
                                                    onClick = { onUpdateRole(member.userId, "STUDENT") },
                                                    modifier = Modifier.weight(1f).height(30.dp),
                                                    contentPadding = PaddingValues(horizontal = 3.dp),
                                                    shape = RoundedCornerShape(7.dp)
                                                ) {
                                                    Text("خفض لطالب", fontSize = tanweerTextSize(9f), color = TextSecondary)
                                                }
                                            }
                                            OutlinedButton(
                                                onClick = { onRemove(member.userId, false) },
                                                modifier = Modifier.weight(0.75f).height(30.dp),
                                                contentPadding = PaddingValues(horizontal = 3.dp),
                                                shape = RoundedCornerShape(7.dp)
                                            ) {
                                                Text("إزالة", fontSize = tanweerTextSize(9f), color = RubyRed)
                                            }
                                            OutlinedButton(
                                                onClick = { onRemove(member.userId, true) },
                                                modifier = Modifier.weight(0.75f).height(30.dp),
                                                contentPadding = PaddingValues(horizontal = 3.dp),
                                                shape = RoundedCornerShape(7.dp)
                                            ) {
                                                Text("حظر", fontSize = tanweerTextSize(9f), color = RubyRed)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (bannedMembers.isNotEmpty()) {
                            item(key = "banned-members-heading") {
                                Text(
                                    "الحسابات المحظورة (${bannedMembers.size})",
                                    color = RubyRed,
                                    fontSize = tanweerTextSize(12f),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 7.dp)
                                )
                            }
                            items(bannedMembers, key = { "banned-${it.userId}" }) { member ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(RubyRed.copy(alpha = 0.08f))
                                        .border(1.dp, RubyRed.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(member.fullName, color = TextPrimary, fontSize = tanweerTextSize(12f), fontWeight = FontWeight.Bold)
                                        member.phoneNumber?.let { Text(it, color = TextMuted, fontSize = tanweerTextSize(10f)) }
                                    }
                                    GlassPill(text = "محظور 🚫", color = RubyRed, bgColor = RubyRed.copy(alpha = 0.12f))
                                }
                            }
                        }
                    }
                    }
                }
            }
        },
        confirmButton = {
            GlassButton(text = "إغلاق", onClick = onDismiss)
        },
        containerColor = MidnightSurface,
        shape = RoundedCornerShape(18.dp)
    )
}
