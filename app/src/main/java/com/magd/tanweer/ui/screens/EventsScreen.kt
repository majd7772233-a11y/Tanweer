package com.magd.tanweer.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.SchoolEventItem
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*

@Composable
fun EventsScreen(
    viewModel: TanweerViewModel
) {
    val context = LocalContext.current
    BackHandler {
        viewModel.navigateBack()
    }

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val activeGroupId = selectedGroupId.ifEmpty { currentUser?.defaultGroupId ?: "" }

    val events by viewModel.repository.getEvents(activeGroupId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(activeGroupId) {
        if (activeGroupId.isNotBlank()) {
            viewModel.repository.syncEvents(activeGroupId)
        }
    }

    val todayStr = remember { TanweerViewModel.getTodayDateString() }

    var selectedCategory by remember { mutableStateOf("ALL") }
    var showAddDialog by remember { mutableStateOf(false) }
    var activeTab by remember { mutableIntStateOf(0) } // 0 = قادمة, 1 = أرشيف سابقة

    val categories = listOf(
        "ALL" to "الكل 🎪",
        "ACTIVITY" to "أنشطة ومسابقات 🏆",
        "CELEBRATION" to "مناسبات واحتفالات 🎉",
        "HOLIDAY" to "عطل وإجازات 🏖️",
        "WORKSHOP" to "ورش ومراجعات 💡",
        "GENERAL" to "عام 📌"
    )

    val filteredEvents = remember(events, selectedCategory, activeTab) {
        events.filter { ev ->
            val matchCategory = if (selectedCategory == "ALL") true else ev.category.equals(selectedCategory, ignoreCase = true)
            val isUpcomingOrToday = ev.eventDate >= todayStr
            val matchTab = if (activeTab == 0) isUpcomingOrToday else !isUpcomingOrToday
            matchCategory && matchTab
        }.sortedWith(
            if (activeTab == 0) compareBy { it.eventDate }
            else compareByDescending { it.eventDate }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = PurpleAccent,
                contentColor = TextOnAccent,
                shape = CircleShape,
                modifier = Modifier
                    .size(56.dp)
                    .border(2.dp, PurpleAccent.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة فعالية جديدة", modifier = Modifier.size(28.dp))
            }
        },
        containerColor = MidnightBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.setSubScreen(SubScreen.NONE) },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(GlassSurface)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = CyanAccent
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "الفعاليات والأنشطة المدرسية 🎪",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = CyanAccent
                            )
                            Text(
                                text = "المسابقات، المناسبات، الإجازات وورش العمل",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    GlassButton(
                        text = "إضافة فعالية",
                        icon = Icons.Default.Add,
                        onClick = { showAddDialog = true },
                        modifier = Modifier.height(36.dp)
                    )
                }
            }

            // Tabs: القادمة vs السابقة
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(GlassSurface)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (activeTab == 0) PurpleAccent.copy(alpha = 0.25f) else Color.Transparent)
                            .border(1.dp, if (activeTab == 0) PurpleAccent else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { activeTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "الفعاليات القادمة (${events.count { it.eventDate >= todayStr }}) ⏳",
                            fontSize = 12.sp,
                            fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (activeTab == 0) PurpleAccent else TextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (activeTab == 1) CyanAccent.copy(alpha = 0.25f) else Color.Transparent)
                            .border(1.dp, if (activeTab == 1) CyanAccent else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { activeTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "الأرشيف والسابقات (${events.count { it.eventDate < todayStr }}) 📦",
                            fontSize = 12.sp,
                            fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (activeTab == 1) CyanAccent else TextSecondary
                        )
                    }
                }
            }

            // Category Filter Pills
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { (catId, catLabel) ->
                        val isSelected = catId == selectedCategory
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) PurpleAccent.copy(alpha = 0.2f) else MidnightSurface)
                                .border(
                                    1.dp,
                                    if (isSelected) PurpleAccent else GlassBorderSubtle,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedCategory = catId }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = catLabel,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) PurpleAccent else TextSecondary
                            )
                        }
                    }
                }
            }

            // Events List or Empty State
            if (filteredEvents.isEmpty()) {
                item {
                    EmptyStateGlass(
                        title = if (activeTab == 0) "لا توجد فعاليات قادمة مجدولة" else "لا توجد فعاليات سابقة",
                        subtitle = "يمكن لأي عضو في الشعبة أو المدرسة إضافة وتوثيق فعالية جديدة ليراها الجميع في التقويم وصفحة الفعاليات.",
                        icon = "🎪",
                        actionButtonText = "إضافة فعالية للشعبة",
                        onActionClick = { showAddDialog = true }
                    )
                }
            } else {
                items(filteredEvents, key = { it.id }) { event ->
                    val isToday = event.eventDate == todayStr

                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = if (isToday) EmeraldGreen else PurpleAccent.copy(alpha = 0.4f),
                        backgroundColor = GlassSurface
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = when (event.category.uppercase()) {
                                            "ACTIVITY" -> "🏆"
                                            "CELEBRATION" -> "🎉"
                                            "HOLIDAY" -> "🏖️"
                                            "WORKSHOP" -> "💡"
                                            else -> "📌"
                                        },
                                        fontSize = 20.sp
                                    )
                                    Column {
                                        Text(
                                            text = event.title,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "التاريخ: ${event.eventDate} ${event.timeStr?.let { "• $it" } ?: ""}",
                                            fontSize = 12.sp,
                                            color = if (isToday) EmeraldGreen else CyanAccent,
                                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }

                                GlassPill(
                                    text = if (isToday) "اليوم! 🔥" else when (event.category.uppercase()) {
                                        "ACTIVITY" -> "نشاط 🏆"
                                        "CELEBRATION" -> "مناسبة 🎉"
                                        "HOLIDAY" -> "إجازة 🏖️"
                                        "WORKSHOP" -> "ورشة 💡"
                                        else -> "فعالية 📌"
                                    },
                                    color = if (isToday) EmeraldGreen else PurpleAccent,
                                    bgColor = if (isToday) EmeraldGreen.copy(alpha = 0.15f) else PurpleAccent.copy(alpha = 0.15f)
                                )
                            }

                            if (!event.description.isNullOrBlank()) {
                                Text(
                                    text = event.description,
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    lineHeight = 18.sp
                                )
                            }

                            if (!event.location.isNullOrBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = "المكان",
                                        tint = WarmAmber,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "المكان: ${event.location}",
                                        fontSize = 11.sp,
                                        color = WarmAmber
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Event Dialog
    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("ACTIVITY") }
        var eventDate by remember { mutableStateOf(todayStr) }
        var timeStr by remember { mutableStateOf("") }
        var location by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                borderColor = PurpleAccent,
                backgroundColor = MidnightSurface
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🎪 إضافة فعالية أو مناسبة جديدة",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("عنوان الفعالية *", fontSize = 12.sp) },
                        placeholder = { Text("مثال: معرض العلوم السنوي", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    // Category dropdown / selector
                    Column {
                        Text("التصنيف:", fontSize = 11.sp, color = TextSecondary)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "ACTIVITY" to "نشاط",
                                "CELEBRATION" to "احتفال",
                                "HOLIDAY" to "عطلة",
                                "WORKSHOP" to "ورشة"
                            ).forEach { (catKey, catName) ->
                                val isCat = category == catKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCat) PurpleAccent.copy(alpha = 0.3f) else GlassSurface)
                                        .border(1.dp, if (isCat) PurpleAccent else GlassBorderSubtle, RoundedCornerShape(8.dp))
                                        .clickable { category = catKey }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = catName,
                                        fontSize = 11.sp,
                                        fontWeight = if (isCat) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCat) PurpleAccent else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = eventDate,
                            onValueChange = { eventDate = it },
                            label = { Text("التاريخ (YYYY-MM-DD) *", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = timeStr,
                            onValueChange = { timeStr = it },
                            label = { Text("الوقت (اختياري)", fontSize = 11.sp) },
                            placeholder = { Text("09:00 ص", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("المكان / القاعة (اختياري)", fontSize = 12.sp) },
                        placeholder = { Text("مسرح المدرسة / القاعة الرياضية", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("تفاصيل وملاحظات الفعالية", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("إلغاء", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (title.isBlank() || eventDate.isBlank()) {
                                    Toast.makeText(context, "يرجى كتابة عنوان الفعالية وتاريخها", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.addEvent(
                                    title = title.trim(),
                                    description = description.trim().ifBlank { null },
                                    category = category,
                                    eventDate = eventDate.trim(),
                                    timeStr = timeStr.trim().ifBlank { null },
                                    location = location.trim().ifBlank { null },
                                    onResult = { res ->
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "تمت إضافة الفعالية بنجاح وسوف تظهر في التقويم 🎪", Toast.LENGTH_SHORT).show()
                                            showAddDialog = false
                                        } else {
                                            val err = res.exceptionOrNull()?.message ?: "حدث خطأ أثناء حفظ الفعالية"
                                            Toast.makeText(context, "تعذر إضافة الفعالية: $err", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("نشر الفعالية ✨", color = TextOnAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
