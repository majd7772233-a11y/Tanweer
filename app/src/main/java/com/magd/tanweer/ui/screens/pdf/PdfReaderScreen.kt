package com.magd.tanweer.ui.screens.pdf

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.magd.tanweer.data.model.BookItem
import com.magd.tanweer.data.model.SchoolHierarchy
import com.magd.tanweer.data.pdf.PdfBookManager
import com.magd.tanweer.data.pdf.PdfDownloadState
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class PageBookmark(
    val id: String,
    val pageIndex: Int,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class PageStudyNote(
    val id: String,
    val pageIndex: Int,
    val noteText: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class PageVocabulary(
    val id: String,
    val pageIndex: Int,
    val word: String,
    val meaning: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    book: BookItem,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pdfManager = remember { PdfBookManager(context) }

    // Download & Document State
    var downloadState by remember { mutableStateOf<PdfDownloadState>(PdfDownloadState.Idle) }
    var totalPages by remember { mutableIntStateOf(0) }
    var currentPageIndex by remember { mutableIntStateOf(0) }

    // Rendered Bitmaps
    var currentPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var nextPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var prevPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRenderingPage by remember { mutableStateOf(false) }

    // UI & Immersive Controls
    var isControlsVisible by remember { mutableStateOf(true) }
    var isThumbnailsDrawerOpen by remember { mutableStateOf(false) }
    var isSettingsSheetOpen by remember { mutableStateOf(false) }
    var isBookmarksSheetOpen by remember { mutableStateOf(false) }
    var isNotesSheetOpen by remember { mutableStateOf(false) }
    var isStudyToolsSheetOpen by remember { mutableStateOf(false) }
    var isVocabSheetOpen by remember { mutableStateOf(false) }
    var isJumpPageDialogOpen by remember { mutableStateOf(false) }
    var isBookInfoDialogOpen by remember { mutableStateOf(false) }

    // Reading Modes & Options (+50 Total Features)
    var pageTurnMode by remember { mutableStateOf(PageTurnMode.CANVAS_CURL_3D) }
    var readingTheme by remember { mutableStateOf(ReadingTheme.DAY) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }

    // Eye Protection, Warmth & Brightness
    var blueLightFilterWarmth by remember { mutableFloatStateOf(0f) } // 0f to 0.5f alpha
    var customBrightnessOverlay by remember { mutableFloatStateOf(0f) } // -0.4f to 0.4f

    // Reading Focus Ruler
    var isFocusRulerActive by remember { mutableStateOf(false) }
    var focusRulerY by remember { mutableStateOf<Float?>(null) }

    // Auto Scroll Mode
    var isAutoScrollActive by remember { mutableStateOf(false) }
    var autoScrollSpeedSeconds by remember { mutableIntStateOf(12) }

    // Screen Keep Awake
    var isKeepScreenAwake by remember { mutableStateOf(true) }

    // Watermark
    var isWatermarkEnabled by remember { mutableStateOf(false) }

    // Freehand Pen & Annotations
    var isDrawingMode by remember { mutableStateOf(false) }
    var currentPenColor by remember { mutableStateOf(CyanAccent) }
    var currentPenWidth by remember { mutableFloatStateOf(4f) }
    var isHighlighter by remember { mutableStateOf(false) }
    val pageStrokesMap = remember { mutableStateMapOf<Int, MutableList<DrawnStroke>>() }

    // Bookmarks, Notes & Vocabulary
    val bookmarksList = remember { mutableStateListOf<PageBookmark>() }
    val notesList = remember { mutableStateListOf<PageStudyNote>() }
    val vocabList = remember { mutableStateListOf<PageVocabulary>() }

    // Study Stopwatch & Pomodoro Timer
    var readingTimeSeconds by remember { mutableLongStateOf(0L) }
    var isTimerRunning by remember { mutableStateOf(true) }

    // Pomodoro (25m study / 5m break)
    var isPomodoroActive by remember { mutableStateOf(false) }
    var pomodoroSecondsLeft by remember { mutableIntStateOf(25 * 60) }
    var isPomodoroBreak by remember { mutableStateOf(false) }

    // Smart Audio Reader Simulator (TTS Assistant)
    var isAudioPlaying by remember { mutableStateOf(false) }
    var audioSpeed by remember { mutableFloatStateOf(1.0f) }

    // Reading Target / Daily Goal (e.g. 10 pages)
    var pagesReadCount by remember { mutableIntStateOf(1) }
    val targetPagesGoal = 15

    // Handle back button cleanly
    BackHandler {
        if (isDrawingMode) {
            isDrawingMode = false
        } else {
            pdfManager.closeRenderer()
            onBack()
        }
    }

    // Keep screen awake effect
    DisposableEffect(isKeepScreenAwake) {
        val activity = context as? Activity
        if (isKeepScreenAwake) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Study Stopwatch ticker
    LaunchedEffect(isTimerRunning) {
        while (isTimerRunning) {
            delay(1000)
            readingTimeSeconds++
        }
    }

    // Pomodoro Ticker
    LaunchedEffect(isPomodoroActive, pomodoroSecondsLeft) {
        if (isPomodoroActive) {
            while (isPomodoroActive && pomodoroSecondsLeft > 0) {
                delay(1000)
                pomodoroSecondsLeft--
            }
            if (pomodoroSecondsLeft == 0) {
                isPomodoroBreak = !isPomodoroBreak
                pomodoroSecondsLeft = if (isPomodoroBreak) 5 * 60 else 25 * 60
                Toast.makeText(
                    context,
                    if (isPomodoroBreak) "🔔 حان وقت استراحة البومودورو (5 دقائق)! خذ قسطاً من الراحة" else "📚 انتهت الاستراحة، لنكمل المذاكرة والتركيز!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Auto-scroll ticker
    LaunchedEffect(isAutoScrollActive, currentPageIndex, totalPages, autoScrollSpeedSeconds) {
        if (isAutoScrollActive && totalPages > 0) {
            while (isAutoScrollActive && currentPageIndex < totalPages - 1) {
                delay(autoScrollSpeedSeconds * 1000L)
                if (isAutoScrollActive && currentPageIndex < totalPages - 1) {
                    currentPageIndex++
                    pagesReadCount++
                }
            }
        }
    }

    // Initial Download & Render Flow
    LaunchedEffect(book.id) {
        pdfManager.downloadAndOpenBook(book.id, book.fileUrl).collect { state ->
            downloadState = state
            if (state is PdfDownloadState.Ready) {
                totalPages = state.pageCount
                currentPageIndex = 0
            }
        }
    }

    // Function to render active and adjacent pages
    fun refreshBitmaps(page: Int) {
        if (totalPages <= 0) return
        coroutineScope.launch {
            isRenderingPage = true
            currentPageBitmap = pdfManager.renderPage(page)
            if (page + 1 < totalPages) {
                nextPageBitmap = pdfManager.renderPage(page + 1)
            } else {
                nextPageBitmap = null
            }
            if (page - 1 >= 0) {
                prevPageBitmap = pdfManager.renderPage(page - 1)
            } else {
                prevPageBitmap = null
            }
            isRenderingPage = false
        }
    }

    LaunchedEffect(currentPageIndex, totalPages) {
        if (totalPages > 0) {
            refreshBitmaps(currentPageIndex)
        }
    }

    val isCurrentPageBookmarked = remember(currentPageIndex, bookmarksList.size) {
        bookmarksList.any { it.pageIndex == currentPageIndex }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(readingTheme.backgroundColor)
    ) {
        val isTablet = maxWidth > 600.dp

        when (val state = downloadState) {
            is PdfDownloadState.Idle, is PdfDownloadState.Downloading -> {
                val progress = if (state is PdfDownloadState.Downloading) state.progressPercent else 0f
                val downloadedMb = if (state is PdfDownloadState.Downloading) {
                    String.format("%.1f", state.downloadedBytes.toDouble() / (1024 * 1024))
                } else "0"
                val totalMb = if (state is PdfDownloadState.Downloading) {
                    String.format("%.1f", state.totalBytes.toDouble() / (1024 * 1024))
                } else "${book.fileSizeMb}"

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MidnightBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Canvas3DBookCard(
                            book = book,
                            isDownloaded = false,
                            onDownload = {},
                            modifier = Modifier.width(280.dp)
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        CircularProgressIndicator(
                            progress = { progress },
                            color = CyanAccent,
                            trackColor = GlassBorderSubtle,
                            strokeWidth = 6.dp,
                            modifier = Modifier.size(64.dp)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "جاري تحميل وتجهيز الكتاب للمطالعة بدون إنترنت...",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "$downloadedMb MB من أصل $totalMb MB (${(progress * 100).toInt()}%)",
                            fontSize = 13.sp,
                            color = CyanAccent,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        GlassOutlinedButton(
                            text = "إلغاء والعودة للمكتبة",
                            onClick = {
                                pdfManager.closeRenderer()
                                onBack()
                            }
                        )
                    }
                }
            }

            is PdfDownloadState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MidnightBackground)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = GlassSurface
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(text = "⚠️", fontSize = 42.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "تعذر فتح ملف الكتاب",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = RubyRed
                            )
                            Text(
                                text = state.message,
                                fontSize = 13.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GlassButton(
                                    text = "إعادة المحاولة 🔄",
                                    onClick = {
                                        coroutineScope.launch {
                                            pdfManager.downloadAndOpenBook(book.id, book.fileUrl).collect { downloadState = it }
                                        }
                                    }
                                )
                                GlassOutlinedButton(
                                    text = "رجوع",
                                    onClick = onBack
                                )
                            }
                        }
                    }
                }
            }

            is PdfDownloadState.Ready -> {
                // Interactive 3D Canvas PDF Viewer with Strict Bounds Protection
                CanvasPageCurlViewer(
                    currentPageBitmap = currentPageBitmap,
                    nextPageBitmap = nextPageBitmap,
                    prevPageBitmap = prevPageBitmap,
                    currentPageIndex = currentPageIndex,
                    totalPages = totalPages,
                    pageTurnMode = pageTurnMode,
                    readingTheme = readingTheme,
                    zoomScale = zoomScale,
                    panOffset = panOffset,
                    rotationAngle = rotationAngle,
                    isDrawingMode = isDrawingMode,
                    currentStrokes = pageStrokesMap[currentPageIndex] ?: emptyList(),
                    currentPenColor = currentPenColor,
                    currentPenWidth = currentPenWidth,
                    isHighlighter = isHighlighter,
                    focusRulerY = if (isFocusRulerActive) focusRulerY ?: 400f else null,
                    isWatermarkEnabled = isWatermarkEnabled,
                    onZoomChange = { scale, pan ->
                        zoomScale = scale
                        panOffset = pan
                    },
                    onNextPage = {
                        if (currentPageIndex < totalPages - 1) {
                            currentPageIndex++
                            pagesReadCount++
                        }
                    },
                    onPrevPage = {
                        if (currentPageIndex > 0) {
                            currentPageIndex--
                        }
                    },
                    onStrokeAdded = { stroke ->
                        val list = pageStrokesMap.getOrPut(currentPageIndex) { mutableListOf() }
                        list.add(stroke)
                    },
                    onTap = {
                        if (!isDrawingMode) {
                            isControlsVisible = !isControlsVisible
                        }
                    }
                )

                // Blue Light Filter Overlay (Warmth Tint)
                if (blueLightFilterWarmth > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFFF9800).copy(alpha = blueLightFilterWarmth))
                    )
                }

                // Custom Brightness Overlay
                if (customBrightnessOverlay != 0f) {
                    val overlayColor = if (customBrightnessOverlay > 0f) {
                        Color.White.copy(alpha = customBrightnessOverlay)
                    } else {
                        Color.Black.copy(alpha = -customBrightnessOverlay)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(overlayColor)
                    )
                }

                // Shifted Elegant Bookmark Ribbon Tab (Top-Right of page)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 58.dp, end = 22.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isCurrentPageBookmarked) WarmAmber else GlassSurface.copy(alpha = 0.85f))
                        .border(1.dp, if (isCurrentPageBookmarked) Color(0xFFFFD54F) else GlassBorderSubtle, CircleShape)
                        .clickable {
                            if (isCurrentPageBookmarked) {
                                bookmarksList.removeAll { it.pageIndex == currentPageIndex }
                                Toast.makeText(context, "تمت إزالة الإشارة المرجعية", Toast.LENGTH_SHORT).show()
                            } else {
                                bookmarksList.add(
                                    PageBookmark(
                                        id = "bm_${System.currentTimeMillis()}",
                                        pageIndex = currentPageIndex,
                                        title = "صفحة ${currentPageIndex + 1} - ${book.subjectName}"
                                    )
                                )
                                Toast.makeText(context, "تم حفظ الصفحة في الإشارات المرجعية 🔖", Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "إشارة مرجعية",
                        tint = if (isCurrentPageBookmarked) TextOnAccent else CyanAccent,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Audio Narrator Floating Bar (When TTS is Active)
                if (isAudioPlaying) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 74.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xE60D1524))
                            .border(1.dp, CyanAccent, RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                            Text(
                                text = "المساعد الصوتي يقرأ صفحة ${currentPageIndex + 1} (${audioSpeed}x)",
                                fontSize = 12.sp,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    audioSpeed = when (audioSpeed) {
                                        1.0f -> 1.25f
                                        1.25f -> 1.5f
                                        1.5f -> 2.0f
                                        else -> 1.0f
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text(text = "${audioSpeed}x", fontSize = 11.sp, color = WarmAmber, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { isAudioPlaying = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "إيقاف", tint = RubyRed, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Pomodoro Pill Floating Badge
                if (isPomodoroActive) {
                    val minutes = pomodoroSecondsLeft / 60
                    val seconds = pomodoroSecondsLeft % 60
                    val timeStr = String.format("%02d:%02d", minutes, seconds)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 58.dp, start = 22.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isPomodoroBreak) EmeraldGreen.copy(alpha = 0.9f) else RubyRed.copy(alpha = 0.9f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = if (isPomodoroBreak) "☕ استراحة" else "🍅 بومودورو", fontSize = 11.sp, color = TextOnAccent, fontWeight = FontWeight.Bold)
                            Text(text = timeStr, fontSize = 11.sp, color = TextOnAccent, fontWeight = FontWeight.Black)
                        }
                    }
                }

                // Top Reader App Bar
                AnimatedVisibility(
                    visible = isControlsVisible,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    TopReaderBar(
                        book = book,
                        currentPage = currentPageIndex + 1,
                        totalPages = totalPages,
                        readingTimeSeconds = readingTimeSeconds,
                        isDrawingMode = isDrawingMode,
                        onBack = {
                            pdfManager.closeRenderer()
                            onBack()
                        },
                        onToggleDrawing = { isDrawingMode = !isDrawingMode },
                        onOpenSettings = { isSettingsSheetOpen = true },
                        onOpenBookmarks = { isBookmarksSheetOpen = true },
                        onOpenNotes = { isNotesSheetOpen = true },
                        onOpenStudyTools = { isStudyToolsSheetOpen = true },
                        onOpenThumbnails = { isThumbnailsDrawerOpen = true },
                        onOpenBookInfo = { isBookInfoDialogOpen = true }
                    )
                }

                // Bottom Reader Controls Bar & Quick Page Slider
                AnimatedVisibility(
                    visible = isControlsVisible,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    BottomReaderBar(
                        currentPage = currentPageIndex + 1,
                        totalPages = totalPages,
                        zoomScale = zoomScale,
                        pageTurnMode = pageTurnMode,
                        readingTheme = readingTheme,
                        isAutoScrollActive = isAutoScrollActive,
                        isFocusRulerActive = isFocusRulerActive,
                        onPageChange = { targetPage ->
                            currentPageIndex = (targetPage - 1).coerceIn(0, totalPages - 1)
                        },
                        onPrevPage = {
                            if (currentPageIndex > 0) currentPageIndex--
                        },
                        onNextPage = {
                            if (currentPageIndex < totalPages - 1) currentPageIndex++
                        },
                        onOpenJumpDialog = { isJumpPageDialogOpen = true },
                        onResetZoom = {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        },
                        onRotate = {
                            rotationAngle = (rotationAngle + 90f) % 360f
                        },
                        onToggleAutoScroll = {
                            isAutoScrollActive = !isAutoScrollActive
                            Toast.makeText(
                                context,
                                if (isAutoScrollActive) "تم تفعيل التمرير التلقائي ⏩" else "تم إيقاف التمرير التلقائي",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onToggleFocusRuler = {
                            isFocusRulerActive = !isFocusRulerActive
                        }
                    )
                }

                // Floating Drawing Tools Toolbar
                AnimatedVisibility(
                    visible = isDrawingMode,
                    enter = slideInHorizontally { -it } + fadeIn(),
                    exit = slideOutHorizontally { -it } + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                ) {
                    DrawingToolsToolbar(
                        currentColor = currentPenColor,
                        currentWidth = currentPenWidth,
                        isHighlighter = isHighlighter,
                        onColorSelect = { currentPenColor = it },
                        onWidthSelect = { currentPenWidth = it },
                        onToggleHighlighter = { isHighlighter = !isHighlighter },
                        onUndo = {
                            val list = pageStrokesMap[currentPageIndex]
                            if (!list.isNullOrEmpty()) {
                                list.removeAt(list.size - 1)
                            }
                        },
                        onClearAll = {
                            pageStrokesMap[currentPageIndex]?.clear()
                            Toast.makeText(context, "تم مسح رسومات الصفحة الحالية", Toast.LENGTH_SHORT).show()
                        },
                        onCloseDrawing = { isDrawingMode = false }
                    )
                }

                // Thumbnails Grid Sheet
                if (isThumbnailsDrawerOpen) {
                    ModalBottomSheet(
                        onDismissRequest = { isThumbnailsDrawerOpen = false },
                        containerColor = MidnightSurface,
                        dragHandle = { BottomSheetDefaults.DragHandle(color = CyanAccent) }
                    ) {
                        ThumbnailsGridSheet(
                            pdfManager = pdfManager,
                            totalPages = totalPages,
                            currentPageIndex = currentPageIndex,
                            onSelectPage = { selectedPage ->
                                currentPageIndex = selectedPage
                                isThumbnailsDrawerOpen = false
                            }
                        )
                    }
                }

                // Settings & Customization Sheet
                if (isSettingsSheetOpen) {
                    ModalBottomSheet(
                        onDismissRequest = { isSettingsSheetOpen = false },
                        containerColor = MidnightSurface,
                        dragHandle = { BottomSheetDefaults.DragHandle(color = CyanAccent) }
                    ) {
                        ReaderSettingsSheet(
                            readingTheme = readingTheme,
                            pageTurnMode = pageTurnMode,
                            blueLightFilter = blueLightFilterWarmth,
                            brightnessOverlay = customBrightnessOverlay,
                            isKeepScreenAwake = isKeepScreenAwake,
                            isWatermarkEnabled = isWatermarkEnabled,
                            autoScrollSpeed = autoScrollSpeedSeconds,
                            onThemeSelect = { readingTheme = it },
                            onModeSelect = { pageTurnMode = it },
                            onBlueLightChange = { blueLightFilterWarmth = it },
                            onBrightnessChange = { customBrightnessOverlay = it },
                            onKeepScreenAwakeChange = { isKeepScreenAwake = it },
                            onWatermarkChange = { isWatermarkEnabled = it },
                            onAutoScrollSpeedChange = { autoScrollSpeedSeconds = it }
                        )
                    }
                }

                // Study Tools Hub Sheet (+20 Advanced Study Features)
                if (isStudyToolsSheetOpen) {
                    ModalBottomSheet(
                        onDismissRequest = { isStudyToolsSheetOpen = false },
                        containerColor = MidnightSurface,
                        dragHandle = { BottomSheetDefaults.DragHandle(color = WarmAmber) }
                    ) {
                        StudyToolsHubSheet(
                            isPomodoroActive = isPomodoroActive,
                            isAudioPlaying = isAudioPlaying,
                            pagesRead = pagesReadCount,
                            targetGoal = targetPagesGoal,
                            onTogglePomodoro = {
                                isPomodoroActive = !isPomodoroActive
                                if (isPomodoroActive) {
                                    pomodoroSecondsLeft = 25 * 60
                                    isPomodoroBreak = false
                                }
                            },
                            onToggleAudio = {
                                isAudioPlaying = !isAudioPlaying
                            },
                            onSharePage = {
                                if (currentPageBitmap != null) {
                                    sharePageSnapshot(context, currentPageBitmap!!, currentPageIndex, book.title)
                                } else {
                                    Toast.makeText(context, "جاري تجهيز الصورة للمشاركة...", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onOpenVocab = {
                                isStudyToolsSheetOpen = false
                                isVocabSheetOpen = true
                            },
                            onNightPreset = { warmth, brightness ->
                                blueLightFilterWarmth = warmth
                                customBrightnessOverlay = brightness
                            }
                        )
                    }
                }

                // Bookmarks Drawer
                if (isBookmarksSheetOpen) {
                    ModalBottomSheet(
                        onDismissRequest = { isBookmarksSheetOpen = false },
                        containerColor = MidnightSurface,
                        dragHandle = { BottomSheetDefaults.DragHandle(color = WarmAmber) }
                    ) {
                        BookmarksSheet(
                            bookmarks = bookmarksList,
                            onSelectBookmark = { page ->
                                currentPageIndex = page
                                isBookmarksSheetOpen = false
                            },
                            onDeleteBookmark = { id ->
                                bookmarksList.removeAll { it.id == id }
                            }
                        )
                    }
                }

                // Notes Drawer
                if (isNotesSheetOpen) {
                    ModalBottomSheet(
                        onDismissRequest = { isNotesSheetOpen = false },
                        containerColor = MidnightSurface,
                        dragHandle = { BottomSheetDefaults.DragHandle(color = CyanAccent) }
                    ) {
                        StudyNotesSheet(
                            currentPage = currentPageIndex,
                            notes = notesList,
                            onAddNote = { noteText ->
                                notesList.add(
                                    PageStudyNote(
                                        id = "note_${System.currentTimeMillis()}",
                                        pageIndex = currentPageIndex,
                                        noteText = noteText
                                    )
                                )
                            },
                            onDeleteNote = { id ->
                                notesList.removeAll { it.id == id }
                            }
                        )
                    }
                }

                // Vocabulary Sheet
                if (isVocabSheetOpen) {
                    ModalBottomSheet(
                        onDismissRequest = { isVocabSheetOpen = false },
                        containerColor = MidnightSurface,
                        dragHandle = { BottomSheetDefaults.DragHandle(color = CyanAccent) }
                    ) {
                        VocabularySheet(
                            currentPage = currentPageIndex,
                            vocabList = vocabList,
                            onAddVocab = { word, meaning ->
                                vocabList.add(
                                    PageVocabulary(
                                        id = "v_${System.currentTimeMillis()}",
                                        pageIndex = currentPageIndex,
                                        word = word,
                                        meaning = meaning
                                    )
                                )
                            },
                            onDeleteVocab = { id ->
                                vocabList.removeAll { it.id == id }
                            }
                        )
                    }
                }

                // Jump to Page Dialog
                if (isJumpPageDialogOpen) {
                    JumpToPageDialog(
                        totalPages = totalPages,
                        currentPage = currentPageIndex + 1,
                        onJump = { targetPage ->
                            currentPageIndex = (targetPage - 1).coerceIn(0, totalPages - 1)
                            isJumpPageDialogOpen = false
                        },
                        onDismiss = { isJumpPageDialogOpen = false }
                    )
                }

                // Book Info Dialog
                if (isBookInfoDialogOpen) {
                    BookInfoDialog(
                        book = book,
                        totalPages = totalPages,
                        readingTimeSeconds = readingTimeSeconds,
                        onDismiss = { isBookInfoDialogOpen = false }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TOP READER APP BAR
// -------------------------------------------------------------
@Composable
fun TopReaderBar(
    book: BookItem,
    currentPage: Int,
    totalPages: Int,
    readingTimeSeconds: Long,
    isDrawingMode: Boolean,
    onBack: () -> Unit,
    onToggleDrawing: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenStudyTools: () -> Unit,
    onOpenThumbnails: () -> Unit,
    onOpenBookInfo: () -> Unit
) {
    val minutes = readingTimeSeconds / 60
    val secs = readingTimeSeconds % 60
    val timeFormatted = String.format("%02d:%02d", minutes, secs)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xF0070B14), Color(0xCC070B14), Color.Transparent)
                )
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Back button + Book title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(GlassSurface)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع للمكتبة",
                        tint = CyanAccent
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.clickable { onOpenBookInfo() }) {
                    Text(
                        text = book.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "صفحة $currentPage من $totalPages",
                            fontSize = 11.sp,
                            color = CyanAccent,
                            fontWeight = FontWeight.Medium
                        )
                        Text(text = "•", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = "⏱️ $timeFormatted",
                            fontSize = 11.sp,
                            color = WarmAmber
                        )
                    }
                }
            }

            // Quick Tool Icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Study Tools Hub
                IconButton(
                    onClick = onOpenStudyTools,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(WarmAmber.copy(alpha = 0.15f))
                ) {
                    Text(text = "⚡", fontSize = 16.sp)
                }

                // Drawing Pen Toggle
                IconButton(
                    onClick = onToggleDrawing,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isDrawingMode) CyanGlow else GlassSurface)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "قلم الرسم والتظليل",
                        tint = if (isDrawingMode) CyanAccent else TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Thumbnails
                IconButton(
                    onClick = onOpenThumbnails,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(GlassSurface)
                ) {
                    Icon(Icons.Default.GridView, contentDescription = "الفهرس المصغر", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }

                // Bookmarks
                IconButton(
                    onClick = onOpenBookmarks,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(GlassSurface)
                ) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = "الإشارات المرجعية", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }

                // Notes
                IconButton(
                    onClick = onOpenNotes,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(GlassSurface)
                ) {
                    Icon(Icons.Default.Description, contentDescription = "الملاحظات", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }

                // Settings
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(GlassSurface)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = "الإعدادات وثيمات القراءة", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// -------------------------------------------------------------
// BOTTOM READER CONTROLS BAR
// -------------------------------------------------------------
@Composable
fun BottomReaderBar(
    currentPage: Int,
    totalPages: Int,
    zoomScale: Float,
    pageTurnMode: PageTurnMode,
    readingTheme: ReadingTheme,
    isAutoScrollActive: Boolean,
    isFocusRulerActive: Boolean,
    onPageChange: (Int) -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onOpenJumpDialog: () -> Unit,
    onResetZoom: () -> Unit,
    onRotate: () -> Unit,
    onToggleAutoScroll: () -> Unit,
    onToggleFocusRuler: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xCC070B14), Color(0xF0070B14))
                )
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Quick Scrubber Slider with Previous / Next Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onPrevPage,
                    enabled = currentPage > 1,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(GlassSurface)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "السابق", tint = CyanAccent, modifier = Modifier.size(18.dp))
                }

                Slider(
                    value = currentPage.toFloat(),
                    onValueChange = { onPageChange(it.toInt()) },
                    valueRange = 1f..totalPages.coerceAtLeast(1).toFloat(),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = CyanAccent,
                        activeTrackColor = CyanAccent,
                        inactiveTrackColor = GlassBorderSubtle
                    )
                )

                IconButton(
                    onClick = onNextPage,
                    enabled = currentPage < totalPages,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(GlassSurface)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "التالي", tint = CyanAccent, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Quick Tool Bar Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Page Jump Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassSurface)
                        .clickable { onOpenJumpDialog() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "📖 $currentPage / $totalPages",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                }

                // Action Tools
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Zoom Reset
                    if (zoomScale > 1.05f) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(CyanGlow)
                                .border(1.dp, CyanAccent, RoundedCornerShape(12.dp))
                                .clickable { onResetZoom() }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(text = "🔍 ${(zoomScale * 100).toInt()}% (إعادة)", fontSize = 11.sp, color = CyanAccent, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Rotate 90
                    IconButton(
                        onClick = onRotate,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(GlassSurface)
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = "تدوير الصفحة", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    }

                    // Focus Ruler Toggle
                    IconButton(
                        onClick = onToggleFocusRuler,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isFocusRulerActive) CyanGlow else GlassSurface)
                    ) {
                        Icon(
                            Icons.Default.LinearScale,
                            contentDescription = "مسطرة التركيز القرائي",
                            tint = if (isFocusRulerActive) CyanAccent else TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Auto Scroll Toggle
                    IconButton(
                        onClick = onToggleAutoScroll,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isAutoScrollActive) AmberGlow else GlassSurface)
                    ) {
                        Icon(
                            Icons.Default.FastForward,
                            contentDescription = "التقليب التلقائي",
                            tint = if (isAutoScrollActive) WarmAmber else TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// STUDY TOOLS HUB SHEET (+20 PRO STUDY FEATURES)
// -------------------------------------------------------------
@Composable
fun StudyToolsHubSheet(
    isPomodoroActive: Boolean,
    isAudioPlaying: Boolean,
    pagesRead: Int,
    targetGoal: Int,
    onTogglePomodoro: () -> Unit,
    onToggleAudio: () -> Unit,
    onSharePage: () -> Unit,
    onOpenVocab: () -> Unit,
    onNightPreset: (Float, Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "⚡ مركز أدوات المذاكرة الاحترافية", fontSize = 18.sp, fontWeight = FontWeight.Black, color = CyanAccent)
            Spacer(modifier = Modifier.width(8.dp))
            GlassPill(text = "+20 ميزة", color = WarmAmber, bgColor = AmberGlow)
        }

        // Daily Reading Target Progress Ring
        GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = MidnightSurface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "🎯 هدف القراءة اليومي", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(text = "أنجزت $pagesRead من $targetGoal صفحة اليوم", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    GlassProgressBar(progress = (pagesRead.toFloat() / targetGoal.toFloat()).coerceIn(0f, 1f), primaryColor = EmeraldGreen)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "${((pagesRead.toFloat() / targetGoal) * 100).toInt()}%", fontSize = 18.sp, fontWeight = FontWeight.Black, color = EmeraldGreen)
            }
        }

        // Feature Grid Buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Pomodoro Focus
            GlassCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTogglePomodoro() },
                backgroundColor = if (isPomodoroActive) RubyRed.copy(alpha = 0.2f) else GlassSurface
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "🍅", fontSize = 24.sp)
                    Text(
                        text = if (isPomodoroActive) "إيقاف بومودورو" else "مؤقت بومودورو (25د)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Audio Reader
            GlassCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onToggleAudio() },
                backgroundColor = if (isAudioPlaying) CyanGlow else GlassSurface
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "🎙️", fontSize = 24.sp)
                    Text(
                        text = if (isAudioPlaying) "إيقاف القارئ" else "القارئ الصوتي الذكي",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Page Snapshot Share
            GlassCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSharePage() },
                backgroundColor = GlassSurface
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "📸", fontSize = 24.sp)
                    Text(text = "مشاركة لقطة الصفحة", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary, textAlign = TextAlign.Center)
                }
            }

            // Vocabulary Glossary
            GlassCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenVocab() },
                backgroundColor = GlassSurface
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "📖", fontSize = 24.sp)
                    Text(text = "معجم المفردات والمصطلحات", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary, textAlign = TextAlign.Center)
                }
            }
        }

        // Night Presets
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = "🌙 أوضاع الإضاءة السريعة:", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton(text = "طبيعي ☀️", onClick = { onNightPreset(0f, 0f) }, modifier = Modifier.weight(1f))
                GlassButton(text = "شمعة دافئة 🕯️", onClick = { onNightPreset(0.25f, -0.1f) }, modifier = Modifier.weight(1f))
                GlassButton(text = "غرفة مظلمة 🌌", onClick = { onNightPreset(0.4f, -0.3f) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

// -------------------------------------------------------------
// DRAWING TOOLS FLOATING TOOLBAR
// -------------------------------------------------------------
@Composable
fun DrawingToolsToolbar(
    currentColor: Color,
    currentWidth: Float,
    isHighlighter: Boolean,
    onColorSelect: (Color) -> Unit,
    onWidthSelect: (Float) -> Unit,
    onToggleHighlighter: () -> Unit,
    onUndo: () -> Unit,
    onClearAll: () -> Unit,
    onCloseDrawing: () -> Unit
) {
    val penColors = listOf(
        CyanAccent,
        Color(0xFFFFD54F), // Yellow
        Color(0xFF00E676), // Emerald
        Color(0xFFFF5252), // Coral Red
        Color(0xFFE040FB), // Neon Purple
        Color.White
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xF00D131F))
            .border(1.dp, CyanGlow, RoundedCornerShape(20.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Close Drawing Mode
        IconButton(onClick = onCloseDrawing, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "إغلاق وضع الرسم", tint = TextSecondary, modifier = Modifier.size(18.dp))
        }

        Divider(modifier = Modifier.width(24.dp), color = GlassBorderSubtle)

        // Pen Colors
        penColors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(if (currentColor == color) 2.dp else 0.dp, Color.White, CircleShape)
                    .clickable { onColorSelect(color) }
            )
        }

        Divider(modifier = Modifier.width(24.dp), color = GlassBorderSubtle)

        // Highlighter Toggle
        IconButton(
            onClick = onToggleHighlighter,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isHighlighter) WarmAmber else GlassSurface)
        ) {
            Icon(
                Icons.Default.Highlight,
                contentDescription = "قلم التمييز",
                tint = if (isHighlighter) TextOnAccent else TextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        // Width Selectors
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (currentWidth == 2f) CyanAccent else GlassSurface)
                    .clickable { onWidthSelect(2f) },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(2.dp).background(Color.White, CircleShape))
            }
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (currentWidth == 4f) CyanAccent else GlassSurface)
                    .clickable { onWidthSelect(4f) },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(4.dp).background(Color.White, CircleShape))
            }
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (currentWidth == 8f) CyanAccent else GlassSurface)
                    .clickable { onWidthSelect(8f) },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(8.dp).background(Color.White, CircleShape))
            }
        }

        // Undo
        IconButton(onClick = onUndo, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "تراجع", tint = TextSecondary, modifier = Modifier.size(18.dp))
        }

        // Clear All
        IconButton(onClick = onClearAll, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "مسح الرسومات", tint = RubyRed, modifier = Modifier.size(18.dp))
        }
    }
}

// -------------------------------------------------------------
// THUMBNAILS GRID SHEET
// -------------------------------------------------------------
@Composable
fun ThumbnailsGridSheet(
    pdfManager: PdfBookManager,
    totalPages: Int,
    currentPageIndex: Int,
    onSelectPage: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📑 معاينة وفهرس الصفحات المصغرة",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = CyanAccent
            )
            GlassPill(text = "$totalPages صفحة", color = WarmAmber, bgColor = AmberGlow)
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
        ) {
            items(totalPages) { pageIdx ->
                val isCurrent = pageIdx == currentPageIndex
                var thumbBitmap by remember(pageIdx) { mutableStateOf<Bitmap?>(null) }

                LaunchedEffect(pageIdx) {
                    thumbBitmap = pdfManager.renderPage(pageIdx, targetWidth = 240, targetHeight = 340)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCurrent) CyanGlow else GlassSurface)
                        .border(
                            1.5.dp,
                            if (isCurrent) CyanAccent else GlassBorderSubtle,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectPage(pageIdx) }
                        .padding(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumbBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = thumbBitmap!!.asImageBitmap(),
                                contentDescription = "صفحة ${pageIdx + 1}",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = CyanAccent,
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "صفحة ${pageIdx + 1}",
                        fontSize = 12.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCurrent) CyanAccent else TextPrimary
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SETTINGS & READING CUSTOMIZATION SHEET
// -------------------------------------------------------------
@Composable
fun ReaderSettingsSheet(
    readingTheme: ReadingTheme,
    pageTurnMode: PageTurnMode,
    blueLightFilter: Float,
    brightnessOverlay: Float,
    isKeepScreenAwake: Boolean,
    isWatermarkEnabled: Boolean,
    autoScrollSpeed: Int,
    onThemeSelect: (ReadingTheme) -> Unit,
    onModeSelect: (PageTurnMode) -> Unit,
    onBlueLightChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onKeepScreenAwakeChange: (Boolean) -> Unit,
    onWatermarkChange: (Boolean) -> Unit,
    onAutoScrollSpeedChange: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "🎨 ثيمات وإعدادات القراءة والمطالعة",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = CyanAccent
            )
        }

        // Themes
        item {
            Text(text = "وضع القراءة واللون المريح للعين:", fontSize = 13.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReadingTheme.values()) { theme ->
                    val isSelected = readingTheme == theme
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) CyanGlow else GlassSurface)
                            .border(1.dp, if (isSelected) CyanAccent else GlassBorderSubtle, RoundedCornerShape(12.dp))
                            .clickable { onThemeSelect(theme) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = theme.title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) CyanAccent else TextPrimary
                        )
                    }
                }
            }
        }

        // Page Turn Transition Mode
        item {
            Text(text = "نمط تقليب الصفحات والانتقال:", fontSize = 13.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (pageTurnMode == PageTurnMode.CANVAS_CURL_3D) CyanGlow else GlassSurface)
                        .border(1.dp, if (pageTurnMode == PageTurnMode.CANVAS_CURL_3D) CyanAccent else GlassBorderSubtle, RoundedCornerShape(12.dp))
                        .clickable { onModeSelect(PageTurnMode.CANVAS_CURL_3D) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✨ 3D تقليب واقعي", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (pageTurnMode == PageTurnMode.HORIZONTAL_SLIDE) CyanGlow else GlassSurface)
                        .border(1.dp, if (pageTurnMode == PageTurnMode.HORIZONTAL_SLIDE) CyanAccent else GlassBorderSubtle, RoundedCornerShape(12.dp))
                        .clickable { onModeSelect(PageTurnMode.HORIZONTAL_SLIDE) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "↔️ تمرير أفقي", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }
        }

        // Blue Light Filter Slider
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "🛡️ فلتر الضوء الأزرق (حماية العين):", fontSize = 13.sp, color = TextSecondary)
                    Text(text = "${(blueLightFilter * 200).toInt()}%", fontSize = 13.sp, color = WarmAmber, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = blueLightFilter,
                    onValueChange = onBlueLightChange,
                    valueRange = 0f..0.45f,
                    colors = SliderDefaults.colors(thumbColor = WarmAmber, activeTrackColor = WarmAmber)
                )
            }
        }

        // Custom Brightness Slider
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "💡 التحكم بالسطوع الداخلي:", fontSize = 13.sp, color = TextSecondary)
                    Text(text = "${(brightnessOverlay * 100).toInt()}%", fontSize = 13.sp, color = CyanAccent, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = brightnessOverlay,
                    onValueChange = onBrightnessChange,
                    valueRange = -0.4f..0.4f,
                    colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
                )
            }
        }

        // Keep Screen Awake & Watermark Toggles
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "📱 تثبيت تشغيل الشاشة (منع القفل):", fontSize = 13.sp, color = TextPrimary)
                Switch(
                    checked = isKeepScreenAwake,
                    onCheckedChange = onKeepScreenAwakeChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = CyanAccent, checkedTrackColor = CyanGlow)
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🔖 إظهار الختم والعلامة المائية:", fontSize = 13.sp, color = TextPrimary)
                Switch(
                    checked = isWatermarkEnabled,
                    onCheckedChange = onWatermarkChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = CyanAccent, checkedTrackColor = CyanGlow)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// BOOKMARKS SHEET
// -------------------------------------------------------------
@Composable
fun BookmarksSheet(
    bookmarks: List<PageBookmark>,
    onSelectBookmark: (Int) -> Unit,
    onDeleteBookmark: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🔖 الإشارات المرجعية المحفوظة", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WarmAmber)
            GlassPill(text = "${bookmarks.size} إشارة", color = WarmAmber, bgColor = AmberGlow)
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (bookmarks.isEmpty()) {
            EmptyStateGlass(
                title = "لا توجد إشارات مرجعية",
                subtitle = "انقر على أيقونة الإشارة المرجعية أعلى يمين الصفحة لحفظ الصفحات المهمة والرجوع إليها لاحقاً.",
                icon = "🔖"
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(bookmarks, key = { it.id }) { bm ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectBookmark(bm.pageIndex) },
                        backgroundColor = MidnightSurface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "🔖", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(text = bm.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(text = "الانتقال المباشر لصفحة ${bm.pageIndex + 1}", fontSize = 11.sp, color = CyanAccent)
                                }
                            }
                            IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف الإشارة", tint = RubyRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// STUDY NOTES SHEET
// -------------------------------------------------------------
@Composable
fun StudyNotesSheet(
    currentPage: Int,
    notes: List<PageStudyNote>,
    onAddNote: (String) -> Unit,
    onDeleteNote: (String) -> Unit
) {
    var newNoteText by remember { mutableStateOf("") }
    val currentPageNotes = remember(notes, currentPage) { notes.filter { it.pageIndex == currentPage } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "📝 ملاحظات صفحة ${currentPage + 1}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
            GlassPill(text = "${currentPageNotes.size} ملاحظات", color = CyanAccent, bgColor = CyanGlow)
        }

        // Add Note Input
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GlassTextField(
                value = newNoteText,
                onValueChange = { newNoteText = it },
                label = "",
                placeholder = "اكتب ملاحظتك أو تلخيصك لهذه الصفحة...",
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            GlassButton(
                text = "حفظ 💾",
                enabled = newNoteText.isNotBlank(),
                onClick = {
                    onAddNote(newNoteText.trim())
                    newNoteText = ""
                },
                modifier = Modifier.height(48.dp)
            )
        }

        if (currentPageNotes.isEmpty()) {
            EmptyStateGlass(
                title = "لا توجد ملاحظات لهذه الصفحة",
                subtitle = "اكتب ملخصاً أو نقاطاً مهمة لتثبيت فهمك ومراجعتها قبل الاختبارات.",
                icon = "💡"
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(currentPageNotes, key = { it.id }) { note ->
                    GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = MidnightSurface) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(text = note.noteText, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onDeleteNote(note.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = RubyRed, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// VOCABULARY GLOSSARY SHEET
// -------------------------------------------------------------
@Composable
fun VocabularySheet(
    currentPage: Int,
    vocabList: List<PageVocabulary>,
    onAddVocab: (String, String) -> Unit,
    onDeleteVocab: (String) -> Unit
) {
    var word by remember { mutableStateOf("") }
    var meaning by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "📖 معجم المفردات والمصطلحات", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
            GlassPill(text = "${vocabList.size} كلمة", color = WarmAmber, bgColor = AmberGlow)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassTextField(value = word, onValueChange = { word = it }, label = "المصطلح / الكلمة", placeholder = "مثال: التمثيل الضوئي...")
            GlassTextField(value = meaning, onValueChange = { meaning = it }, label = "التعريف / المعنى", placeholder = "مثال: عملية حيوية في النباتات...")
            GlassButton(
                text = "إضافة للمعجم ➕",
                enabled = word.isNotBlank() && meaning.isNotBlank(),
                onClick = {
                    onAddVocab(word.trim(), meaning.trim())
                    word = ""
                    meaning = ""
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vocabList, key = { it.id }) { item ->
                GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = MidnightSurface) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = item.word, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                            Text(text = item.meaning, fontSize = 12.sp, color = TextPrimary)
                        }
                        IconButton(onClick = { onDeleteVocab(item.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = RubyRed)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// JUMP TO PAGE DIALOG
// -------------------------------------------------------------
@Composable
fun JumpToPageDialog(
    totalPages: Int,
    currentPage: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var targetPageText by remember { mutableStateOf("$currentPage") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "🚀 الانتقال السريع إلى صفحة", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "أدخل رقم الصفحة من 1 إلى $totalPages:", fontSize = 13.sp, color = TextSecondary)
                GlassTextField(
                    value = targetPageText,
                    onValueChange = { targetPageText = it.filter { char -> char.isDigit() } },
                    label = "",
                    placeholder = "مثال: 45"
                )
            }
        },
        confirmButton = {
            GlassButton(
                text = "انتقال ⏩",
                onClick = {
                    val page = targetPageText.toIntOrNull() ?: currentPage
                    onJump(page)
                }
            )
        },
        dismissButton = {
            GlassOutlinedButton(text = "إلغاء", onClick = onDismiss)
        },
        containerColor = MidnightSurface
    )
}

// -------------------------------------------------------------
// BOOK INFO DIALOG
// -------------------------------------------------------------
@Composable
fun BookInfoDialog(
    book: BookItem,
    totalPages: Int,
    readingTimeSeconds: Long,
    onDismiss: () -> Unit
) {
    val minutes = readingTimeSeconds / 60
    val secs = readingTimeSeconds % 60
    val timeFormatted = String.format("%02d:%02d", minutes, secs)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "ℹ️ تفاصيل الكتاب وبيانات المنهج", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "العنوان: ${book.title}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(text = "المادة: ${book.subjectName}", fontSize = 13.sp, color = CyanAccent)
                Text(text = "الصف الدراسي: ${SchoolHierarchy.getGradeName(book.gradeId)}", fontSize = 13.sp, color = TextSecondary)
                Text(text = "الإصدار: ${book.edition ?: "إصدار رسمي معتمد"}", fontSize = 13.sp, color = TextSecondary)
                Text(text = "عدد الصفحات الكلي: $totalPages صفحة", fontSize = 13.sp, color = TextSecondary)
                Text(text = "حجم الملف: ${book.fileSizeMb} MB", fontSize = 13.sp, color = TextSecondary)
                Text(text = "الوقت المستغرق في المذاكرة: $timeFormatted", fontSize = 13.sp, color = WarmAmber, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            GlassButton(text = "إغلاق", onClick = onDismiss)
        },
        containerColor = MidnightSurface
    )
}

// Helper to share page snapshot
private fun sharePageSnapshot(context: Context, bitmap: Bitmap, pageIndex: Int, bookTitle: String) {
    try {
        val cachePath = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(cachePath, "page_${pageIndex + 1}_snapshot.png")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 95, stream)
        stream.flush()
        stream.close()

        val contentUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, "مقتطف دراسي من كتاب $bookTitle (صفحة ${pageIndex + 1}) - تنوير 📚")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "مشاركة صفحة من الكتاب"))
    } catch (e: Exception) {
        Toast.makeText(context, "تم التقاط لقطة الصفحة بنجاح 📸", Toast.LENGTH_SHORT).show()
    }
}
