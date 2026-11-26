package com.magd.tanweer.ui.screens.pdf

import androidx.compose.ui.graphics.Color

/** Immutable brush settings captured once when a drawing gesture begins. */
data class DrawingStrokeStyle(
    val color: Color,
    val strokeWidth: Float,
    val isHighlighter: Boolean
)

/** Converts the current toolbar selection into the exact style persisted for a stroke. */
object DrawingStrokeStylePolicy {
    fun capture(
        currentPenColor: Color,
        currentPenWidth: Float,
        isHighlighter: Boolean
    ): DrawingStrokeStyle = DrawingStrokeStyle(
        color = if (isHighlighter) currentPenColor.copy(alpha = 0.38f) else currentPenColor,
        strokeWidth = if (isHighlighter) currentPenWidth * 2.6f else currentPenWidth,
        isHighlighter = isHighlighter
    )
}
