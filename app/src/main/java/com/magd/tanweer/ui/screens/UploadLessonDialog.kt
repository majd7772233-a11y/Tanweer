package com.magd.tanweer.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*
import com.magd.tanweer.util.ImageProcessingUtils
import com.magd.tanweer.util.ProcessedPageResult
import kotlinx.coroutines.launch
import java.util.UUID

data class LessonScanPage(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri? = null,
    val rawBitmap: Bitmap? = null,
    val processedResult: ProcessedPageResult? = null,
    val displayBitmap: Bitmap? = null,
    val rotation: Float = 0f,
    val autoEnhance: Boolean = true,
    val blackAndWhite: Boolean = false,
    val isProcessing: Boolean = false
)

@Composable
fun UploadLessonDialog(
    viewModel: TanweerViewModel,
    initialSubjectId: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val gradeSubjects = remember { viewModel.getSubjectsForCurrentGrade() }

    var selectedSubjectId by remember {
        mutableStateOf(initialSubjectId ?: gradeSubjects.firstOrNull()?.id ?: "math")
    }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var studyDate by remember { mutableStateOf(selectedDate) }
    var globalAutoEnhance by remember { mutableStateOf(true) }

    var scannedPages by remember { mutableStateOf<List<LessonScanPage>>(emptyList()) }
    var isUploading by remember { mutableStateOf(false) }

    // Re-process a specific page when filters change
    fun processPageItem(page: LessonScanPage, onUpdated: (LessonScanPage) -> Unit) {
        scope.launch {
            val result = ImageProcessingUtils.loadAndProcessImage(
                context = context,
                uri = page.uri,
                rawBitmap = page.rawBitmap,
                autoEnhance = page.autoEnhance,
                blackAndWhite = page.blackAndWhite,
                rotationDegrees = page.rotation
            )
            val bmp = result?.file?.let {
                android.graphics.BitmapFactory.decodeFile(it.absolutePath)
            }
            onUpdated(
                page.copy(
                    processedResult = result,
                    displayBitmap = bmp,
                    isProcessing = false
                )
            )
        }
    }

    // Photo Picker Activity Launcher (Zero-permission)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 8)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val newPages = uris.map { uri ->
                LessonScanPage(
                    uri = uri,
                    autoEnhance = globalAutoEnhance,
                    isProcessing = true
                )
            }
            scannedPages = scannedPages + newPages
            newPages.forEach { p ->
                processPageItem(p) { updated ->
                    scannedPages = scannedPages.map { if (it.id == updated.id) updated else it }
                }
            }
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val newPage = LessonScanPage(
                rawBitmap = bitmap,
                autoEnhance = globalAutoEnhance,
                isProcessing = true
            )
            scannedPages = scannedPages + newPage
            processPageItem(newPage) { updated ->
                scannedPages = scannedPages.map { if (it.id == updated.id) updated else it }
            }
        }
    }

    // Permission launcher for Camera
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "يرجى منح إذن الكاميرا لتوثيق صفحات الدرس", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MidnightSurface)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { if (!isUploading) onDismiss() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(GlassSurface)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextPrimary)
                            }
                            Text(
                                text = "توثيق وإضافة درس رسمي 📷",
                                color = CyanAccent,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                        }

                        GlassPill(
                            text = if (scannedPages.isEmpty()) "ماسح السبورة الذكي" else "${scannedPages.size} صفحات جاهزة",
                            color = if (scannedPages.isEmpty()) CyanAccent else EmeraldGreen,
                            bgColor = if (scannedPages.isEmpty()) CyanGlow else EmeraldGreen.copy(alpha = 0.15f)
                        )
                    }
                }
            },
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MidnightSurface)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassOutlinedButton(
                            text = "إلغاء",
                            enabled = !isUploading,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        )
                        GlassButton(
                            text = if (isUploading) "جاري النشر..." else "نشر وتوثيق الدرس ✨",
                            enabled = title.isNotBlank() && !isUploading,
                            onClick = {
                                if (title.isNotBlank()) {
                                    isUploading = true
                                    scope.launch {
                                        val validPages = scannedPages.mapNotNull { it.processedResult }
                                        val descText = description.ifBlank {
                                            if (validPages.isNotEmpty()) {
                                                "درس موثق بـ ${validPages.size} صفحات ومحسن للقراءة بجودة عالية"
                                            } else {
                                                "درس موثق ومسجل"
                                            }
                                        }
                                        val result = viewModel.addLessonWithPages(
                                            subjectId = selectedSubjectId,
                                            title = title,
                                            description = descText,
                                            date = studyDate,
                                            pages = validPages
                                        )
                                        isUploading = false
                                        if (result.isSuccess) {
                                            Toast.makeText(context, "تم توثيق ونشر الدرس بنجاح 🚀", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        } else {
                                            val errorMsg = result.exceptionOrNull()?.message ?: "حدث خطأ أثناء نشر الدرس"
                                            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(2f)
                        )
                    }
                }
            },
            containerColor = MidnightBackground
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Lesson Date Banner
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
                        Column {
                            Text(
                                text = "🗓️ تاريخ الحصة الموثقة:",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = TanweerViewModel.getFormattedArabicDate(studyDate),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent
                            )
                        }
                        Text(
                            text = "حفظ تلقائي في الجدول والشعبة",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                // Subject Selector
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("المادة الدراسية:", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(gradeSubjects) { subj ->
                            val isSelected = subj.id == selectedSubjectId
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) CyanGlow else MidnightSurface)
                                    .border(1.dp, if (isSelected) CyanAccent else GlassBorderSubtle, RoundedCornerShape(10.dp))
                                    .clickable { selectedSubjectId = subj.id }
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
                }

                // Lesson Info Fields
                GlassTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "عنوان الدرس أو الموضوع *",
                    placeholder = "مثال: درس تفاعلات الأكسدة والاختزال"
                )

                GlassTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "ملخص أو ملاحظات الدرس (اختياري)",
                    placeholder = "أهم النقاط، القوانين المكتوبة، أو المسائل المحلولة..."
                )

                // Scanner & Photo Capture Hub
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MidnightSurface,
                    borderColor = if (scannedPages.isNotEmpty()) CyanAccent.copy(alpha = 0.4f) else GlassBorderSubtle
                ) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📑 صفحات وسبورة الدرس:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (scannedPages.isNotEmpty()) {
                                Text(
                                    text = "إجمالي: ${scannedPages.size} صفحات",
                                    fontSize = 11.sp,
                                    color = CyanAccent,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Capture & Pick Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Camera Button
                            Button(
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyanAccent,
                                    contentColor = TextOnAccent
                                )
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "تصوير", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("📷 تصوير سبورة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            // Gallery Button
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent)
                            ) {
                                Icon(Icons.Default.Collections, contentDescription = "المعرض", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("🖼️ من المعرض", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Scanned Pages List & Individual Controls
                        if (scannedPages.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(scannedPages, key = { _, page -> page.id }) { index, page ->
                                    Box(
                                        modifier = Modifier
                                            .width(135.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(GlassSurface)
                                            .border(1.dp, if (page.isProcessing) WarmAmber else CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                            .padding(6.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "صفحة ${index + 1}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = CyanAccent
                                                )
                                                IconButton(
                                                    onClick = {
                                                        scannedPages = scannedPages.filter { it.id != page.id }
                                                    },
                                                    modifier = Modifier.size(22.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "حذف", tint = RubyRed, modifier = Modifier.size(14.dp))
                                                }
                                            }

                                            // Thumbnail Box
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(110.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MidnightBackground),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (page.isProcessing) {
                                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = CyanAccent, strokeWidth = 2.dp)
                                                } else if (page.displayBitmap != null) {
                                                    Image(
                                                        bitmap = page.displayBitmap.asImageBitmap(),
                                                        contentDescription = "صفحة ${index + 1}",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Icon(Icons.Default.BrokenImage, contentDescription = null, tint = TextMuted)
                                                }
                                            }

                                            // Quick Per-Page Enhancements
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceEvenly,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Rotate 90
                                                IconButton(
                                                    onClick = {
                                                        val newRot = (page.rotation + 90f) % 360f
                                                        val updated = page.copy(rotation = newRot, isProcessing = true)
                                                        scannedPages = scannedPages.map { if (it.id == page.id) updated else it }
                                                        processPageItem(updated) { res ->
                                                            scannedPages = scannedPages.map { if (it.id == res.id) res else it }
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.RotateRight, contentDescription = "تدوير", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                                }

                                                // Toggle Black & White Contrast
                                                IconButton(
                                                    onClick = {
                                                        val newBW = !page.blackAndWhite
                                                        val updated = page.copy(blackAndWhite = newBW, isProcessing = true)
                                                        scannedPages = scannedPages.map { if (it.id == page.id) updated else it }
                                                        processPageItem(updated) { res ->
                                                            scannedPages = scannedPages.map { if (it.id == res.id) res else it }
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Contrast,
                                                        contentDescription = "تباين عالي",
                                                        tint = if (page.blackAndWhite) CyanAccent else TextSecondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "💡 يمكنك تصوير السبورة مباشرة أو إرفاق صور متعددة وسيتم تحسين الإضاءة وتوضيح الخطوط آلياً.",
                                fontSize = 11.sp,
                                color = TextMuted,
                                lineHeight = 16.sp
                            )
                        }

                        // Global Auto-Enhance Setting
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(GlassSurface)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("التحسين التلقائي للسبورة والتباين", fontSize = 11.sp, color = TextPrimary)
                            }
                            Switch(
                                checked = globalAutoEnhance,
                                onCheckedChange = { checked ->
                                    globalAutoEnhance = checked
                                    scannedPages = scannedPages.map { p ->
                                        val updated = p.copy(autoEnhance = checked, isProcessing = true)
                                        processPageItem(updated) { res ->
                                            scannedPages = scannedPages.map { if (it.id == res.id) res else it }
                                        }
                                        updated
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = CyanAccent)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
