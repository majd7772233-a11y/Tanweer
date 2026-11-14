package com.magd.tanweer.ui.screens

import com.magd.tanweer.ui.theme.tanweerTextSize
import com.magd.tanweer.ui.theme.tanweerLineHeight

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.SchoolStats
import com.magd.tanweer.data.model.UserBrief
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.GlassButton
import com.magd.tanweer.ui.components.GlassCard
import com.magd.tanweer.ui.components.GlassPill
import com.magd.tanweer.ui.theme.CyanAccent
import com.magd.tanweer.ui.theme.EmeraldGreen
import com.magd.tanweer.ui.theme.GlassSurface
import com.magd.tanweer.ui.theme.MidnightBackground
import com.magd.tanweer.ui.theme.MidnightSurface
import com.magd.tanweer.ui.theme.TextMuted
import com.magd.tanweer.ui.theme.TextPrimary
import com.magd.tanweer.ui.theme.TextSecondary
import com.magd.tanweer.ui.theme.WarmAmber

private enum class AdminStudentActionType {
    CHANGE_STATUS,
    REMOVE_FROM_CLASS
}

private data class AdminStudentAction(
    val student: UserBrief,
    val type: AdminStudentActionType,
    val newStatus: String = ""
)

@Composable
fun AdminDashboardScreen(viewModel: TanweerViewModel) {
    val context = LocalContext.current
    val dashboardData by viewModel.adminDashboard.collectAsStateWithLifecycle()
    val dashboardError by viewModel.adminDashboardError.collectAsStateWithLifecycle()
    val isLoading by viewModel.isDashboardLoading.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showBroadcastDialog by rememberSaveable { mutableStateOf(false) }
    var broadcastTitle by rememberSaveable { mutableStateOf("") }
    var broadcastContent by rememberSaveable { mutableStateOf("") }
    var isPublishingBroadcast by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<AdminStudentAction?>(null) }
    var isActionInProgress by remember { mutableStateOf(false) }

    val allStudents = dashboardData?.students.orEmpty()
    val filteredStudents = remember(allStudents, searchQuery) {
        allStudents.filter { student -> student.matchesAdminSearch(searchQuery) }
    }
    val totalStudents = dashboardData?.stats?.totalStudents ?: 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 64.dp)
    ) {
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MidnightSurface,
                borderColor = Color(0xFFFFD700).copy(alpha = 0.55f)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dashboardData?.school?.name ?: "إدارة المدرسة",
                                color = WarmAmber,
                                fontSize = tanweerTextSize(17f),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "لوحة الإدارة المدرسية",
                                color = TextPrimary,
                                fontSize = tanweerTextSize(13f),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = currentUser?.fullName?.let { "مرحباً، $it" } ?: "إدارة حسابات الطلاب والتعاميم",
                                color = TextSecondary,
                                fontSize = tanweerTextSize(10f)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.loadAdminDashboard() },
                            enabled = !isLoading,
                            modifier = Modifier.size(38.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(19.dp),
                                    strokeWidth = 2.dp,
                                    color = CyanAccent
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "تحديث بيانات المدرسة",
                                    tint = CyanAccent
                                )
                            }
                        }
                    }

                    val stats = dashboardData?.stats
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdminStatCard(
                            title = "الطلاب",
                            value = stats?.totalStudents?.toString() ?: "—",
                            modifier = Modifier.weight(1f),
                            accent = CyanAccent
                        )
                        AdminStatCard(
                            title = "حسابات موقوفة",
                            value = stats?.totalDisabledUsers?.toString() ?: "—",
                            modifier = Modifier.weight(1f),
                            accent = WarmAmber
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdminStatCard(
                            title = "كل الحسابات",
                            value = stats?.totalUsers?.toString() ?: "—",
                            modifier = Modifier.weight(1f),
                            accent = TextPrimary
                        )
                        AdminStatCard(
                            title = "المجموعات",
                            value = stats?.totalGroups?.toString() ?: "—",
                            modifier = Modifier.weight(1f),
                            accent = EmeraldGreen
                        )
                    }

                    GlassButton(
                        text = "نشر تعميم مدرسي رسمي 📢",
                        onClick = { showBroadcastDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = GlassSurface
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "إدارة الطلاب",
                            color = TextPrimary,
                            fontSize = tanweerTextSize(15f),
                            fontWeight = FontWeight.Bold
                        )
                        GlassPill(
                            text = "${filteredStudents.size} / $totalStudents",
                            color = CyanAccent,
                            bgColor = CyanAccent.copy(alpha = 0.12f)
                        )
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("بحث بالاسم أو الهاتف أو الصف أو الشعبة") }
                    )
                    Text(
                        text = "يمكنك إيقاف حساب الطالب مؤقتاً أو فصله من شعبته دون حذف حسابه أو مساهماته.",
                        color = TextSecondary,
                        fontSize = tanweerTextSize(10f)
                    )
                }
            }
        }

        if (isLoading && dashboardData == null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = CyanAccent)
                }
            }
        }

        if (!dashboardError.isNullOrBlank()) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MidnightSurface,
                    borderColor = WarmAmber.copy(alpha = 0.55f)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("تعذر تحميل بيانات الإدارة", color = WarmAmber, fontWeight = FontWeight.Bold)
                        Text(dashboardError ?: "", color = TextSecondary, fontSize = tanweerTextSize(11f))
                        OutlinedButton(onClick = { viewModel.loadAdminDashboard() }) {
                            Text("إعادة المحاولة")
                        }
                    }
                }
            }
        } else if (dashboardData != null && filteredStudents.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = GlassSurface) {
                    Text(
                        text = if (allStudents.isEmpty()) "لا توجد حسابات طلاب في البيانات التي أعادها الخادم."
                        else "لا توجد نتائج مطابقة لعبارة البحث.",
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        fontSize = tanweerTextSize(12f)
                    )
                }
            }
        } else if (dashboardData != null) {
            items(filteredStudents, key = { it.id }) { student ->
                AdminStudentCard(
                    student = student,
                    onToggleStatus = {
                        val nextStatus = if (student.status.equals("DISABLED", ignoreCase = true)) "ACTIVE" else "DISABLED"
                        pendingAction = AdminStudentAction(
                            student = student,
                            type = AdminStudentActionType.CHANGE_STATUS,
                            newStatus = nextStatus
                        )
                    },
                    onRemoveFromClass = {
                        pendingAction = AdminStudentAction(
                            student = student,
                            type = AdminStudentActionType.REMOVE_FROM_CLASS
                        )
                    }
                )
            }
            if (totalStudents > allStudents.size) {
                item {
                    Text(
                        text = "يعرض الخادم أول ${allStudents.size} طالباً من أصل $totalStudents. استخدم البحث ضمن القائمة المحمّلة.",
                        color = TextMuted,
                        fontSize = tanweerTextSize(10f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (showBroadcastDialog) {
        AlertDialog(
            onDismissRequest = { if (!isPublishingBroadcast) showBroadcastDialog = false },
            title = { Text("تعميم مدرسي رسمي", color = WarmAmber) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = broadcastTitle,
                        onValueChange = { broadcastTitle = it },
                        label = { Text("عنوان التعميم") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = broadcastContent,
                        onValueChange = { broadcastContent = it },
                        label = { Text("نص التعميم") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val title = broadcastTitle.trim()
                        val body = broadcastContent.trim()
                        if (title.isBlank() || body.isBlank() || isPublishingBroadcast) return@Button
                        isPublishingBroadcast = true
                        viewModel.publishSchoolAnnouncement(title, body) { success, message ->
                            isPublishingBroadcast = false
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            if (success) {
                                showBroadcastDialog = false
                                broadcastTitle = ""
                                broadcastContent = ""
                                viewModel.loadAdminDashboard()
                            }
                        }
                    },
                    enabled = !isPublishingBroadcast && broadcastTitle.isNotBlank() && broadcastContent.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD700),
                        contentColor = MidnightBackground
                    )
                ) {
                    if (isPublishingBroadcast) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MidnightBackground
                        )
                    } else {
                        Text("نشر التعميم")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBroadcastDialog = false },
                    enabled = !isPublishingBroadcast
                ) {
                    Text("إلغاء")
                }
            },
            containerColor = MidnightSurface
        )
    }

    pendingAction?.let { action ->
        val isRemoving = action.type == AdminStudentActionType.REMOVE_FROM_CLASS
        val isDisabling = action.newStatus == "DISABLED"
        AlertDialog(
            onDismissRequest = { if (!isActionInProgress) pendingAction = null },
            title = {
                Text(
                    text = when {
                        isRemoving -> "فصل الطالب من شعبته"
                        isDisabling -> "إيقاف حساب الطالب"
                        else -> "إعادة تفعيل حساب الطالب"
                    },
                    color = WarmAmber
                )
            },
            text = {
                Text(
                    text = when {
                        isRemoving -> "سيُزال ${action.student.full_name} من عضوية شعبته الحالية فقط. سيبقى حسابه ودروسه وواجباته وسجله محفوظاً."
                        isDisabling -> "سيُمنع ${action.student.full_name} من تسجيل الدخول، وستُنهى جلساته الحالية. لن يُحذف أي محتوى."
                        else -> "سيُعاد تفعيل حساب ${action.student.full_name}. وسيحتاج الطالب إلى تسجيل الدخول من جديد."
                    },
                    color = TextSecondary,
                    fontSize = tanweerTextSize(12f)
                )
            },
            confirmButton = {
                Button(
                    enabled = !isActionInProgress,
                    onClick = {
                        isActionInProgress = true
                        val callback: (Boolean, String) -> Unit = { success, message ->
                            isActionInProgress = false
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            if (success) pendingAction = null
                        }
                        when (action.type) {
                            AdminStudentActionType.CHANGE_STATUS ->
                                viewModel.setSchoolStudentStatus(action.student.id, action.newStatus, callback)
                            AdminStudentActionType.REMOVE_FROM_CLASS ->
                                viewModel.removeStudentFromClass(action.student.id, callback)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRemoving) WarmAmber else CyanAccent,
                        contentColor = MidnightBackground
                    )
                ) {
                    if (isActionInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MidnightBackground
                        )
                    } else {
                        Text("تأكيد")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingAction = null },
                    enabled = !isActionInProgress
                ) {
                    Text("إلغاء")
                }
            },
            containerColor = MidnightSurface
        )
    }
}

@Composable
private fun AdminStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color
) {
    GlassCard(
        modifier = modifier,
        backgroundColor = GlassSurface
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = TextSecondary, fontSize = tanweerTextSize(10f))
            Text(value, color = accent, fontSize = tanweerTextSize(19f), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AdminStudentCard(
    student: UserBrief,
    onToggleStatus: () -> Unit,
    onRemoveFromClass: () -> Unit
) {
    val isDisabled = student.status.equals("DISABLED", ignoreCase = true)
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MidnightSurface,
        borderColor = if (isDisabled) WarmAmber.copy(alpha = 0.45f) else CyanAccent.copy(alpha = 0.25f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(CyanAccent.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = student.full_name.take(1),
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = tanweerTextSize(16f)
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(student.full_name, color = TextPrimary, fontSize = tanweerTextSize(13f), fontWeight = FontWeight.Bold)
                    Text("هاتف: ${student.phone_number}", color = TextSecondary, fontSize = tanweerTextSize(10f))
                    Text(
                        text = if (student.grade_id > 0 && student.section_id.isNotBlank())
                            "الصف ${student.grade_id} • شعبة ${student.section_id}"
                        else "البيانات الدراسية غير مكتملة",
                        color = TextMuted,
                        fontSize = tanweerTextSize(10f)
                    )
                }
                GlassPill(
                    text = if (isDisabled) "موقوف" else "نشط",
                    color = if (isDisabled) WarmAmber else EmeraldGreen,
                    bgColor = (if (isDisabled) WarmAmber else EmeraldGreen).copy(alpha = 0.12f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextButton(
                    onClick = onToggleStatus,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (isDisabled) EmeraldGreen else WarmAmber
                    )
                ) {
                    Text(if (isDisabled) "إعادة التفعيل" else "إيقاف الحساب", fontSize = tanweerTextSize(11f))
                }
                OutlinedButton(
                    onClick = onRemoveFromClass,
                    modifier = Modifier.weight(1f),
                    enabled = student.grade_id > 0 && student.section_id.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text("فصل من الشعبة", fontSize = tanweerTextSize(11f))
                }
            }
        }
    }
}

private fun UserBrief.matchesAdminSearch(query: String): Boolean {
    val value = query.trim()
    if (value.isBlank()) return true
    return full_name.contains(value, ignoreCase = true) ||
        phone_number.contains(value, ignoreCase = true) ||
        grade_id.toString().contains(value, ignoreCase = true) ||
        section_id.contains(value, ignoreCase = true) ||
        status.contains(value, ignoreCase = true)
}
