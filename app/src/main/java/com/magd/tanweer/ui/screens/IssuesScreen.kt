package com.magd.tanweer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.IssueCommentItem
import com.magd.tanweer.data.model.IssueItem
import com.magd.tanweer.data.model.SubjectItem
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*

enum class IssueFilter(val label: String) {
    ALL("الكل"),
    OPEN("قيد الانتظار ⏳"),
    SOLVED("تم حلها ✅"),
    DISCUSSING("قيد النقاش 💬")
}

@Composable
fun IssuesScreen(
    viewModel: TanweerViewModel
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId ?: "" }

    val issues by viewModel.repository.getIssues(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val gradeSubjects = remember(currentUser?.gradeId) {
        viewModel.getSubjectsForCurrentGrade()
    }

    var selectedIssueId by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf(IssueFilter.ALL) }
    var isAddModalOpen by remember { mutableStateOf(false) }

    // If an issue is selected, show Issue Details screen
    if (selectedIssueId != null) {
        IssueDetailView(
            issueId = selectedIssueId!!,
            viewModel = viewModel,
            onBack = { selectedIssueId = null }
        )
        return
    }

    val filteredIssues = remember(issues, selectedFilter) {
        when (selectedFilter) {
            IssueFilter.ALL -> issues
            IssueFilter.OPEN -> issues.filter { it.status == "OPEN" }
            IssueFilter.SOLVED -> issues.filter { it.status == "SOLVED" }
            IssueFilter.DISCUSSING -> issues.filter { it.status == "IN_DISCUSSION" }
        }
    }

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
                            text = "أسئلة الدروس والواجبات وحلولها النموذجية في الشعبة",
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

            // Filters
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(IssueFilter.values()) { filter ->
                        val isSelected = selectedFilter == filter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) CyanGlow else MidnightSurface)
                                .border(1.dp, if (isSelected) CyanAccent else GlassBorderSubtle, RoundedCornerShape(12.dp))
                                .clickable { selectedFilter = filter }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = filter.label,
                                fontSize = 12.sp,
                                color = if (isSelected) CyanAccent else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            if (filteredIssues.isEmpty()) {
                item {
                    EmptyStateGlass(
                        title = "لا توجد استفسارات هنا",
                        subtitle = "هل لديك نقطة غامضة في المنهج أو الواجب؟ اطرح سؤالك ليجيبك زملاؤك والمعلم وتوثق الإجابة النموذجية.",
                        icon = "❓",
                        actionButtonText = "طرح استفسار جديد",
                        onActionClick = { isAddModalOpen = true }
                    )
                }
            } else {
                items(filteredIssues, key = { it.id }) { issue ->
                    IssueListItemCard(
                        issue = issue,
                        onClick = { selectedIssueId = issue.id }
                    )
                }
            }
        }

        if (isAddModalOpen) {
            AddIssueDialog(
                subjects = gradeSubjects,
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
fun IssueListItemCard(
    issue: IssueItem,
    onClick: () -> Unit
) {
    val isSolved = issue.status == "SOLVED"
    val borderColor = if (isSolved) EmeraldGreen.copy(alpha = 0.5f) else GlassBorderSubtle

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        borderColor = borderColor,
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
                        "SOLVED" -> "تم الحل بنموذجية ✓"
                        "IN_DISCUSSION" -> "قيد النقاش 💬"
                        else -> "مفتوح للنقاش ⏳"
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
                            maxLines = 1
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (issue.commentsCount > 0) CyanGlow else MidnightSurface)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${issue.commentsCount} إجابة",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (issue.commentsCount > 0) CyanAccent else TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

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
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "سأل: ${issue.authorName}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "عرض الإجابات والمناقشة",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun IssueDetailView(
    issueId: String,
    viewModel: TanweerViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    LaunchedEffect(issueId) {
        viewModel.syncIssueDetails(issueId)
    }

    val issue by viewModel.repository.getIssue(issueId)
        .collectAsStateWithLifecycle(initialValue = null)

    val comments by viewModel.repository.getIssueComments(issueId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    var replyText by remember { mutableStateOf("") }

    val bestComment = remember(comments) {
        comments.find { it.isBestAnswer }
    }
    val otherComments = remember(comments) {
        comments.filter { !it.isBestAnswer }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "العودة",
                            tint = CyanAccent
                        )
                    }
                    Text(
                        text = "تفاصيل الاستفسار",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                issue?.let { iss ->
                    val statusColor = if (iss.status == "SOLVED") EmeraldGreen else CyanAccent
                    val statusText = if (iss.status == "SOLVED") "تم الحل بنجاح" else "قيد النقاش"
                    GlassPill(text = statusText, color = statusColor, bgColor = statusColor.copy(alpha = 0.15f))
                }
            }

            // Scrollable Content
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Issue Question Card
                item {
                    if (issue != null) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = MidnightSurface,
                            borderColor = CyanGlow
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (!issue!!.subjectName.isNullOrBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    ) {
                                        Text(issue!!.subjectIcon ?: "📚", fontSize = 16.sp)
                                        Text(
                                            text = issue!!.subjectName ?: "",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyanAccent
                                        )
                                    }
                                }

                                Text(
                                    text = issue!!.title,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextPrimary
                                )

                                if (!issue!!.description.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = issue!!.description ?: "",
                                        fontSize = 14.sp,
                                        color = TextSecondary,
                                        lineHeight = 22.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = GlassBorderSubtle)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "طرح بواسطة: ${issue!!.authorName}",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                    Text(
                                        text = "${comments.size} إجابات مسجلة",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyanAccent
                                    )
                                }
                            }
                        }
                    }
                }

                // Best Answer Pinned Section (if exists)
                if (bestComment != null) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "الإجابة النموذجية المعتمدة 🏆",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = WarmAmber
                                )
                            }

                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = WarmAmber.copy(alpha = 0.08f),
                                borderColor = WarmAmber.copy(alpha = 0.6f)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = bestComment.authorName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WarmAmber
                                        )
                                        GlassPill(
                                            text = "الحل المعتمد ✓",
                                            color = EmeraldGreen,
                                            bgColor = EmeraldGreen.copy(alpha = 0.15f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = bestComment.comment,
                                        fontSize = 14.sp,
                                        color = TextPrimary,
                                        lineHeight = 22.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Other Answers
                item {
                    Text(
                        text = if (bestComment != null) "بقية الإجابات والمناقشات (${otherComments.size}):" else "الإجابات والمناقشات (${comments.size}):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (otherComments.isEmpty() && bestComment == null) {
                    item {
                        EmptyStateGlass(
                            title = "لا توجد إجابات بعد",
                            subtitle = "كن أول من يساعد ويقدم الحل والشرح النموذجي لهذا السؤال!",
                            icon = "💡"
                        )
                    }
                } else {
                    items(otherComments, key = { it.id }) { comment ->
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
                                    Text(
                                        text = comment.authorName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyanAccent
                                    )

                                    // Mark as Best Answer button if issue not solved or if this can be chosen
                                    if (bestComment == null) {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.markBestAnswer(issueId, comment.id)
                                            },
                                            modifier = Modifier.height(28.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = WarmAmber),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, WarmAmber.copy(alpha = 0.5f)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("اعتماد كحل ⭐", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = comment.comment,
                                    fontSize = 13.sp,
                                    color = TextPrimary,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Reply Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    label = "",
                    placeholder = "اكتب إجابتك أو شرحك للحل النموذجي...",
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        if (replyText.isNotBlank()) {
                            viewModel.addIssueComment(issueId, replyText.trim()) {
                                replyText = ""
                            }
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(CyanAccent)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "إرسال الإجابة", tint = TextOnAccent)
                }
            }
        }
    }
}

@Composable
fun AddIssueDialog(
    subjects: List<SubjectItem>,
    onDismiss: () -> Unit,
    onAdd: (title: String, description: String?, subjectId: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedSubjId by remember { mutableStateOf<String?>(subjects.firstOrNull()?.id) }

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
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(subjects) { subj ->
                        val isSelected = subj.id == selectedSubjId
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) CyanGlow else MidnightSurface)
                                .border(1.dp, if (isSelected) CyanAccent else GlassBorderSubtle, RoundedCornerShape(10.dp))
                                .clickable { selectedSubjId = subj.id }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${subj.icon} ${subj.name}",
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
                    label = "عنوان السؤال أو الاستفسار *",
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
                enabled = title.isNotBlank(),
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
