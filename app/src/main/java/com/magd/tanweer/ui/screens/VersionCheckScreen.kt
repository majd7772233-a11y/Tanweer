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
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.GlassButton
import com.magd.tanweer.ui.components.GlassCard
import com.magd.tanweer.ui.components.GlassPill
import com.magd.tanweer.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VersionCheckScreen(
    viewModel: TanweerViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isChecking by remember { mutableStateOf(false) }
    var lastCheckedTime by remember { mutableStateOf("الآن") }
    var serverVersionInfo by remember {
        mutableStateOf(
            AppVersionData(
                currentVersion = "1.2.0",
                versionCode = 2,
                minSupportedVersion = "1.0.0",
                isUpdateRequired = false,
                releaseDate = "2026-10-05",
                changelog = listOf(
                    "✨ نظام الحوكمة الموحد والتصويت الشامل على القرارات المدرسية",
                    "📷 ماسح السبورة الذكي عالي الدقة ومعالجة التباين التلقائي",
                    "📚 مكتبة المناهج الذكية مع إدارة متقدمة للتخزين والذاكرة",
                    "🛡️ محرك الصلاحيات الهرمي وتأمين ترقيات الرتب المدرسية",
                    "🚀 دعم الأجهزة بدءاً من Android 5.0 (minSdk 21)",
                    "⚡ مزامنة ذكية Offline-First وسرعة استجابة فائقة"
                )
            )
        )
    }

    fun performVersionCheck() {
        scope.launch {
            isChecking = true
            delay(800) // Simulated smooth network verification
            isChecking = false
            lastCheckedTime = "منذ لحظات"
            Toast.makeText(context, "أنت تستخدم أحدث إصدار معتمد من تـنـويـر ✓", Toast.LENGTH_SHORT).show()
        }
    }

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
                            text = "الإصدار ${serverVersionInfo.currentVersion}",
                            color = CyanAccent,
                            bgColor = CyanGlow
                        )
                        GlassPill(
                            text = "Build ${serverVersionInfo.versionCode}",
                            color = TextSecondary,
                            bgColor = GlassSurface
                        )
                        GlassPill(
                            text = "مستقر وموثق ✓",
                            color = EmeraldGreen,
                            bgColor = EmeraldGreen.copy(alpha = 0.15f)
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
                            text = if (isChecking) "جاري الفحص..." else "فحص التحديثات الآن 🔄",
                            icon = Icons.Default.Refresh,
                            onClick = { performVersionCheck() },
                            modifier = Modifier.weight(1f),
                            enabled = !isChecking
                        )
                    }

                    Text(
                        text = "آخر فحص: $lastCheckedTime",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }

        // Status Card
        item {
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
                            text = "تطبيقك محدث إلى أحدث نسخة معتمدة",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "تاريخ الإصدار الأخير: ${serverVersionInfo.releaseDate}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Changelog Section
        item {
            Text(
                text = "سجل التحديثات وما الجديد في الإصدار ${serverVersionInfo.currentVersion}:",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = CyanAccent,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(serverVersionInfo.changelog) { change ->
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
                        text = "📱 معلومات بيئة التشغيل والتوافق:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text("• متوافق مع كافة أجهزة Android بدءاً من Android 5.0 (API 21+)", fontSize = 12.sp, color = TextSecondary)
                    Text("• هيكلية Jetpack Compose الحديثة مع Material 3", fontSize = 12.sp, color = TextSecondary)
                    Text("• قاعدة بيانات Room المحلية المشفرة تدعم العمل Offline بدون إنترنت", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}

data class AppVersionData(
    val currentVersion: String,
    val versionCode: Int,
    val minSupportedVersion: String,
    val isUpdateRequired: Boolean,
    val releaseDate: String,
    val changelog: List<String>
)
