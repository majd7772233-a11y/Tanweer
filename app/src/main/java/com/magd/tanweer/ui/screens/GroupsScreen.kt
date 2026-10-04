package com.magd.tanweer.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.ChatMessageItem
import com.magd.tanweer.data.model.GroupItem
import com.magd.tanweer.data.remote.ConnectionStatus
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*

enum class GroupsTab(val label: String) {
    MY_GROUPS("مجموعاتي والشعبة"),
    DISCOVER("اكتشف المجموعات 🔍")
}

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
    val groupMembersState by viewModel.groupMembersState.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(GroupsTab.MY_GROUPS) }
    var activeChatGroup by remember { mutableStateOf<GroupItem?>(null) }
    var managingGroup by remember { mutableStateOf<GroupItem?>(null) }
    val chatMessages by viewModel.repository.getChatMessages(activeChatGroup?.id ?: "")
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var chatInputText by remember { mutableStateOf("") }
    var isSendingChat by remember { mutableStateOf(false) }

    LaunchedEffect(activeChatGroup?.id) {
        val g = activeChatGroup
        if (g != null) {
            viewModel.connectGroupChat(g.id)
        }
    }

    if (activeChatGroup != null) {
        BackHandler { activeChatGroup = null }

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
                            IconButton(onClick = { activeChatGroup = null }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "عودة",
                                    tint = CyanAccent
                                )
                            }
                            Text(text = activeChatGroup?.icon ?: "💬", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = activeChatGroup?.name ?: "نقاش المجموعة",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "نقاشات مباشرة ولن تضيع الدروس والواجبات",
                                    fontSize = 11.sp,
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

                // Chat Messages List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (chatMessages.isEmpty()) {
                        item {
                            EmptyStateGlass(
                                title = "بدء النقاش المباشر في الشعبة",
                                subtitle = "الرسائل هنا متزامنة لحظياً مع الخادم ومحفوظة أوفلاين في جهازك.",
                                icon = "💬"
                            )
                        }
                    } else {
                        items(chatMessages, key = { it.id }) { msg ->
                            val isMe = msg.senderId == currentUser?.id || msg.isMe
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
                                        .background(if (isMe) CyanGlow else MidnightSurface)
                                        .border(
                                            1.dp,
                                            if (isMe) CyanAccent.copy(alpha = 0.4f) else GlassBorderSubtle,
                                            RoundedCornerShape(16.dp)
                                        )
                                        .padding(10.dp)
                                ) {
                                    if (!isMe) {
                                        Text(
                                            text = "${msg.senderName} (${msg.senderGradeSection})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WarmAmber
                                        )
                                    }

                                    Text(
                                        text = msg.text,
                                        fontSize = 13.sp,
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
                                                    Text("⏳ جاري الإرسال...", fontSize = 9.sp, color = TextMuted)
                                                }
                                                "SENT" -> {
                                                    Text("✓✓ تم الإرسال", fontSize = 9.sp, color = CyanAccent)
                                                }
                                                "FAILED" -> {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.clickable {
                                                            viewModel.retryChatMessage(msg)
                                                            Toast.makeText(context, "إعادة إرسال الرسالة...", Toast.LENGTH_SHORT).show()
                                                        }
                                                    ) {
                                                        Text("⚠️ فشل الإرسال (اضغط للإعادة)", fontSize = 9.sp, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Chat Input Field
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
                        placeholder = "اكتب رسالة نقاش للشعبة...",
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val text = chatInputText.trim()
                            val gId = activeChatGroup?.id
                            if (text.isNotBlank() && gId != null && !isSendingChat) {
                                isSendingChat = true
                                chatInputText = ""
                                viewModel.sendChatMessage(text, targetGroupId = gId)
                                isSendingChat = false
                            }
                        },
                        enabled = !isSendingChat && chatInputText.isNotBlank(),
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (chatInputText.isNotBlank()) CyanAccent else CyanAccent.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "إرسال", tint = TextOnAccent)
                    }
                }
            }
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
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = CyanAccent
                )
                Text(
                    text = "شعبتك الرسمية المعتمدة والمجموعات الدراسية المشتركة",
                    fontSize = 12.sp,
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
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TextOnAccent else TextSecondary
                            )
                        }
                    }
                }
            }

            if (activeTab == GroupsTab.MY_GROUPS) {
                // Tab 1: MY GROUPS
                val officialClassGroup = myGroups.find { it.type == "CLASS" || it.id.startsWith("class_") || it.role == "OFFICIAL" }
                    ?: myGroups.firstOrNull()
                val sharedGroups = myGroups.filter { it.id != officialClassGroup?.id }

                // 1. Official Section Group (الشعبة الرسمية الأساسية) - Distinguished Hero Card
                if (officialClassGroup != null) {
                    item {
                        val isSelected = officialClassGroup.id == selectedGroupId || (selectedGroupId.isEmpty() && officialClassGroup.id == currentUser?.defaultGroupId)

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Text("👑", fontSize = 16.sp)
                                Text(
                                    text = "الشعبة الرسمية الأساسية المعتمدة",
                                    fontSize = 13.sp,
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
                                                Text(text = officialClassGroup.icon ?: "🏫", fontSize = 24.sp)
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = officialClassGroup.name,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = "${officialClassGroup.memberCount} طالب وطالبة • الصف والشعبة الرسمية",
                                                    fontSize = 12.sp,
                                                    color = WarmAmber
                                                )
                                            }
                                        }

                                        if (isSelected) {
                                            GlassPill(text = "الشعبة النشطة ✓", color = EmeraldGreen, bgColor = EmeraldGreen.copy(alpha = 0.15f))
                                        }
                                    }

                                    Text(
                                        text = officialClassGroup.description ?: "الشعبة الرسمية المرتبطة بمدرستك والتي يُنشر فيها الجدول والواجبات والاختبارات المعتمدة.",
                                        fontSize = 12.sp,
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
                                        GlassOutlinedButton(
                                            text = "إدارة الأعضاء 👥",
                                            onClick = {
                                                managingGroup = officialClassGroup
                                                viewModel.loadGroupMembers(officialClassGroup.id)
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (!isSelected) {
                                            GlassOutlinedButton(
                                                text = "التبديل كشعبة نشطة",
                                                onClick = { viewModel.setSelectedGroup(officialClassGroup.id) },
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                if (sharedGroups.isEmpty()) {
                    item {
                        EmptyStateGlass(
                            title = "لا توجد مجموعات مشتركة بعد",
                            subtitle = "يمكنك استكشاف المجموعات العامة والأندية المدرسية من تبويب 'اكتشف المجموعات'.",
                            icon = "👥"
                        )
                    }
                } else {
                    items(sharedGroups, key = { it.id }) { group ->
                        val isSelected = group.id == selectedGroupId
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = if (isSelected) CyanAccent else GlassBorder,
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
                                            Text(text = group.icon ?: "👥", fontSize = 18.sp)
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = group.name,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "${group.memberCount} طالب • مجموعة مشتركة",
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        GlassPill(text = "نشطة", color = CyanAccent, bgColor = CyanGlow)
                                    }
                                }

                                if (!group.description.isNullOrBlank()) {
                                    Text(
                                        text = group.description,
                                        fontSize = 12.sp,
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
                                    if (!isSelected) {
                                        GlassOutlinedButton(
                                            text = "تفعيل المجموعة",
                                            onClick = { viewModel.setSelectedGroup(group.id) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Tab 2: DISCOVER PUBLIC GROUPS
                if (discoverGroups.isEmpty()) {
                    item {
                        EmptyStateGlass(
                            title = "لا توجد مجموعات عامة جديدة",
                            subtitle = "أنت منضم بالفعل إلى كافة المجموعات المتاحة لمدرستك وشعبتك!",
                            icon = "🔍"
                        )
                    }
                } else {
                    items(discoverGroups, key = { it.id }) { group ->
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
                                            Text(text = group.icon ?: "🌐", fontSize = 20.sp)
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = group.name,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "${group.memberCount} عضو • ${when (group.type) { "SHARED" -> "شعبة مشتركة"; else -> "نادي مدرسي عام" }}",
                                                fontSize = 12.sp,
                                                color = CyanAccent
                                            )
                                        }
                                    }

                                    GlassPill(
                                        text = "عامة للجميع",
                                        color = EmeraldGreen,
                                        bgColor = EmeraldGreen.copy(alpha = 0.15f)
                                    )
                                }

                                if (!group.description.isNullOrBlank()) {
                                    Text(
                                        text = group.description,
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                GlassButton(
                                    text = "طلب الانضمام للمجموعة ➕",
                                    icon = Icons.Default.GroupAdd,
                                    onClick = {
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
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        // Group Members Management Dialog
        managingGroup?.let { grp ->
            GroupMembersManagementDialog(
                group = grp,
                membersResponse = groupMembersState,
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
    onDismiss: () -> Unit,
    onUpdateRole: (userId: String, newRole: String) -> Unit,
    onRemove: (userId: String, isBan: Boolean) -> Unit,
    onApproveJoin: (userId: String) -> Unit
) {
    val members = membersResponse?.members ?: emptyList()
    val canManage = membersResponse?.canManageMembers == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(group.icon ?: "👥", fontSize = 22.sp)
                Column {
                    Text(
                        text = "إدارة أعضاء ${group.name}",
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "${members.size} عضو مسجل في المجموعة",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            if (members.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("جاري جلب قائمة الأعضاء...", fontSize = 12.sp, color = TextSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(members, key = { it.userId }) { m ->
                        val isPending = m.status == "PENDING"
                        val isBanned = m.status == "BANNED"

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MidnightBackground)
                                .border(1.dp, if (isPending) WarmAmber else GlassBorderSubtle, RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = m.fullName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        m.phoneNumber?.let {
                                            Text(text = it, fontSize = 10.sp, color = TextMuted)
                                        }
                                    }

                                    val roleLabel = when (m.memberRole) {
                                        "ADMIN" -> "👑 مدير"
                                        "TEACHER" -> "🎓 أستاذ"
                                        "MODERATOR" -> "🛡️ مشرف"
                                        else -> "👤 طالب"
                                    }
                                    GlassPill(
                                        text = if (isPending) "طلب انضمام ⏳" else if (isBanned) "محظور 🚫" else roleLabel,
                                        color = if (isPending) WarmAmber else if (isBanned) RubyRed else CyanAccent,
                                        bgColor = if (isPending) AmberGlow else CyanGlow
                                    )
                                }

                                if (canManage) {
                                    HorizontalDivider(color = GlassBorderSubtle, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isPending) {
                                            Button(
                                                onClick = { onApproveJoin(m.userId) },
                                                modifier = Modifier.weight(1f).height(28.dp),
                                                contentPadding = PaddingValues(0.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                                            ) {
                                                Text("قبول الاعتماد ✅", fontSize = 10.sp, color = Color.White)
                                            }
                                        } else {
                                            if (m.memberRole == "STUDENT") {
                                                OutlinedButton(
                                                    onClick = { onUpdateRole(m.userId, "MODERATOR") },
                                                    modifier = Modifier.weight(1f).height(28.dp),
                                                    contentPadding = PaddingValues(0.dp),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text("ترقية لمشرف 🛡️", fontSize = 9.sp, color = CyanAccent)
                                                }
                                            } else if (m.memberRole == "MODERATOR") {
                                                OutlinedButton(
                                                    onClick = { onUpdateRole(m.userId, "STUDENT") },
                                                    modifier = Modifier.weight(1f).height(28.dp),
                                                    contentPadding = PaddingValues(0.dp),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text("خفض لطالب 👤", fontSize = 9.sp, color = TextSecondary)
                                                }
                                            }

                                            OutlinedButton(
                                                onClick = { onRemove(m.userId, false) },
                                                modifier = Modifier.weight(0.7f).height(28.dp),
                                                contentPadding = PaddingValues(0.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text("إزالة ✕", fontSize = 9.sp, color = RubyRed)
                                            }

                                            if (!isBanned) {
                                                OutlinedButton(
                                                    onClick = { onRemove(m.userId, true) },
                                                    modifier = Modifier.weight(0.7f).height(28.dp),
                                                    contentPadding = PaddingValues(0.dp),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text("حظر 🚫", fontSize = 9.sp, color = RubyRed)
                                                }
                                            }
                                        }
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
