package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChatMessageItem
import com.example.data.model.GroupItem
import com.example.data.remote.ConnectionStatus
import com.example.ui.TanweerViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun GroupsScreen(
    viewModel: TanweerViewModel
) {
    val groups by viewModel.repository.getGroups().collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val chatStatus by viewModel.chatConnectionStatus.collectAsStateWithLifecycle()

    var activeChatGroup by remember { mutableStateOf<GroupItem?>(null) }
    val chatMessages by viewModel.repository.getChatMessages(activeChatGroup?.id ?: "")
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var chatInputText by remember { mutableStateOf("") }

    LaunchedEffect(activeChatGroup?.id) {
        val g = activeChatGroup
        if (g != null) {
            viewModel.connectGroupChat(g.id)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
    ) {
        if (activeChatGroup != null) {
            // Group Chat View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurface
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = activeChatGroup?.icon ?: "💬", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = activeChatGroup?.name ?: "النقاش والمحادثة",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "الرسائل هنا للنقاش فقط ولن تضيع الدروس والواجبات",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
                                    text = "غير متصل 🔴",
                                    color = RubyRed,
                                    bgColor = Color(0x22FF3366)
                                )
                            }

                            GlassOutlinedButton(
                                text = "عودة",
                                onClick = { activeChatGroup = null },
                                modifier = Modifier.height(34.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (chatMessages.isEmpty()) {
                        item {
                            EmptyStateGlass(
                                title = "بدء النقاش المباشر في الشعبة",
                                subtitle = "المحادثة متصلة مباشرة بالخادم عبر بروتوكول لحظي للنقاشات المدرسية السريعة.",
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
                                        .fillMaxWidth(0.8f)
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
                                }
                            }
                        }
                    }
                }

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
                        placeholder = "اكتب رسالة نقاش...",
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (chatInputText.isNotBlank()) {
                                viewModel.sendChatMessage(chatInputText.trim())
                                chatInputText = ""
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(CyanAccent)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "إرسال", tint = TextOnAccent)
                    }
                }
            }
        } else {
            // Groups List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = "👥 المجموعات والشعب الدراسية",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = CyanAccent
                    )
                    Text(
                        text = "المجموعات الحقيقية المسجلة لشعبتك في تنوير",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                if (groups.isEmpty()) {
                    item {
                        EmptyStateGlass(
                            title = "لا توجد مجموعات بعد",
                            subtitle = "المجموعات في تنوير تنشأ حقيقيًا وترتبط بشعبتك الرسمية فقط دون أي قوالب وهمية.",
                            icon = "🏫"
                        )
                    }
                } else {
                    items(groups, key = { it.id }) { group ->
                        val isSelected = group.id == selectedGroupId || (selectedGroupId.isEmpty() && group.id == currentUser?.defaultGroupId)
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
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(CyanGlow),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = group.icon ?: "👥", fontSize = 20.sp)
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
                                                text = "${group.memberCount} طالب • ${when(group.type) { "CLASS" -> "مجموعة الشعبة الأساسية"; "SHARED" -> "مجموعة مشتركة"; else -> "نادي مدرسي" }}",
                                                fontSize = 12.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        GlassPill(text = "المجموعة النشطة", color = CyanAccent, bgColor = CyanGlow)
                                    }
                                }

                                if (!group.description.isNullOrBlank()) {
                                    Text(
                                        text = group.description,
                                        fontSize = 12.sp,
                                        color = TextMuted,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    GlassButton(
                                        text = "فتح نقاش المجموعة 💬",
                                        icon = Icons.Default.Chat,
                                        onClick = { activeChatGroup = group },
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (!isSelected) {
                                        GlassOutlinedButton(
                                            text = "التبديل إليها",
                                            onClick = { viewModel.setSelectedGroup(group.id) },
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
    }
}
