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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
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
import com.example.data.model.DefaultSubjects
import com.example.data.model.IssueItem
import com.example.ui.TanweerViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun IssuesScreen(
    viewModel: TanweerViewModel
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId ?: "" }

    val issues by viewModel.repository.getIssues(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var isAddModalOpen by remember { mutableStateOf(false) }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "❓ الاستفسارات الدراسية",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = CyanAccent
                        )
                        Text(
                            text = "أسئلة الدروس والواجبات وحلولها النموذجية",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    GlassButton(
                        text = "طرح استفسار",
                        icon = Icons.Default.Add,
                        onClick = { isAddModalOpen = true },
                        modifier = Modifier.height(40.dp)
                    )
                }
            }

            if (issues.isEmpty()) {
                item {
                    EmptyStateGlass(
                        title = "لا توجد استفسارات مفتوحة",
                        subtitle = "هل لديك سؤال أو نقطة غير مفهومة في أي مادة أو واجب؟ اطرح استفسارك ليساعدك زملاؤك والمعلمون.",
                        icon = "❓",
                        actionButtonText = "طرح استفسار جديد",
                        onActionClick = { isAddModalOpen = true }
                    )
                }
            } else {
                items(issues, key = { it.id }) { issue ->
                    IssueCard(issue = issue)
                }
            }
        }

        if (isAddModalOpen) {
            AddIssueDialog(
                onDismiss = { isAddModalOpen = false },
                onAdd = { title, desc, subjId ->
                    viewModel.addIssue(
                        title = title,
                        description = desc,
                        subjectId = subjId
                    )
                    isAddModalOpen = false
                }
            )
        }
    }
}

@Composable
fun IssueCard(issue: IssueItem) {
    var expanded by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    val comments = remember { mutableStateListOf<Pair<String, String>>() }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = GlassSurface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val statusColor = when (issue.status) {
                        "SOLVED" -> EmeraldGreen
                        "IN_DISCUSSION" -> WarmAmber
                        else -> CyanAccent
                    }
                    val statusText = when (issue.status) {
                        "SOLVED" -> "تم الحل ✓"
                        "IN_DISCUSSION" -> "قيد النقاش"
                        else -> "مفتوح"
                    }

                    GlassPill(
                        text = statusText,
                        color = statusColor,
                        bgColor = statusColor.copy(alpha = 0.15f)
                    )
                    if (!issue.subjectName.isNullOrBlank()) {
                        Text(
                            text = "${issue.subjectIcon ?: "📚"} ${issue.subjectName}",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Text(
                    text = "💬 ${comments.size + issue.commentsCount}",
                    fontSize = 12.sp,
                    color = TextMuted,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = issue.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            if (!issue.description.isNullOrBlank()) {
                Text(
                    text = issue.description,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "طرح بواسطة: ${issue.authorName}",
                    fontSize = 11.sp,
                    color = CyanAccent
                )

                GlassOutlinedButton(
                    text = if (expanded) "إغلاق الردود" else "الرد والمناقشة",
                    icon = Icons.Default.ChatBubbleOutline,
                    onClick = { expanded = !expanded },
                    modifier = Modifier.height(34.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = GlassBorderSubtle, modifier = Modifier.padding(bottom = 10.dp))

                    comments.forEach { (author, text) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MidnightSurface)
                                .padding(8.dp)
                        ) {
                            Text(text = author, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                            Text(text = text, fontSize = 12.sp, color = TextPrimary, modifier = Modifier.padding(top = 2.dp))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            label = "",
                            placeholder = "اكتب إجابتك أو توضيحك...",
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (replyText.isNotBlank()) {
                                    comments.add(Pair("أنا", replyText.trim()))
                                    replyText = ""
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(CyanAccent)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "إرسال", tint = TextOnAccent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddIssueDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, description: String?, subjectId: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedSubjId by remember { mutableStateOf<String?>(DefaultSubjects[0].id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "طرح استفسار دراسي ❓",
                color = CyanAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("المادة المرتبطة (اختياري):", fontSize = 12.sp, color = TextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DefaultSubjects.take(4).forEach { subj ->
                        val isSelected = subj.id == selectedSubjId
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) CyanGlow else MidnightSurface)
                                .border(1.dp, if (isSelected) CyanAccent else GlassBorderSubtle, RoundedCornerShape(10.dp))
                                .clickable { selectedSubjId = subj.id }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = subj.name,
                                fontSize = 11.sp,
                                color = if (isSelected) CyanAccent else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                GlassTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "عنوان السؤال أو الاستفسار",
                    placeholder = "مثال: ما الفرق بين الحركة الدائرية والاهتزازية؟"
                )

                GlassTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "تفاصيل وتوضيح السؤال",
                    placeholder = "اشرح بالتفصيل ما الذي لم تفهمه..."
                )
            }
        },
        confirmButton = {
            GlassButton(
                text = "نشر الاستفسار",
                onClick = {
                    if (title.isNotBlank()) {
                        onAdd(title, description, selectedSubjId)
                    }
                }
            )
        },
        dismissButton = {
            GlassOutlinedButton(text = "إلغاء", onClick = onDismiss)
        },
        containerColor = MidnightSurface,
        shape = RoundedCornerShape(20.dp)
    )
}
