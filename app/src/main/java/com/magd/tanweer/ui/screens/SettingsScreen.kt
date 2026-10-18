package com.magd.tanweer.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.BuildConfig
import com.magd.tanweer.data.model.ComprehensiveSyncReport
import com.magd.tanweer.ui.SubScreen
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*
import com.magd.tanweer.util.TanweerHapticManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class FontSizeOption(val label: String, val sizeSp: Int, val scale: Float, val badge: String) {
    COMPACT_11("صغير جداً", 11, 0.88f, "11sp"),
    DEFAULT_12("افتراضي تنوير", 12, 1.0f, "12sp ✨"),
    MEDIUM_14("متوسط", 14, 1.15f, "14sp"),
    LARGE_16("كبير", 16, 1.30f, "16sp"),
    XLARGE_18("ضخم", 18, 1.50f, "18sp")
}

@Composable
fun SettingsScreen(
    viewModel: TanweerViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val selectedFontScale by viewModel.fontSizeScale.collectAsStateWithLifecycle()
    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val autoSyncEnabled by viewModel.autoSyncEnabled.collectAsStateWithLifecycle()
    val imageQuality by viewModel.imageQualitySetting.collectAsStateWithLifecycle()
    val notifyHomework by viewModel.notifyHomework.collectAsStateWithLifecycle()
    val notifySchedule by viewModel.notifySchedule.collectAsStateWithLifecycle()
    val notifyExams by viewModel.notifyExams.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val pendingOutboxCount by viewModel.repository.getPendingOutboxCount().collectAsStateWithLifecycle(initialValue = 0)

    var cacheSizeBytes by remember { mutableLongStateOf(0L) }
    var isClearingCache by remember { mutableStateOf(false) }
    var serverPingStatus by remember { mutableStateOf<String?>(null) }
    var isPingingServer by remember { mutableStateOf(false) }
    var pendingNotifToggleAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var syncReportDialog by remember { mutableStateOf<ComprehensiveSyncReport?>(null) }
    var isSyncingComprehensive by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingNotifToggleAction?.invoke()
            Toast.makeText(context, "تم تفعيل إذن الإشعارات بنجاح 🔔", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "لم يتم منح إذن الإشعارات. يرجى تفعيله من إعدادات الهاتف لتلقي التنبيهات.", Toast.LENGTH_LONG).show()
        }
        pendingNotifToggleAction = null
    }

    fun requestNotificationPermissionIfNeeded(action: () -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPerm) {
                pendingNotifToggleAction = action
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        action()
    }

    fun calculateCacheSize() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                var size = 0L
                val cacheDir = context.cacheDir
                cacheDir.walkTopDown().forEach { file ->
                    if (file.isFile) size += file.length()
                }
                cacheSizeBytes = size
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        calculateCacheSize()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // -------------------------------------------------------------
        // SECTION 1: FONT SIZE (حجم الخط - الافتراضي 12sp)
        // -------------------------------------------------------------
        item {
            SettingsSectionHeader(title = "🔤 حجم الخط والنصوص", subtitle = "الافتراضي 12sp بدقة عالية ومريحة للعين")
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "اختر حجم الخط الأساسي في التطبيق:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FontSizeOption.values().forEach { option ->
                            val isSelected = (selectedFontScale - option.scale).let { kotlin.math.abs(it) < 0.05f }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) CyanGlow else MidnightSurface)
                                    .border(
                                        1.dp,
                                        if (isSelected) CyanAccent else GlassBorderSubtle,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        viewModel.setFontScale(option.scale)
                                        Toast.makeText(context, "تم تعيين حجم الخط: ${option.label}", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.setFontScale(option.scale) },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = CyanAccent,
                                            unselectedColor = TextSecondary
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = option.label,
                                            fontSize = (option.sizeSp).sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) CyanAccent else TextPrimary
                                        )
                                        Text(
                                            text = "حجم Base Font: ${option.sizeSp}sp",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                GlassPill(
                                    text = option.badge,
                                    color = if (isSelected) CyanAccent else TextSecondary,
                                    bgColor = if (isSelected) CyanGlow else Color.Transparent
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MidnightSurface)
                            .border(1.dp, GlassBorderSubtle, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "معاينة حية للخط:",
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "منصة تنوير التعليمية — متابعة الدروس والواجبات المدرسية للشعب والصفوف الدراسية بأعلى سرعة وكفاءة.",
                                fontSize = (12 * selectedFontScale).sp,
                                color = TextPrimary,
                                lineHeight = (18 * selectedFontScale).sp
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // SECTION 2: THEME & DISPLAY (المظهر والسمة)
        // -------------------------------------------------------------
        item {
            SettingsSectionHeader(title = "🎨 المظهر والألوان", subtitle = "تخصيص نمط العرض وتباين الشاشة")
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeButton(
                            title = "داكن نيون",
                            subtitle = "Dark Midnight",
                            icon = "🌌",
                            isSelected = appTheme == "DARK",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setAppTheme("DARK") }
                        )
                        ThemeButton(
                            title = "سواد تام",
                            subtitle = "AMOLED Black",
                            icon = "🖤",
                            isSelected = appTheme == "AMOLED",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setAppTheme("AMOLED") }
                        )
                    }

                    SettingsToggleRow(
                        icon = Icons.Default.Vibration,
                        title = "الاهتزاز التفاعلي (Haptic Feedback)",
                        subtitle = "اهتزاز لطيف فعلي عند النقر وحفظ البيانات",
                        checked = hapticsEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setHapticsEnabled(enabled)
                            if (enabled) {
                                TanweerHapticManager.performHaptic(context, true, TanweerHapticManager.HapticType.SUCCESS)
                                Toast.makeText(context, "تم تفعيل الاهتزاز التفاعلي 📳", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // SECTION 3: SYNC & OFFLINE (المزامنة والعمل بدون اتصال)
        // -------------------------------------------------------------
        item {
            SettingsSectionHeader(title = "🔄 المزامنة والبيانات المحلية", subtitle = "التحكم في تحديثات السيرفر وقاعدة البيانات")
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsToggleRow(
                        icon = Icons.Default.Sync,
                        title = "المزامنة التلقائية الفورية",
                        subtitle = "تحديث الواجبات والجدول عند فتح الشاشات والتبديل بينها",
                        checked = autoSyncEnabled,
                        onCheckedChange = { viewModel.setAutoSyncEnabled(it) }
                    )

                    HorizontalDivider(color = GlassBorderSubtle)

                    // Honest Outbox Queue State
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (pendingOutboxCount == 0) EmeraldGreen.copy(alpha = 0.12f) else WarmAmber.copy(alpha = 0.15f))
                            .border(1.dp, if (pendingOutboxCount == 0) EmeraldGreen.copy(alpha = 0.4f) else WarmAmber.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (pendingOutboxCount == 0) "🟢" else "🟡", fontSize = 14.sp)
                                Text(
                                    text = if (pendingOutboxCount == 0) "قائمة الانتظار السحابية: متزامنة تماماً" else "قائمة الانتظار السحابية (Outbox): $pendingOutboxCount عمليات معلقة",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pendingOutboxCount == 0) EmeraldGreen else WarmAmber
                                )
                            }
                            Text(
                                text = if (pendingOutboxCount == 0) "كافة تعديلاتك وواجباتك متزامنة ومؤكدة على السيرفر." else "هناك عمليات محفوظة محلياً بانتظار رفعها للسيرفر عند توفر الاتصال.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }

                        if (pendingOutboxCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            GlassOutlinedButton(
                                text = "إرسال 📤",
                                onClick = {
                                    viewModel.repository.mutationQueue.triggerDrain()
                                    Toast.makeText(context, "جاري إرسال العمليات المعلقة في قائمة الانتظار...", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = GlassBorderSubtle)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("إعادة المزامنة الشاملة الآن", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("تحديث كل البيانات والتحقق من نتيجة كل قسم", fontSize = 11.sp, color = TextSecondary)
                        }
                        GlassButton(
                            text = if (isSyncingComprehensive) "جاري الفحص..." else "مزامنة الكل ⚡",
                            enabled = !isSyncingComprehensive,
                            onClick = {
                                isSyncingComprehensive = true
                                TanweerHapticManager.performHaptic(context, hapticsEnabled, TanweerHapticManager.HapticType.CLICK)
                                Toast.makeText(context, "جاري فحص وتحديث ومزامنة جميع أقسام تنوير...", Toast.LENGTH_SHORT).show()
                                viewModel.syncAllComprehensive { report ->
                                    isSyncingComprehensive = false
                                    syncReportDialog = report
                                    if (report.isAllSucceeded) {
                                        TanweerHapticManager.performHaptic(context, hapticsEnabled, TanweerHapticManager.HapticType.SUCCESS)
                                    } else {
                                        TanweerHapticManager.performHaptic(context, hapticsEnabled, TanweerHapticManager.HapticType.WARNING)
                                    }
                                }
                            },
                            modifier = Modifier.height(36.dp)
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // SECTION 4: MEDIA & IMAGE COMPRESSION (الصور وضغط الوسائط)
        // -------------------------------------------------------------
        item {
            SettingsSectionHeader(title = "📷 الصور وضغط التوثيق", subtitle = "إعدادات جودة وضغط صور الواجبات والدروس")
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "مستوى ضغط الصور المرفوعة:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("COMPACT", "أقصى ضغط ⚡", "< 1MB"),
                            Triple("BALANCED", "متوازن ⚖️", "< 1.25MB"),
                            Triple("HIGH", "عالي الدقة 🖼️", "< 1.5MB")
                        ).forEach { (key, name, cap) ->
                            val isSelected = imageQuality == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) CyanGlow else MidnightSurface)
                                    .border(1.dp, if (isSelected) CyanAccent else GlassBorderSubtle, RoundedCornerShape(10.dp))
                                    .clickable { viewModel.setImageQualitySetting(key) }
                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) CyanAccent else TextPrimary
                                    )
                                    Text(text = cap, fontSize = 9.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // SECTION 5: NOTIFICATIONS (التنبيهات المدرسية)
        // -------------------------------------------------------------
        item {
            SettingsSectionHeader(title = "🔔 التنبيهات المدرسية", subtitle = "تخصيص الإشعارات والتذكيرات")
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsToggleRow(
                        icon = Icons.Default.Assignment,
                        title = "تذكيرات تسليم الواجبات",
                        subtitle = "تنبيه قبل موعد استحقاق الواجب والتكليف",
                        checked = notifyHomework,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                requestNotificationPermissionIfNeeded {
                                    viewModel.setNotifyHomework(true)
                                }
                            } else {
                                viewModel.setNotifyHomework(false)
                            }
                        }
                    )
                    HorizontalDivider(color = GlassBorderSubtle)
                    SettingsToggleRow(
                        icon = Icons.Default.CalendarToday,
                        title = "تذكير جدول الحصص الصباحي",
                        subtitle = "إشعار صباحي بحصص اليوم الدراسي",
                        checked = notifySchedule,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                requestNotificationPermissionIfNeeded {
                                    viewModel.setNotifySchedule(true)
                                }
                            } else {
                                viewModel.setNotifySchedule(false)
                            }
                        }
                    )
                    HorizontalDivider(color = GlassBorderSubtle)
                    SettingsToggleRow(
                        icon = Icons.Default.Alarm,
                        title = "العد التنازلي للاختبارات",
                        subtitle = "تذكير بمواعيد الاختبارات الشهرية والنهائية",
                        checked = notifyExams,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                requestNotificationPermissionIfNeeded {
                                    viewModel.setNotifyExams(true)
                                }
                            } else {
                                viewModel.setNotifyExams(false)
                            }
                        }
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // SECTION 6: CACHE & STORAGE (التخزين وتنظيف الكاش)
        // -------------------------------------------------------------
        item {
            SettingsSectionHeader(title = "💾 التخزين والذاكرة المؤقتة", subtitle = "إدارة مساحة الجهاز وتنظيف الكاش")
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("الملفات المؤقتة والكاش", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            text = "الحجم الحالي: ${(cacheSizeBytes / (1024 * 1024.0)).let { "%.2f".format(it) }} ميجابايت",
                            fontSize = 11.sp,
                            color = CyanAccent
                        )
                    }

                    GlassOutlinedButton(
                        text = if (isClearingCache) "جاري التنظيف..." else "تنظيف الكاش 🧹",
                        onClick = {
                            isClearingCache = true
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    context.cacheDir.deleteRecursively()
                                    context.cacheDir.mkdirs()
                                } catch (_: Exception) {}
                                withContext(Dispatchers.Main) {
                                    calculateCacheSize()
                                    isClearingCache = false
                                    Toast.makeText(context, "تم تنظيف الذاكرة المؤقتة بنجاح", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // SECTION 7: SERVER DIAGNOSTICS & CONNECTION (فحص الخادم)
        // -------------------------------------------------------------
        item {
            SettingsSectionHeader(title = "🌐 فحص الاتصال بالخادم", subtitle = "التأكد من جاهزية السيرفر واستجابة الشبكة")
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("خادم Cloudflare Workers", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("tanweer.magd.workers.dev", fontSize = 11.sp, color = TextSecondary)
                        }

                        GlassButton(
                            text = if (isPingingServer) "جاري الفحص..." else "فحص الاتصال 📡",
                            enabled = !isPingingServer,
                            onClick = {
                                isPingingServer = true
                                serverPingStatus = null
                                coroutineScope.launch(Dispatchers.IO) {
                                    val start = System.currentTimeMillis()
                                    val statusText = try {
                                        val url = URL("https://tanweer.magd.workers.dev/api/v1/health")
                                        val conn = url.openConnection() as HttpURLConnection
                                        conn.connectTimeout = 6000
                                        conn.readTimeout = 6000
                                        conn.requestMethod = "GET"
                                        val code = conn.responseCode
                                        val latency = System.currentTimeMillis() - start
                                        if (code == 200) {
                                            "متصل بالسيرفر بنجاح ✅ (${latency}ms)"
                                        } else {
                                            "استجاب السيرفر برمز: $code (${latency}ms)"
                                        }
                                    } catch (e: Exception) {
                                        "تعذر الوصول للسيرفر: ${e.localizedMessage ?: "انقطاع الشبكة"}"
                                    }
                                    withContext(Dispatchers.Main) {
                                        serverPingStatus = statusText
                                        isPingingServer = false
                                    }
                                }
                            },
                            modifier = Modifier.height(36.dp)
                        )
                    }

                    if (serverPingStatus != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (serverPingStatus!!.contains("✅")) EmeraldGreen.copy(alpha = 0.15f) else Color(0xFFFF5252).copy(alpha = 0.15f))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = serverPingStatus!!,
                                fontSize = 12.sp,
                                color = if (serverPingStatus!!.contains("✅")) EmeraldGreen else Color(0xFFFF5252),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // SECTION 8: APP VERSION & UPDATE CHECK
        // -------------------------------------------------------------
        item {
            SettingsSectionHeader(title = "🚀 التحديثات والإصدار", subtitle = "التحقق من حالة النسخة وسجل التحسينات")
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.setSubScreen(SubScreen.VERSION_CHECK) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🚀", fontSize = 22.sp)
                        Column {
                            Text("فحص التحديثات والإصدار (${BuildConfig.VERSION_NAME})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("التحقق من توافق النسخة مع الخادم وما الجديد", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                    GlassPill(text = "محدث ✓", color = EmeraldGreen, bgColor = EmeraldGreen.copy(alpha = 0.15f))
                }
            }
        }

        // -------------------------------------------------------------
        // SECTION 9: ABOUT TANWEER (حول المنصة)
        // -------------------------------------------------------------
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "تـنـويـر ✨", fontSize = 20.sp, fontWeight = FontWeight.Black, color = CyanAccent)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "المنصة التعليمية للطلاب والمعلمين", fontSize = 12.sp, color = TextSecondary)
                    Text(text = "الإصدار ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • معمارية Cloudflare Workers + SQLite Room", fontSize = 11.sp, color = TextMuted)
                }
            }
        }
    }

    if (syncReportDialog != null) {
        val report = syncReportDialog!!
        AlertDialog(
            onDismissRequest = { syncReportDialog = null },
            title = {
                Text(
                    text = if (report.isAllSucceeded) "تقرير المزامنة الشاملة ✅" else "نتيجة المزامنة الشاملة ⚠️",
                    fontSize = 17.sp,
                    color = if (report.isAllSucceeded) EmeraldGreen else WarmAmber,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = report.summaryMessage,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    HorizontalDivider(color = GlassBorderSubtle)
                    if (report.succeededSections.isNotEmpty()) {
                        Text(
                            text = "الأقسام المكتملة بنجاح (${report.succeededSections.size}):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen
                        )
                        Text(
                            text = report.succeededSections.joinToString(" • "),
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    if (report.failedSections.isNotEmpty()) {
                        Text(
                            text = "الأقسام المتعذرة (${report.failedSections.size}):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = RubyRed
                        )
                        report.failedSections.forEach { (sec, err) ->
                            Text(
                                text = "• $sec: $err",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                    if (report.offlineSections.isNotEmpty()) {
                        Text(
                            text = "أقسام تعمل بالوضع المحلي المؤقت (${report.offlineSections.size}):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarmAmber
                        )
                        Text(
                            text = report.offlineSections.joinToString(" • "),
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    if (report.pendingOutboxCount > 0) {
                        Text(
                            text = "عمليات محفوظة في جهازك بانتظار الرفع: ${report.pendingOutboxCount}",
                            fontSize = 11.sp,
                            color = WarmAmber
                        )
                    }
                }
            },
            confirmButton = {
                GlassButton(
                    text = "حسناً",
                    onClick = { syncReportDialog = null }
                )
            },
            containerColor = MidnightSurface
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
        Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
        Text(text = subtitle, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun ThemeButton(
    title: String,
    subtitle: String,
    icon: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) CyanGlow else MidnightSurface)
            .border(1.dp, if (isSelected) CyanAccent else GlassBorderSubtle, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) CyanAccent else TextPrimary
            )
            Text(text = subtitle, fontSize = 9.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CyanGlow),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(text = subtitle, fontSize = 11.sp, color = TextSecondary)
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = CyanAccent,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = MidnightSurface
            )
        )
    }
}
