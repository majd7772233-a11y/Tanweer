package com.magd.tanweer.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.magd.tanweer.data.model.ContentItem
import com.magd.tanweer.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Smart Notebook Transcription Mode (وضع نقل الدرس للدفتر)
 * Allows students who missed a lesson or need to catch up on their notebooks to:
 * 1. View high-resolution board/chalkboard captures with contrast enhancement.
 * 2. Zoom & inspect handwritten blackboard text clearly.
 * 3. Track page-by-page transcription progress ("تم نقل هذه الصفحة للدفتر").
 * 4. Cache photos locally on the device for completely offline study.
 * 5. Quick-copy key notes and lesson metadata.
 */
@Composable
fun NotebookTranscriptionDialog(
    content: ContentItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val mediaList = content.media
    var currentPageIndex by remember { mutableIntStateOf(0) }
    val currentMedia = mediaList.getOrNull(currentPageIndex)

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        if (scale > 1f) {
            offsetX += offsetChange.x
            offsetY += offsetChange.y
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }

    // Reset zoom on page change
    LaunchedEffect(currentPageIndex) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    // Load persistent notebook record for this lesson
    val initialRecord = remember(content.id) {
        com.magd.tanweer.util.StudentNotebookManager.getLessonRecord(context, content.id)
    }

    // Transcription completion tracking (persisted to device storage)
    val completedPages = remember(content.id) {
        mutableStateMapOf<Int, Boolean>().apply {
            initialRecord?.completedPages?.forEach { put(it, true) }
        }
    }
    val isCurrentPageCompleted = completedPages[currentPageIndex] == true

    // Contrast enhancement mode (Normal vs High Contrast for blackboard readability)
    var isHighContrast by remember { mutableStateOf(false) }

    // Offline caching state
    var isSavingOffline by remember { mutableStateOf(false) }
    var isDownloadingAll by remember { mutableStateOf(false) }
    var refreshLocalTrigger by remember { mutableIntStateOf(0) }

    val localBoardFile = remember(content.id, currentPageIndex, refreshLocalTrigger) {
        File(com.magd.tanweer.util.StudentNotebookManager.getOfflineDir(context), "board_${content.id}_${currentPageIndex}.jpg")
    }
    val isLocallySaved = localBoardFile.exists() && localBoardFile.length() > 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MidnightBackground.copy(alpha = 0.96f))
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MidnightSurface)
                    .border(1.dp, GlassBorderSubtle, RoundedCornerShape(20.dp))
                    .padding(14.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("✍️ وضع نقل الدرس للدفتر", fontSize = 16.sp, fontWeight = FontWeight.Black, color = CyanAccent)
                            GlassPill(
                                text = if (isLocallySaved) "محلي على جهازك ✓" else "متاح سحابياً ☁️",
                                color = if (isLocallySaved) EmeraldGreen else WarmAmber,
                                bgColor = if (isLocallySaved) EmeraldGreen.copy(alpha = 0.2f) else WarmAmber.copy(alpha = 0.2f)
                            )
                        }
                        Text(
                            text = "${content.subjectName} • ${content.title} (${content.studyDate})",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GlassSurface)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Board Viewer Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                        .transformable(state = transformState),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentMedia != null) {
                        val colorFilter = if (isHighContrast) {
                            // High contrast color matrix for blackboard chalk legibility
                            ColorFilter.colorMatrix(
                                ColorMatrix(
                                    floatArrayOf(
                                        1.5f, 0f, 0f, 0f, -30f,
                                        0f, 1.5f, 0f, 0f, -30f,
                                        0f, 0f, 1.5f, 0f, -30f,
                                        0f, 0f, 0f, 1f, 0f
                                    )
                                )
                            )
                        } else null

                        val imageModel = if (localBoardFile.exists() && localBoardFile.length() > 0) localBoardFile else currentMedia.url

                        AsyncImage(
                            model = imageModel,
                            contentDescription = "لوحة السبورة صفحة ${currentPageIndex + 1}",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                ),
                            contentScale = ContentScale.Fit,
                            colorFilter = colorFilter
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("📝 لا تتوفر لوحات مصورة لهذا الدرس", fontSize = 14.sp, color = TextSecondary)
                            Text("يمكنك نسخ ملخص الدرس المكتوب بالأسفل مباشرة إلى دفترك.", fontSize = 11.sp, color = TextSecondary)
                        }
                    }

                    // Floating Controls on Viewer
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // High Contrast Toggle
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isHighContrast) CyanAccent else GlassSurface.copy(alpha = 0.8f))
                                .clickable { isHighContrast = !isHighContrast }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = if (isHighContrast) "تباين عالٍ 👁️" else "تباين عادي",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isHighContrast) TextOnAccent else TextPrimary
                            )
                        }

                        // Reset Zoom Button
                        if (scale > 1f) {
                            Box(
                                modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(GlassSurface.copy(alpha = 0.8f))
                                .clickable {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text("إعادة الحجم 🔍", fontSize = 10.sp, color = TextPrimary)
                            }
                        }
                    }

                    // Page Badge
                    if (mediaList.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MidnightSurface.copy(alpha = 0.85f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "اللوحة ${currentPageIndex + 1} من ${mediaList.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Notebook Catch-up Checklist & Progress
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCurrentPageCompleted) EmeraldGreen.copy(alpha = 0.15f) else GlassSurface)
                        .border(
                            1.dp,
                            if (isCurrentPageCompleted) EmeraldGreen.copy(alpha = 0.5f) else GlassBorderSubtle,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            val nextState = !isCurrentPageCompleted
                            completedPages[currentPageIndex] = nextState
                            com.magd.tanweer.util.StudentNotebookManager.markPageCompleted(context, content, currentPageIndex, nextState)
                            com.magd.tanweer.util.TanweerHapticManager.performHaptic(context, true, if (nextState) com.magd.tanweer.util.TanweerHapticManager.HapticType.SUCCESS else com.magd.tanweer.util.TanweerHapticManager.HapticType.CLICK)
                            if (nextState) {
                                val label = if (mediaList.isNotEmpty()) "اللوحة ${currentPageIndex + 1}" else "ملخص الدرس"
                                Toast.makeText(context, "أحسنت! تم حفظ نقل $label إلى دفترك ✓", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(
                            checked = isCurrentPageCompleted,
                            onCheckedChange = { checked ->
                                completedPages[currentPageIndex] = checked
                                com.magd.tanweer.util.StudentNotebookManager.markPageCompleted(context, content, currentPageIndex, checked)
                                com.magd.tanweer.util.TanweerHapticManager.performHaptic(context, true, if (checked) com.magd.tanweer.util.TanweerHapticManager.HapticType.SUCCESS else com.magd.tanweer.util.TanweerHapticManager.HapticType.CLICK)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = EmeraldGreen,
                                uncheckedColor = TextSecondary
                            )
                        )
                        Column {
                            val statusTitle = if (mediaList.isNotEmpty()) {
                                if (isCurrentPageCompleted) "تم نقل هذه اللوحة إلى الدفتر المدرسي ✓" else "انقر هنا لتأكيد نقل هذه اللوحة إلى دفترك"
                            } else {
                                if (isCurrentPageCompleted) "تم تدوين ملخص الدرس في دفترك المدرسي ✓" else "انقر هنا لتأكيد تدوين ملخص الدرس في دفترك"
                            }
                            Text(
                                text = statusTitle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrentPageCompleted) EmeraldGreen else TextPrimary
                            )
                            val totalCompleted = completedPages.values.count { it }
                            val progressSubtitle = if (mediaList.isNotEmpty()) {
                                "إنجاز النقل للدفتر: $totalCompleted من ${mediaList.size} لوحات مكتملة"
                            } else {
                                if (isCurrentPageCompleted) "تم النقل للدفتر بنجاح (1 من 1)" else "بانتظار النقل للدفتر (0 من 1)"
                            }
                            Text(
                                text = progressSubtitle,
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Action Tools: Offline caching, batch download, quick copy, and peer sharing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Paging Buttons
                    if (mediaList.size > 1) {
                        IconButton(
                            onClick = {
                                if (currentPageIndex > 0) currentPageIndex--
                            },
                            enabled = currentPageIndex > 0,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (currentPageIndex > 0) GlassSurface else MidnightBackground)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "السابق", tint = if (currentPageIndex > 0) CyanAccent else TextSecondary)
                        }

                        IconButton(
                            onClick = {
                                if (currentPageIndex < mediaList.size - 1) currentPageIndex++
                            },
                            enabled = currentPageIndex < mediaList.size - 1,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (currentPageIndex < mediaList.size - 1) GlassSurface else MidnightBackground)
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "التالي", tint = if (currentPageIndex < mediaList.size - 1) CyanAccent else TextSecondary)
                        }
                    }

                    // Download all boards locally for 100% offline access
                    if (mediaList.isNotEmpty()) {
                        GlassOutlinedButton(
                            text = if (isDownloadingAll) "جاري التحميل..." else if (isLocallySaved) "محفوظ محلياً ✓" else "تنزيل الكل 📥",
                            onClick = {
                                scope.launch {
                                    isDownloadingAll = true
                                    val res = com.magd.tanweer.util.StudentNotebookManager.cacheAllLessonPagesLocally(context, content)
                                    isDownloadingAll = false
                                    refreshLocalTrigger++
                                    res.fold(
                                        onSuccess = { count ->
                                            Toast.makeText(context, "تم حفظ لوحات الدرس في ذاكرة هاتفك لمذاكرتها أوفلاين دون إنترنت ✓", Toast.LENGTH_SHORT).show()
                                        },
                                        onFailure = {
                                            Toast.makeText(context, "تعذر تنزيل الصور، تأكد من اتصال الإنترنت.", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Share Lesson with Classmate
                    GlassButton(
                        text = "مشاركة الدرس 📢",
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipText = buildString {
                                appendLine("📝 درس ناقص تم توثيقه في تطبيق تنوير:")
                                appendLine("المادة: ${content.subjectName}")
                                appendLine("عنوان الدرس: ${content.title}")
                                appendLine("تاريخ الدرس: ${content.studyDate}")
                                appendLine("عدد لوحات الشرح: ${content.media.size} لوحات")
                                if (!content.description.isNullOrBlank()) {
                                    appendLine()
                                    appendLine("ملخص الشرح:")
                                    appendLine(content.description)
                                }
                                appendLine("افتح تطبيق تنوير لمطالعة اللوحات ونقلها لدفترك!")
                            }
                            clipboard.setPrimaryClip(ClipData.newPlainText("Shared Tanweer Lesson", clipText))
                            Toast.makeText(context, "تم نسخ تفاصيل الدرس لمشاركته مع زملائك في الشعبة 📋", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
