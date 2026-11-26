package com.magd.tanweer.ui.screens

import com.magd.tanweer.R


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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.magd.tanweer.util.CameraCaptureFileStore
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

    var selectedSubjectId by rememberSaveable {
        mutableStateOf(initialSubjectId ?: gradeSubjects.firstOrNull()?.id ?: "math")
    }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var studyDate by rememberSaveable { mutableStateOf(selectedDate) }
    var globalAutoEnhance by rememberSaveable { mutableStateOf(true) }

    val scannedPagesSaver = remember(context) {
        scanPageListSaver<LessonScanPage>(
            toRecord = { page ->
                ScanPageDraftRecord(
                    id = page.id,
                    sourceUri = page.uri?.toString(),
                    processedFilePath = page.processedResult?.file?.absolutePath,
                    rotation = page.rotation,
                    autoEnhance = page.autoEnhance,
                    blackAndWhite = page.blackAndWhite,
                    isProcessing = page.isProcessing
                )
            },
            fromRecord = { record, processed, preview, shouldResume ->
                LessonScanPage(
                    id = record.id,
                    uri = record.sourceUri?.let(Uri::parse),
                    rawBitmap = null,
                    processedResult = processed,
                    displayBitmap = preview,
                    rotation = record.rotation,
                    autoEnhance = record.autoEnhance,
                    blackAndWhite = record.blackAndWhite,
                    isProcessing = shouldResume
                )
            }
        )
    }
    var scannedPages by rememberSaveable(stateSaver = scannedPagesSaver) {
        mutableStateOf<List<LessonScanPage>>(emptyList())
    }
    var isUploading by remember { mutableStateOf(false) }
    var pendingCameraCapturePath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraCapturePageId by rememberSaveable { mutableStateOf<String?>(null) }
    val processingPageIds = remember { mutableSetOf<String>() }

    // Persist source media before processing so a recreated Activity can resume this page.
    fun processPageItem(page: LessonScanPage, onUpdated: (LessonScanPage) -> Unit) {
        scope.launch {
            val sourceUri = ImageProcessingUtils.persistDraftSource(
                context = context,
                pageId = page.id,
                sourceUri = page.uri,
                rawBitmap = page.rawBitmap
            )
            if (sourceUri == null) {
                onUpdated(page.copy(isProcessing = false))
                Toast.makeText(context, context.getString(R.string.scan_draft_save_failed), Toast.LENGTH_LONG).show()
                return@launch
            }

            val sourcePage = page.copy(uri = sourceUri, rawBitmap = null, isProcessing = true)
            onUpdated(sourcePage)

            val result = ImageProcessingUtils.loadAndProcessImage(
                context = context,
                uri = sourceUri,
                rawBitmap = null,
                autoEnhance = page.autoEnhance,
                blackAndWhite = page.blackAndWhite,
                rotationDegrees = page.rotation
            )
            val preview = result?.file?.let {
                ImageProcessingUtils.decodeProcessedPreview(it.absolutePath)
            }
            onUpdated(
                sourcePage.copy(
                    processedResult = result,
                    displayBitmap = preview,
                    isProcessing = false
                )
            )
        }
    }

    LaunchedEffect(scannedPages.filter {
        it.isProcessing && it.processedResult == null
    }.map { it.id }) {
        scannedPages.filter { it.isProcessing && it.processedResult == null }.forEach { page ->
            if (processingPageIds.add(page.id)) {
                processPageItem(page) { updated ->
                    if (scannedPages.any { it.id == updated.id }) {
                        scannedPages = scannedPages.map { if (it.id == updated.id) updated else it }
                    } else {
                        // The user removed this page while its background processing was
                        // still running. Delete any output produced after the removal.
                        ImageProcessingUtils.deleteDraftPage(
                            context = context,
                            sourceUri = updated.uri,
                            processedFilePath = updated.processedResult?.file?.absolutePath
                        )
                    }
                    if (!updated.isProcessing) processingPageIds.remove(page.id)
                }
            }
        }
    }

    fun discardPendingCameraCapture() {
        CameraCaptureFileStore.delete(context, pendingCameraCapturePath)
        pendingCameraCapturePath = null
        pendingCameraCapturePageId = null
    }

    fun discardDraftSources() {
        discardPendingCameraCapture()
        scannedPages.forEach { page ->
            ImageProcessingUtils.deleteDraftSource(context, page.uri)
        }
    }

    fun discardDraftPages() {
        discardPendingCameraCapture()
        scannedPages.forEach { page ->
            ImageProcessingUtils.deleteDraftPage(
                context = context,
                sourceUri = page.uri,
                processedFilePath = page.processedResult?.file?.absolutePath
            )
        }
    }

    // Photo Picker Activity Launcher (Zero-permission)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 8)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            val newPages = uris.map { uri ->
                LessonScanPage(
                    uri = uri,
                    autoEnhance = globalAutoEnhance,
                    isProcessing = true
                )
            }
            scannedPages = scannedPages + newPages
        }
    }

    // Full-resolution camera capture. The path/id are saveable so an external camera
    // Activity can return safely after this Activity is recreated.
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val capturePath = pendingCameraCapturePath
        val pageId = pendingCameraCapturePageId
        pendingCameraCapturePath = null
        pendingCameraCapturePageId = null

        if (capturePath != null) {
            if (!success || pageId.isNullOrBlank()) {
                CameraCaptureFileStore.delete(context, capturePath)
            } else {
                scope.launch {
                    val sourceUri = CameraCaptureFileStore.persistToDraft(
                        context = context,
                        pageId = pageId,
                        capturePath = capturePath
                    )
                    if (sourceUri == null) {
                        Toast.makeText(context, context.getString(R.string.scan_draft_save_failed), Toast.LENGTH_LONG).show()
                    } else {
                        scannedPages = scannedPages + LessonScanPage(
                            id = pageId,
                            uri = sourceUri,
                            rawBitmap = null,
                            autoEnhance = globalAutoEnhance,
                            isProcessing = true
                        )
                    }
                }
            }
        }
    }

    fun launchCameraCapture() {
        if (pendingCameraCapturePath != null) return
        val pageId = UUID.randomUUID().toString()
        val target = runCatching { CameraCaptureFileStore.create(context, pageId) }
            .getOrElse {
                Toast.makeText(context, context.getString(R.string.scan_draft_save_failed), Toast.LENGTH_LONG).show()
                return
            }
        pendingCameraCapturePath = target.file.absolutePath
        pendingCameraCapturePageId = pageId
        cameraLauncher.launch(target.contentUri)
    }

    // Permission launcher for Camera
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCameraCapture()
        } else {
            Toast.makeText(context, "يرجى منح إذن الكاميرا لتوثيق صفحات الدرس", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = { if (!isUploading) { discardDraftPages(); onDismiss() } },
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
                                onClick = { if (!isUploading) { discardDraftPages(); onDismiss() } },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(GlassSurface)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextPrimary)
                            }
                            Text(
                                text = "توثيق وإضافة درس رسمي 📷",
                                style = AppTypography.CardTitle.copy(color = CyanAccent)
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
                            onClick = { if (!isUploading) { discardDraftPages(); onDismiss() } },
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
                                            val outcome = result.getOrNull()
                                            if (outcome?.isSynced == true) {
                                                Toast.makeText(context, "تم توثيق ونشر الدرس على الخادم بنجاح 🚀", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, outcome?.message ?: "تم حفظ الدرس محلياً، وجارٍ جدولة النشر تلقائياً فور توفر الاتصال ⏳", Toast.LENGTH_LONG).show()
                                            }
                                            discardDraftSources()
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
                                style = AppTypography.Hint,
                                color = TextSecondary
                            )
                            Text(
                                text = TanweerViewModel.getFormattedArabicDate(studyDate),
                                style = AppTypography.Button,
                                color = CyanAccent
                            )
                        }
                        Text(
                            text = "حفظ تلقائي في الجدول والشعبة",
                            style = AppTypography.Hint,
                            color = TextMuted
                        )
                    }
                }

                // Subject Selector
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("المادة الدراسية:", style = AppTypography.FieldLabel, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
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
                                    style = AppTypography.Chip,
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
                                style = AppTypography.CardTitle,
                                color = TextPrimary
                            )
                            if (scannedPages.isNotEmpty()) {
                                Text(
                                    text = "إجمالي: ${scannedPages.size} صفحات",
                                    style = AppTypography.Caption,
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
                                Text("📷 تصوير سبورة", style = AppTypography.Button)
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
                                Text("🖼️ من المعرض", style = AppTypography.Button)
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
                                                    style = AppTypography.Caption.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        ImageProcessingUtils.deleteDraftPage(
                                                            context = context,
                                                            sourceUri = page.uri,
                                                            processedFilePath = page.processedResult?.file?.absolutePath
                                                        )
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
                                style = AppTypography.Hint.copy(color = TextMuted, lineHeight = tanweerLineHeight(16f))
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
                                Text("التحسين التلقائي للسبورة والتباين", style = AppTypography.CaptionNormal.copy(color = TextPrimary))
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
