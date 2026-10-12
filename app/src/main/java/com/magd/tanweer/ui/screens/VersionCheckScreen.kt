package com.magd.tanweer.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magd.tanweer.BuildConfig
import com.magd.tanweer.data.remote.SystemVersionResponse
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.GlassButton
import com.magd.tanweer.ui.components.GlassCard
import com.magd.tanweer.ui.components.GlassPill
import com.magd.tanweer.ui.theme.*
import com.magd.tanweer.util.AppVersionInfo
import com.magd.tanweer.util.AppVersionManager
import com.magd.tanweer.util.VersionSource
import kotlinx.coroutines.launch

@Composable
fun VersionCheckScreen(
    viewModel: TanweerViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val installedVersionName = BuildConfig.VERSION_NAME
    val installedVersionCode = BuildConfig.VERSION_CODE

    var isChecking by remember { mutableStateOf(false) }
    var lastCheckedTime by remember { mutableStateOf<String?>("لم يتم الفحص بعد") }
    var checkErrorMessage by remember { mutableStateOf<String?>(null) }
    var versionInfoState by remember { mutableStateOf<AppVersionInfo?>(null) }

    val defaultChangelog = listOf(
        "✨ نظام الحوكمة الموحد والتصويت الشامل على القرارات المدرسية",
        "📷 ماسح السبورة الذكي عالي الدقة ومعالجة التباين التلقائي",
        "📚 مكتبة المناهج الذكية مع إدارة متقدمة للتخزين والذاكرة",
        "🛡️ محرك الصلاحيات الهرمي وتأمين ترقيات الرتب المدرسية",
        "🚀 دعم واسع للأجهزة بدءاً من Android 6.0 (API 23+) فما فوق",
        "🔄 دعم العمل بدون اتصال مع حفظ محلي وإعادة محاولة تلقائية عند توفر الإنترنت"
    )

    fun performVersionCheck() {
        scope.launch {
            isChecking = true
            checkErrorMessage = null
            val result = AppVersionManager.checkVersion()
            isChecking = false
            result.fold(
                onSuccess = { res ->
                    versionInfoState = res
                    lastCheckedTime = "منذ لحظات"
                    if (res.isUpdateAvailable) {
                        Toast.makeText(context, "يتوفر تحديث جديد: الإصدار ${res.currentVersionName} 🚀", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "أنت تستخدم أحدث إصدار معتمد من تـنـويـر ✓", Toast.LENGTH_SHORT).show()
                    }
                },
                onFailure = { err ->
                    checkErrorMessage = err.localizedMessage ?: "تعذر الوصول إلى خادم التحديثات"
                    lastCheckedTime = "فشل الاتصال"
                    Toast.makeText(context, "تعذر فحص التحديثات: تأكد من اتصال الإنترنت", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        performVersionCheck()
    }

    val versionInfo = versionInfoState
    val isUpdateAvailable = versionInfo?.isUpdateAvailable == true
    val changelogToDisplay = versionInfo?.changelog?.takeIf { it.isNotEmpty() } ?: defaultChangelog

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
    ) {
        // App Version Hero Card
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MidnightSurface,
                borderColor = CyanAccent.copy(alpha = 0.4f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(CyanAccent.copy(alpha = 0.2f), ElectricBlue.copy(alpha = 0.3f))
                                )
                            )
                            .border(2.dp, CyanAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✨", fontSize = 34.sp)
                    }

                    Text(
                        text = "منظومة تـنـويـر التعليمية",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassPill(
                            text = "الإصدار $installedVersionName",
                            color = CyanAccent,
                            bgColor = CyanGlow
                        )
                        GlassPill(
                            text = "Build $installedVersionCode",
                            color = TextSecondary,
                            bgColor = GlassSurface
                        )
                        GlassPill(
                            text = if (isUpdateAvailable) "يتوفر تحديث ⚠️" else "نسخة رسمية ✓",
                            color = if (isUpdateAvailable) WarmAmber else EmeraldGreen,
                            bgColor = if (isUpdateAvailable) WarmAmber.copy(alpha = 0.15f) else EmeraldGreen.copy(alpha = 0.15f)
                        )
                    }

                    Text(
                        text = "المنصة التعاونية المدرسية الذكية للطلاب والمعلمين وإدارات المدارس",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassButton(
                            text = if (isChecking) "جاري الفحص من السيرفر..." else "فحص التحديثات الآن 🔄",
                            icon = Icons.Default.Refresh,
                            onClick = { performVersionCheck() },
                            modifier = Modifier.weight(1f),
                            enabled = !isChecking
                        )
                    }

                    Text(
                        text = "آخر فحص: ${lastCheckedTime ?: "الآن"}",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }

        // Status Card / Update Notice
        item {
            if (isUpdateAvailable && versionInfo != null) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MidnightSurface,
                    borderColor = if (versionInfo.isMandatory) Color(0xFFFF5252).copy(alpha = 0.8f) else WarmAmber.copy(alpha = 0.7f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background((if (versionInfo.isMandatory) Color(0xFFFF5252) else WarmAmber).copy(alpha = 0.15f))
                                    .border(1.dp, if (versionInfo.isMandatory) Color(0xFFFF5252) else WarmAmber, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = if (versionInfo.isMandatory) Color(0xFFFF5252) else WarmAmber,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "يتوفر إصدار جديد (${versionInfo.currentVersionName})",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (versionInfo.isMandatory) Color(0xFFFF5252) else WarmAmber
                                    )
                                    GlassPill(
                                        text = versionInfo.source.label,
                                        color = CyanAccent,
                                        bgColor = CyanGlow.copy(alpha = 0.2f)
                                    )
                                }
                                Text(
                                    text = "تاريخ الإطلاق: ${versionInfo.releaseDate ?: "معتمد"} • إصدار جهازك: v${versionInfo.installedVersionName}",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        if (versionInfo.isMandatory) {
                            Text(
                                text = "⚠️ هذا التحديث إلزامي لضمان توافق البيانات وأمان الحساب المدرسي.",
                                fontSize = 12.sp,
                                color = Color(0xFFFF8A80),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Direct APK Download Option if available
                        if (!versionInfo.directApkUrl.isNullOrBlank()) {
                            GlassButton(
                                text = "تنزيل وتثبيت التحديث (APK) مباشرة 📥",
                                icon = Icons.Default.Download,
                                color = EmeraldGreen,
                                onClick = {
                                    AppVersionManager.startNativeApkDownload(
                                        context,
                                        versionInfo.directApkUrl,
                                        versionInfo.currentVersionName
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Honest Web Page button
                        GlassButton(
                            text = "الانتقال إلى صفحة الإصدار في المتصفح 🌐",
                            icon = Icons.Default.OpenInBrowser,
                            color = WarmAmber,
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(versionInfo.webpageUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "تعذر فتح المتصفح", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else if (checkErrorMessage != null) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurface,
                    borderColor = Color(0xFFFF5252).copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF5252).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFFFF5252), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(24.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "وضع عدم الاتصال / تعذر فحص الخادم",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = checkErrorMessage ?: "يرجى التحقق من الشبكة لمعرفة آخر الإصدارات",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurface,
                    borderColor = EmeraldGreen.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(EmeraldGreen.copy(alpha = 0.15f))
                                .border(1.dp, EmeraldGreen, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(24.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "أنت تستخدم أحدث إصدار معتمد من تـنـويـر ✓",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "النسخة الحالية $installedVersionName مثبتة ومحدثة بالكامل",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Changelog Section
        item {
            Text(
                text = "سجل التحديثات والميزات الجديدة في الإصدار ${versionInfo?.currentVersionName ?: installedVersionName}:",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = CyanAccent,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(changelogToDisplay) { change ->
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MidnightSurface.copy(alpha = 0.7f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = change,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // System Compatibility Card
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MidnightSurface,
                borderColor = GlassBorderSubtle
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "📱 معلومات بيئة التشغيل والتوافق المعتمدة:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text("• متوافق مع كافة أجهزة Android بدءاً من Android 6.0 (API 23+) فما فوق", fontSize = 12.sp, color = TextSecondary)
                    Text("• واجهات مستخدم حديثة مبنية بتقنية Jetpack Compose و Material 3", fontSize = 12.sp, color = TextSecondary)
                    Text("• قاعدة بيانات Room المحلية عالية الأداء والمعزولة أمنيًا مع دعم كامل لنمط Offline-First", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}
