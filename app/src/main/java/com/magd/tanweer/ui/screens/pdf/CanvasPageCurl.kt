package com.magd.tanweer.ui.screens.pdf

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class PageTurnMode {
    CANVAS_CURL_3D,
    HORIZONTAL_SLIDE,
    VERTICAL_CONTINUOUS,
    FLIP_BOOK
}

enum class ReadingTheme(
    val title: String,
    val backgroundColor: Color,
    val textColor: Color
) {
    DAY("الوضع الافتراضي (نهاري)", Color(0xFFFFFFFF), Color(0xFF1E293B)),
    SEPIA("ورق قديم (سيبيا مريح)", Color(0xFFFBF0D9), Color(0xFF5F4B32)),
    DARK("الوضع الليلي (كحلي هادئ)", Color(0xFF0F172A), Color(0xFFF1F5F9)),
    OLED_BLACK("أسود مطلق (توفير بطارية)", Color(0xFF000000), Color(0xFFE2E8F0)),
    WARM_YELLOW("أصفر دافئ (حماية العين)", Color(0xFFFFFBEB), Color(0xFF451A03)),
    FOREST_GREEN("أخضر هادئ (تركيز عميق)", Color(0xFFF0FDF4), Color(0xFF14532D))
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
    onFocusRulerYChange: (Float) -> Unit,
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit,
    onStrokeAdded: (DrawnStroke) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 3D Curl State (-1f = prev page curled, 0f = neutral, 1f = next page curled)
    val curlProgress = remember { Animatable(0f) }
    // Keep gesture processing alive while Compose state changes during a pinch or pan.
    val latestZoomScale by rememberUpdatedState(zoomScale)
    val latestPanOffset by rememberUpdatedState(panOffset)
    val latestFocusRulerY by rememberUpdatedState(focusRulerY)

    // Live drawing points for active stroke
    val activeStrokePoints = remember { mutableStateListOf<Offset>() }

    val hasPrevPage = currentPageIndex > 0
    val hasNextPage = currentPageIndex < totalPages - 1

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(readingTheme.backgroundColor)
            // Gesture Layer 1: Drawing Mode Handler (Active when user taps Pen)
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
            // Gesture Layer 2: One gesture owner for pinch, pan, page curl and taps.
            // Two or more fingers can only zoom; a single finger can only turn or pan.
            .pointerInput(
                isDrawingMode,
                currentPageIndex,
                totalPages,
                pageTurnMode
            ) {
                if (!isDrawingMode) {
                    var lastTapUptime = 0L
                    var lastTapPosition = Offset.Zero

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var previousPosition = down.position
                        var totalDrag = Offset.Zero
                        var didMove = false
                        var didPinch = false
                        var didRulerDrag = latestFocusRulerY != null && kotlin.math.abs(
                            down.position.y - (if (latestFocusRulerY < 0f) size.height / 2f else latestFocusRulerY)
                        ) <= 36f
                        var previousPinchSpan = 0f
                        var previousPinchCentroid = Offset.Zero

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }

                            if (pressed.size >= 2) {
                                didPinch = true
                                didMove = true

                                val centroid = Offset(
                                    x = pressed.sumOf { it.position.x.toDouble() }.div(pressed.size).toFloat(),
                                    y = pressed.sumOf { it.position.y.toDouble() }.div(pressed.size).toFloat()
                                )
                                val span = pressed.map { (it.position - centroid).getDistance() }.average().toFloat()

                                if (previousPinchSpan > 0f && span > 0f) {
                                    val requestedZoom = span / previousPinchSpan
                                    val currentScale = latestZoomScale
                                    val currentPan = latestPanOffset
                                    val newScale = (currentScale * requestedZoom).coerceIn(1f, 5f)
                                    val appliedZoom = newScale / currentScale.coerceAtLeast(1f)
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val focus = centroid - center - currentPan
                                    val focalCorrection = Offset(
                                        x = focus.x * (1f - appliedZoom),
                                        y = focus.y * (1f - appliedZoom)
                                    )
                                    val centroidPan = centroid - previousPinchCentroid
                                    val maxPanX = (size.width * (newScale - 1f)) / 2f
                                    val maxPanY = (size.height * (newScale - 1f)) / 2f
                                    val newPan = if (newScale <= 1.02f) {
                                        Offset.Zero
                                    } else {
                                        Offset(
                                            x = (currentPan.x + centroidPan.x + focalCorrection.x).coerceIn(-maxPanX, maxPanX),
                                            y = (currentPan.y + centroidPan.y + focalCorrection.y).coerceIn(-maxPanY, maxPanY)
                                        )
                                    }
                                    onZoomChange(newScale, newPan)
                                }

                                previousPinchSpan = span
                                previousPinchCentroid = centroid
                                event.changes.forEach { if (it.pressed) it.consume() }
                            } else if (pressed.isEmpty()) {
                                val up = event.changes.firstOrNull { it.id == down.id }
                                if (!didMove && !didPinch) {
                                    val tapTime = up?.uptimeMillis ?: down.uptimeMillis
                                    val tapPosition = up?.position ?: down.position
                                    val isDoubleTap = lastTapUptime > 0L &&
                                        tapTime - lastTapUptime in 1..320 &&
                                        (tapPosition - lastTapPosition).getDistance() <= 56f

                                    if (isDoubleTap) {
                                        if (latestZoomScale > 1.15f) {
                                            onZoomChange(1f, Offset.Zero)
                                        } else {
                                            onZoomChange(2.2f, Offset.Zero)
                                        }
                                        lastTapUptime = 0L
                                    } else {
                                        if (latestZoomScale <= 1.05f) {
                                            if (tapPosition.x > size.width * 0.88f && hasNextPage) {
                                                onNextPage()
                                            } else if (tapPosition.x < size.width * 0.12f && hasPrevPage) {
                                                onPrevPage()
                                            } else {
                                                onTap()
                                            }
                                        } else {
                                            onTap()
                                        }
                                        lastTapUptime = tapTime
                                        lastTapPosition = tapPosition
                                    }
                                } else if (!didPinch && !didRulerDrag && latestZoomScale <= 1.05f) {
                                    val progress = curlProgress.value
                                    if (progress > 0.20f && hasNextPage) {
                                        curlProgress.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
                                        onNextPage()
                                        curlProgress.snapTo(0f)
                                    } else if (progress < -0.20f && hasPrevPage) {
                                        curlProgress.animateTo(-1f, tween(200, easing = FastOutSlowInEasing))
                                        onPrevPage()
                                        curlProgress.snapTo(0f)
                                    } else {
                                        curlProgress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                    }
                                }
                                break
                            } else if (didPinch) {
                                event.changes.forEach { if (it.pressed) it.consume() }
                            } else {
                                val change = pressed.first()
                                val delta = change.position - previousPosition
                                totalDrag += delta

                                if (totalDrag.getDistance() > viewConfiguration.touchSlop) {
                                    didMove = true

                                    if (didRulerDrag) {
                                        val rulerHeight = 65f
                                        onFocusRulerYChange(
                                            change.position.y.coerceIn(rulerHeight / 2f, size.height - rulerHeight / 2f)
                                        )
                                        change.consume()
                                    } else if (latestZoomScale > 1.05f) {
                                        val currentScale = latestZoomScale
                                        val currentPan = latestPanOffset
                                        val maxPanX = (size.width * (currentScale - 1f)) / 2f
                                        val maxPanY = (size.height * (currentScale - 1f)) / 2f
                                        onZoomChange(
                                            currentScale,
                                            Offset(
                                                x = (currentPan.x + delta.x).coerceIn(-maxPanX, maxPanX),
                                                y = (currentPan.y + delta.y).coerceIn(-maxPanY, maxPanY)
                                            )
                                        )
                                        change.consume()
                                    } else if (kotlin.math.abs(totalDrag.x) > kotlin.math.abs(totalDrag.y) * 0.6f) {
                                        val width = size.width.toFloat().coerceAtLeast(1f)
                                        val progressDelta = -(delta.x / width) * 1.55f
                                        val minProgress = if (hasPrevPage) -1f else 0f
                                        val maxProgress = if (hasNextPage) 1f else 0f
                                        val target = (curlProgress.value + progressDelta).coerceIn(minProgress, maxProgress)
                                        curlProgress.snapTo(target)
                                        change.consume()
                                    }
                                }

                                previousPosition = change.position
                            }
                        }
                    }
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
                    size = size,
                    currentStrokes = currentStrokes
                )
            } else {
                // Standard Flat Page Drawing with Theme Filters
                drawStandardPage(
                    bitmap = currentPageBitmap,
                    readingTheme = readingTheme,
                    size = size
                )
                // Draw Freehand Annotations & Drawings on Top of Page
                drawPageStrokes(currentStrokes)
            }

            // Draw Live Active Stroke
            if (activeStrokePoints.size > 1) {
                val livePath = Path().apply {
                    moveTo(activeStrokePoints.first().x, activeStrokePoints.first().y)
                    for (i in 1 until activeStrokePoints.size) {
                        lineTo(activeStrokePoints[i].x, activeStrokePoints[i].y)
                    }
                }
                drawPath(
                    path = livePath,
                    color = if (isHighlighter) currentPenColor.copy(alpha = 0.38f) else currentPenColor,
                    style = Stroke(
                        width = if (isHighlighter) currentPenWidth * 2.6f else currentPenWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // Draw Focus Reading Ruler (if active)
            focusRulerY?.let { rulerY ->
                drawFocusRuler(rulerY = if (rulerY < 0f) size.height / 2f else rulerY, size = size)
            }

            // Draw Watermark (if enabled)
            if (isWatermarkEnabled) {
                drawWatermark("تنوير - نسخة دراسية رسمية", size = size)
            }

            drawContext.canvas.restore()
        }
    }
}

private fun DrawScope.drawStandardPage(
    bitmap: Bitmap?,
    readingTheme: ReadingTheme,
    size: Size
) {
    if (bitmap == null || bitmap.isRecycled) {
        // Draw elegant placeholder skeleton
        drawRect(
            color = readingTheme.backgroundColor,
            size = size
        )
        return
    }

    val imageBitmap = bitmap.asImageBitmap()
    val srcWidth = imageBitmap.width.toFloat()
    val srcHeight = imageBitmap.height.toFloat()

    // Calculate aspect fit inside canvas
    val scaleX = size.width / srcWidth
    val scaleY = size.height / srcHeight
    val scale = minOf(scaleX, scaleY)

    val destWidth = srcWidth * scale
    val destHeight = srcHeight * scale
    val destLeft = (size.width - destWidth) / 2f
    val destTop = (size.height - destHeight) / 2f

    // Background paper fill
    drawRect(
        color = readingTheme.backgroundColor,
        topLeft = Offset(destLeft, destTop),
        size = Size(destWidth, destHeight)
    )

    // Color Filter based on Reading Theme
    val colorFilter = when (readingTheme) {
        ReadingTheme.DAY -> null
        ReadingTheme.SEPIA -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.95f, 0.05f, 0.00f, 0f, 15f,
                    0.00f, 0.88f, 0.05f, 0f, 10f,
                    0.00f, 0.05f, 0.70f, 0f, 0f,
                    0.00f, 0.00f, 0.00f, 1f, 0f
                )
            )
        )
        ReadingTheme.DARK -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -0.85f, 0f, 0f, 0f, 215f,
                    0f, -0.85f, 0f, 0f, 215f,
                    0f, 0f, -0.85f, 0f, 215f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        ReadingTheme.OLED_BLACK -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -0.95f, 0f, 0f, 0f, 235f,
                    0f, -0.95f, 0f, 0f, 235f,
                    0f, 0f, -0.95f, 0f, 235f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        ReadingTheme.WARM_YELLOW -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    1.05f, 0f, 0f, 0f, 20f,
                    0f, 0.98f, 0f, 0f, 15f,
                    0f, 0f, 0.72f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        ReadingTheme.FOREST_GREEN -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.75f, 0f, 0f, 0f, 0f,
                    0f, 0.95f, 0f, 0f, 18f,
                    0f, 0f, 0.75f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    drawImage(
        image = imageBitmap,
        srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
        srcSize = androidx.compose.ui.unit.IntSize(imageBitmap.width, imageBitmap.height),
        dstOffset = androidx.compose.ui.unit.IntOffset(destLeft.toInt(), destTop.toInt()),
        dstSize = androidx.compose.ui.unit.IntSize(destWidth.toInt(), destHeight.toInt()),
        colorFilter = colorFilter
    )
}

/**
 * 3D Page Curl Effect on Canvas with Realistic Bezier Curves, Underneath Peeking, and Spine Shading.
 */
private fun DrawScope.drawPageCurlEffect(
    currentPage: Bitmap?,
    nextPage: Bitmap?,
    progress: Float,
    isForward: Boolean,
    readingTheme: ReadingTheme,
    size: Size,
    currentStrokes: List<DrawnStroke>
) {
    val width = size.width
    val height = size.height

    // 1. Draw Next Page underneath the curled region
    drawStandardPage(
        bitmap = nextPage,
        readingTheme = readingTheme,
        size = size
    )

    if (currentPage == null || currentPage.isRecycled) return

    val imageBitmap = currentPage.asImageBitmap()
    val curlX = if (isForward) {
        width * (1f - progress)
    } else {
        width * progress
    }

    // 2. Draw remaining visible part of current page using clip path
    drawIntoCanvas { canvas ->
        canvas.save()
        val clipPath = Path().apply {
            if (isForward) {
                moveTo(0f, 0f)
                lineTo(curlX, 0f)
                cubicTo(
                    curlX - 40f, height * 0.3f,
                    curlX + 20f, height * 0.7f,
                    curlX, height
                )
                lineTo(0f, height)
                close()
            } else {
                moveTo(curlX, 0f)
                lineTo(width, 0f)
                lineTo(width, height)
                lineTo(curlX, height)
                cubicTo(
                    curlX + 40f, height * 0.7f,
                    curlX - 20f, height * 0.3f,
                    curlX, 0f
                )
                close()
            }
        }
        canvas.clipPath(clipPath)

        drawImage(
            image = imageBitmap,
            dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
            dstSize = androidx.compose.ui.unit.IntSize(width.toInt(), height.toInt())
        )

        // Draw page strokes deformed/clipped synchronously with the curled paper
        drawPageStrokes(currentStrokes)

        // Add soft curl shadow along the fold edge
        val shadowBrush = if (isForward) {
            Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color(0x66000000), Color(0x99000000)),
                startX = (curlX - 80f).coerceAtLeast(0f),
                endX = curlX
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(Color(0x99000000), Color(0x66000000), Color.Transparent),
                startX = curlX,
                endX = (curlX + 80f).coerceAtMost(width)
            )
        }

        drawRect(
            brush = shadowBrush,
            topLeft = Offset(if (isForward) (curlX - 80f).coerceAtLeast(0f) else curlX, 0f),
            size = Size(80f, height)
        )

        canvas.restore()
    }

    // 3. Draw 3D Curling Page Flap (The back / curled cylinder)
    val flapWidth = (width * 0.22f * (1f - abs(progress - 0.5f) * 1.2f)).coerceAtLeast(15f)
    val flapBrush = Brush.horizontalGradient(
        colors = listOf(
            Color(0x33000000),
            Color(0xFFE0E0E0),
            Color(0xFFFAFAFA),
            Color(0x44000000)
        ),
        startX = curlX - flapWidth / 2f,
        endX = curlX + flapWidth / 2f
    )

    val flapPath = Path().apply {
        if (isForward) {
            moveTo(curlX, 0f)
            lineTo(curlX + flapWidth, 0f)
            cubicTo(
                curlX + flapWidth + 30f, height * 0.4f,
                curlX + flapWidth - 20f, height * 0.8f,
                curlX + flapWidth, height
            )
            lineTo(curlX, height)
            cubicTo(
                curlX + 20f, height * 0.7f,
                curlX - 40f, height * 0.3f,
                curlX, 0f
            )
            close()
        } else {
            moveTo(curlX, 0f)
            lineTo(curlX - flapWidth, 0f)
            cubicTo(
                curlX - flapWidth - 30f, height * 0.4f,
                curlX - flapWidth + 20f, height * 0.8f,
                curlX - flapWidth, height
            )
            lineTo(curlX, height)
            cubicTo(
                curlX - 20f, height * 0.7f,
                curlX + 40f, height * 0.3f,
                curlX, 0f
            )
            close()
        }
    }

    drawPath(path = flapPath, brush = flapBrush)
}

private fun DrawScope.drawPageStrokes(strokes: List<DrawnStroke>) {
    for (stroke in strokes) {
        if (stroke.points.size < 2) continue
        val path = Path().apply {
            moveTo(stroke.points.first().x, stroke.points.first().y)
            for (i in 1 until stroke.points.size) {
                lineTo(stroke.points[i].x, stroke.points[i].y)
            }
        }
        drawPath(
            path = path,
            color = stroke.color,
            style = Stroke(
                width = stroke.strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

private fun DrawScope.drawFocusRuler(rulerY: Float, size: Size) {
    val rulerHeight = 65f
    val topDimHeight = (rulerY - rulerHeight / 2f).coerceAtLeast(0f)
    val bottomDimTop = (rulerY + rulerHeight / 2f).coerceAtMost(size.height)
    val bottomDimHeight = (size.height - bottomDimTop).coerceAtLeast(0f)

    // Dim top area
    if (topDimHeight > 0) {
        drawRect(
            color = Color(0x88000000),
            topLeft = Offset.Zero,
            size = Size(size.width, topDimHeight)
        )
    }

    // Highlight band borders
    drawLine(
        color = Color(0xFFFFD54F),
        start = Offset(0f, topDimHeight),
        end = Offset(size.width, topDimHeight),
        strokeWidth = 3f
    )

    drawLine(
        color = Color(0xFFFFD54F),
        start = Offset(0f, bottomDimTop),
        end = Offset(size.width, bottomDimTop),
        strokeWidth = 3f
    )

    // Dim bottom area
    if (bottomDimHeight > 0) {
        drawRect(
            color = Color(0x88000000),
            topLeft = Offset(0f, bottomDimTop),
            size = Size(size.width, bottomDimHeight)
        )
    }
}

private fun DrawScope.drawWatermark(text: String, size: Size) {
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(30, 255, 255, 255)
            textSize = 36f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.nativeCanvas.save()
        canvas.nativeCanvas.rotate(-30f, size.width / 2f, size.height / 2f)
        canvas.nativeCanvas.drawText(text, size.width / 2f, size.height / 2f, paint)
        canvas.nativeCanvas.restore()
    }
}
