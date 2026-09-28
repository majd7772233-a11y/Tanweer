package com.example.ui.screens.pdf

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import kotlin.math.*

enum class PageTurnMode {
    CANVAS_CURL_3D,
    HORIZONTAL_SLIDE,
    VERTICAL_SCROLL,
    SINGLE_PAGE_SNAP,
    DUAL_PAGE_SPREAD
}

enum class ReadingTheme(
    val title: String,
    val backgroundColor: Color,
    val filterColor: Color,
    val blendMode: BlendMode
) {
    DAY("الصباحي الفاتح", Color(0xFFFBFBFB), Color.Transparent, BlendMode.SrcOver),
    SEPIA("الورق الكلاسيكي", Color(0xFFF7EED9), Color(0x33D7C49E), BlendMode.Multiply),
    NIGHT("الوضع الليلي", Color(0xFF12151C), Color(0x2200E5FF), BlendMode.SrcOver),
    OBSIDIAN("سواد OLED", Color(0xFF000000), Color(0x33000000), BlendMode.SrcOver),
    EMERALD("حماية العين", Color(0xFFE8F5E9), Color(0x2281C784), BlendMode.Multiply),
    INVERTED("التباين المعكوس", Color(0xFF0D1117), Color.White, BlendMode.Difference)
}

data class DrawnStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
    val isHighlighter: Boolean = false
)

@Composable
fun CanvasPageCurlViewer(
    currentPageBitmap: Bitmap?,
    nextPageBitmap: Bitmap?,
    prevPageBitmap: Bitmap?,
    currentPageIndex: Int,
    totalPages: Int,
    pageTurnMode: PageTurnMode,
    readingTheme: ReadingTheme,
    zoomScale: Float,
    panOffset: Offset,
    rotationAngle: Float,
    isDrawingMode: Boolean,
    currentStrokes: List<DrawnStroke>,
    currentPenColor: Color,
    currentPenWidth: Float,
    isHighlighter: Boolean,
    focusRulerY: Float?,
    isWatermarkEnabled: Boolean,
    onZoomChange: (Float, Offset) -> Unit,
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit,
    onStrokeAdded: (DrawnStroke) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    // 3D Curl State (-1f = prev page curled, 0f = neutral, 1f = next page curled)
    val curlProgress = remember { Animatable(0f) }
    var isCurlingForward by remember { mutableStateOf(true) }

    // Live drawing points for active stroke
    val activeStrokePoints = remember { mutableStateListOf<Offset>() }

    val hasPrevPage = currentPageIndex > 0
    val hasNextPage = currentPageIndex < totalPages - 1

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(readingTheme.backgroundColor)
            // Gesture Layer 1: Drawing Mode Handler
            .pointerInput(isDrawingMode) {
                if (isDrawingMode) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            activeStrokePoints.clear()
                            activeStrokePoints.add(startOffset)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            activeStrokePoints.add(change.position)
                        },
                        onDragEnd = {
                            if (activeStrokePoints.isNotEmpty()) {
                                onStrokeAdded(
                                    DrawnStroke(
                                        points = activeStrokePoints.toList(),
                                        color = if (isHighlighter) currentPenColor.copy(alpha = 0.38f) else currentPenColor,
                                        strokeWidth = if (isHighlighter) currentPenWidth * 2.6f else currentPenWidth,
                                        isHighlighter = isHighlighter
                                    )
                                )
                                activeStrokePoints.clear()
                            }
                        },
                        onDragCancel = {
                            activeStrokePoints.clear()
                        }
                    )
                }
            }
            // Gesture Layer 2: Pinch Zoom & Pan when zoomed
            .pointerInput(isDrawingMode, zoomScale) {
                if (!isDrawingMode && zoomScale > 1.05f) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (zoomScale * zoom).coerceIn(1.0f, 5.0f)
                        val maxPanX = (size.width * (newScale - 1f)) / 2f
                        val maxPanY = (size.height * (newScale - 1f)) / 2f
                        val newPan = Offset(
                            x = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                            y = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                        )
                        onZoomChange(newScale, newPan)
                    }
                }
            }
            // Gesture Layer 3: Tap, Double Tap, and Realistic Page Curl Drag (at zoom 1.0x)
            .pointerInput(isDrawingMode, zoomScale, pageTurnMode, currentPageIndex, totalPages) {
                if (!isDrawingMode && zoomScale <= 1.05f) {
                    detectTapGestures(
                        onTap = { offset ->
                            val screenWidth = size.width
                            // Edge tap zones for rapid navigation:
                            // Right 12% = next page in RTL, Left 12% = prev page
                            if (offset.x > screenWidth * 0.88f && hasNextPage) {
                                onNextPage()
                            } else if (offset.x < screenWidth * 0.12f && hasPrevPage) {
                                onPrevPage()
                            } else {
                                onTap()
                            }
                        },
                        onDoubleTap = {
                            if (zoomScale > 1.1f) {
                                onZoomChange(1.0f, Offset.Zero)
                            } else {
                                onZoomChange(2.2f, Offset.Zero)
                            }
                        }
                    )
                }
            }
            .pointerInput(isDrawingMode, zoomScale, pageTurnMode, currentPageIndex, totalPages) {
                if (!isDrawingMode && zoomScale <= 1.05f) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            isCurlingForward = startOffset.x > size.width / 2f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dragDeltaX = dragAmount.x
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            var progressDelta = -(dragDeltaX / width) * 1.55f

                            // STRICT BOUNDARY RULES:
                            // If on First Page, DO NOT allow curling backwards (progressDelta < 0)
                            if (!hasPrevPage && (curlProgress.value + progressDelta) < 0f) {
                                progressDelta = 0f
                            }
                            // If on Last Page, DO NOT allow curling forwards (progressDelta > 0)
                            if (!hasNextPage && (curlProgress.value + progressDelta) > 0f) {
                                progressDelta = 0f
                            }

                            coroutineScope.launch {
                                val currentVal = curlProgress.value
                                val targetVal = (currentVal + progressDelta).coerceIn(
                                    if (hasPrevPage) -1f else 0f,
                                    if (hasNextPage) 1f else 0f
                                )
                                curlProgress.snapTo(targetVal)
                            }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                val currentVal = curlProgress.value
                                if (currentVal > 0.22f && hasNextPage) {
                                    curlProgress.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
                                    onNextPage()
                                    curlProgress.snapTo(0f)
                                } else if (currentVal < -0.22f && hasPrevPage) {
                                    curlProgress.animateTo(-1f, tween(240, easing = FastOutSlowInEasing))
                                    onPrevPage()
                                    curlProgress.snapTo(0f)
                                } else {
                                    curlProgress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                curlProgress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    )
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            if (canvasWidth <= 0 || canvasHeight <= 0) return@Canvas

            // Apply global zoom, pan, and rotation
            drawContext.canvas.save()
            drawContext.canvas.translate(panOffset.x, panOffset.y)
            drawContext.canvas.scale(zoomScale, zoomScale, canvasWidth / 2f, canvasHeight / 2f)
            if (rotationAngle != 0f) {
                drawContext.canvas.rotate(rotationAngle, canvasWidth / 2f, canvasHeight / 2f)
            }

            val progress = curlProgress.value

            if (pageTurnMode == PageTurnMode.CANVAS_CURL_3D && progress != 0f) {
                // Realistic 3D Canvas Page Curl rendering with soft Bezier and lighting
                drawPageCurlEffect(
                    currentPage = currentPageBitmap,
                    nextPage = if (progress > 0) nextPageBitmap else prevPageBitmap,
                    progress = abs(progress),
                    isForward = progress > 0,
                    readingTheme = readingTheme,
                    size = size
                )
            } else if (pageTurnMode == PageTurnMode.DUAL_PAGE_SPREAD && canvasWidth > canvasHeight) {
                // Dual Page Spread for Landscape / Tablets
                drawDualPageSpread(
                    leftPage = prevPageBitmap ?: currentPageBitmap,
                    rightPage = if (prevPageBitmap != null) currentPageBitmap else nextPageBitmap,
                    size = size,
                    readingTheme = readingTheme
                )
            } else {
                // Standard Single Page Display
                if (currentPageBitmap != null && !currentPageBitmap.isRecycled) {
                    drawScaledBitmap(
                        bitmap = currentPageBitmap,
                        containerSize = size,
                        readingTheme = readingTheme
                    )
                }
            }

            // Draw Persistent Annotations & Freehand Drawings for current page
            currentStrokes.forEach { stroke ->
                drawStrokePath(stroke)
            }

            // Draw Currently Active In-Progress Stroke
            if (activeStrokePoints.size > 1) {
                val activeStroke = DrawnStroke(
                    points = activeStrokePoints.toList(),
                    color = if (isHighlighter) currentPenColor.copy(alpha = 0.38f) else currentPenColor,
                    strokeWidth = if (isHighlighter) currentPenWidth * 2.6f else currentPenWidth,
                    isHighlighter = isHighlighter
                )
                drawStrokePath(activeStroke)
            }

            // Draw Reading Focus Ruler / Focus Line
            if (focusRulerY != null) {
                val rulerHeight = 48f
                val clampedY = focusRulerY.coerceIn(rulerHeight / 2f, canvasHeight - rulerHeight / 2f)

                // Dim outer top/bottom regions
                drawRect(
                    color = Color(0x44000000),
                    topLeft = Offset(0f, 0f),
                    size = Size(canvasWidth, clampedY - rulerHeight / 2f)
                )
                drawRect(
                    color = Color(0x44000000),
                    topLeft = Offset(0f, clampedY + rulerHeight / 2f),
                    size = Size(canvasWidth, canvasHeight - (clampedY + rulerHeight / 2f))
                )

                // Glowing reading slit
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x3300E5FF),
                            Color(0x1100E5FF),
                            Color(0x3300E5FF)
                        ),
                        startY = clampedY - rulerHeight / 2f,
                        endY = clampedY + rulerHeight / 2f
                    ),
                    topLeft = Offset(0f, clampedY - rulerHeight / 2f),
                    size = Size(canvasWidth, rulerHeight)
                )
                drawLine(
                    color = Color(0xFF00E5FF),
                    start = Offset(0f, clampedY - rulerHeight / 2f),
                    end = Offset(canvasWidth, clampedY - rulerHeight / 2f),
                    strokeWidth = 2.5f
                )
                drawLine(
                    color = Color(0xFF00E5FF),
                    start = Offset(0f, clampedY + rulerHeight / 2f),
                    end = Offset(canvasWidth, clampedY + rulerHeight / 2f),
                    strokeWidth = 2.5f
                )
            }

            // Watermark / School Seal
            if (isWatermarkEnabled) {
                drawCircle(
                    color = Color(0x0C00E5FF),
                    radius = canvasWidth * 0.32f,
                    center = Offset(canvasWidth / 2f, canvasHeight / 2f)
                )
            }

            drawContext.canvas.restore()
        }
    }
}

private fun DrawScope.drawScaledBitmap(
    bitmap: Bitmap,
    containerSize: Size,
    readingTheme: ReadingTheme
) {
    val srcW = bitmap.width.toFloat()
    val srcH = bitmap.height.toFloat()

    val scale = minOf(containerSize.width / srcW, containerSize.height / srcH)
    val dstW = srcW * scale
    val dstH = srcH * scale

    val left = (containerSize.width - dstW) / 2f
    val top = (containerSize.height - dstH) / 2f

    // Draw realistic page elevation shadow
    drawRoundRect(
        color = Color(0x35000000),
        topLeft = Offset(left + 2f, top + 5f),
        size = Size(dstW, dstH),
        cornerRadius = CornerRadius(6f, 6f)
    )

    drawImage(
        image = bitmap.asImageBitmap(),
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(bitmap.width, bitmap.height),
        dstOffset = IntOffset(left.toInt(), top.toInt()),
        dstSize = IntSize(dstW.toInt(), dstH.toInt())
    )

    // Apply Reading Theme Tint Overlay
    if (readingTheme.filterColor != Color.Transparent) {
        drawRect(
            color = readingTheme.filterColor,
            topLeft = Offset(left, top),
            size = Size(dstW, dstH),
            blendMode = readingTheme.blendMode
        )
    }
}

private fun DrawScope.drawDualPageSpread(
    leftPage: Bitmap?,
    rightPage: Bitmap?,
    size: Size,
    readingTheme: ReadingTheme
) {
    val halfW = size.width / 2f
    val h = size.height

    // Draw left page
    if (leftPage != null && !leftPage.isRecycled) {
        val srcW = leftPage.width.toFloat()
        val srcH = leftPage.height.toFloat()
        val scale = minOf(halfW / srcW, h / srcH)
        val dstW = srcW * scale
        val dstH = srcH * scale
        val left = halfW - dstW
        val top = (h - dstH) / 2f

        drawImage(
            image = leftPage.asImageBitmap(),
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(leftPage.width, leftPage.height),
            dstOffset = IntOffset(left.toInt(), top.toInt()),
            dstSize = IntSize(dstW.toInt(), dstH.toInt())
        )
    }

    // Draw right page
    if (rightPage != null && !rightPage.isRecycled) {
        val srcW = rightPage.width.toFloat()
        val srcH = rightPage.height.toFloat()
        val scale = minOf(halfW / srcW, h / srcH)
        val dstW = srcW * scale
        val dstH = srcH * scale
        val left = halfW
        val top = (h - dstH) / 2f

        drawImage(
            image = rightPage.asImageBitmap(),
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(rightPage.width, rightPage.height),
            dstOffset = IntOffset(left.toInt(), top.toInt()),
            dstSize = IntSize(dstW.toInt(), dstH.toInt())
        )
    }

    // Spine Center Crease Shadow
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                Color(0x33000000),
                Color(0x77000000),
                Color(0x33000000),
                Color.Transparent
            ),
            startX = halfW - 20f,
            endX = halfW + 20f
        ),
        topLeft = Offset(halfW - 20f, 0f),
        size = Size(40f, h)
    )
}

private fun DrawScope.drawPageCurlEffect(
    currentPage: Bitmap?,
    nextPage: Bitmap?,
    progress: Float,
    isForward: Boolean,
    readingTheme: ReadingTheme,
    size: Size
) {
    val w = size.width
    val h = size.height

    // 1. Draw Next / Background Revealed Page Underneath
    if (nextPage != null && !nextPage.isRecycled) {
        drawScaledBitmap(nextPage, size, readingTheme)
    }

    // 2. Draw Current Page with Curling Corner / Fold Clipping
    if (currentPage != null && !currentPage.isRecycled) {
        val curlAmount = progress.coerceIn(0f, 1f)
        val foldX = if (isForward) w * (1f - curlAmount) else w * curlAmount

        // Draw flat remaining part of current page
        val visiblePath = Path().apply {
            if (isForward) {
                moveTo(0f, 0f)
                lineTo(foldX, 0f)
                cubicTo(
                    foldX - 30f * curlAmount, h * 0.3f,
                    foldX - 60f * curlAmount, h * 0.7f,
                    max(0f, foldX - 100f * curlAmount), h
                )
                lineTo(0f, h)
            } else {
                moveTo(w, 0f)
                lineTo(foldX, 0f)
                cubicTo(
                    foldX + 30f * curlAmount, h * 0.3f,
                    foldX + 60f * curlAmount, h * 0.7f,
                    min(w, foldX + 100f * curlAmount), h
                )
                lineTo(w, h)
            }
            close()
        }

        clipPath(visiblePath) {
            drawScaledBitmap(currentPage, size, readingTheme)
        }

        // 3. Draw 3D Fold Crease & Back-of-Page Curl Lighting & Shadow
        val shadowWidth = 70f * (1f - abs(0.5f - curlAmount))
        val shadowBrush = if (isForward) {
            Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0x35000000),
                    Color(0x75000000),
                    Color(0x30FFFFFF),
                    Color.Transparent
                ),
                startX = foldX - shadowWidth,
                endX = foldX + shadowWidth
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0x30FFFFFF),
                    Color(0x75000000),
                    Color(0x35000000),
                    Color.Transparent
                ),
                startX = foldX - shadowWidth,
                endX = foldX + shadowWidth
            )
        }

        drawRect(
            brush = shadowBrush,
            topLeft = Offset(foldX - shadowWidth, 0f),
            size = Size(shadowWidth * 2f, h)
        )
    }
}

private fun DrawScope.drawStrokePath(stroke: DrawnStroke) {
    if (stroke.points.size < 2) return
    val path = Path().apply {
        moveTo(stroke.points[0].x, stroke.points[0].y)
        for (i in 1 until stroke.points.size) {
            lineTo(stroke.points[i].x, stroke.points[i].y)
        }
    }
    drawPath(
        path = path,
        color = stroke.color,
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = stroke.strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}
