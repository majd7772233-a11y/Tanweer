package com.magd.tanweer.ui.screens.pdf

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingStrokeStylePolicyTest {
    @Test
    fun normalPenCapturesItsCurrentColorWidthAndDisabledHighlighterState() {
        val color = Color(0xFF1976D2)

        val style = DrawingStrokeStylePolicy.capture(
            currentPenColor = color,
            currentPenWidth = 4f,
            isHighlighter = false
        )

        assertEquals(color, style.color)
        assertEquals(4f, style.strokeWidth, 0.001f)
        assertFalse(style.isHighlighter)
    }

    @Test
    fun highlighterCapturesTheExpectedAlphaAndWidthMultiplier() {
        val color = Color(0xFF00BCD4)

        val style = DrawingStrokeStylePolicy.capture(
            currentPenColor = color,
            currentPenWidth = 4f,
            isHighlighter = true
        )

        assertEquals(color, style.color.copy(alpha = 1f))
        assertEquals(0.38f, style.color.alpha, 0.001f)
        assertEquals(10.4f, style.strokeWidth, 0.001f)
        assertTrue(style.isHighlighter)
    }

    @Test
    fun newStrokeUsesLatestToolbarSelectionAfterHighlighterIsTurnedOff() {
        val oldHighlighterStroke = DrawingStrokeStylePolicy.capture(
            currentPenColor = Color.Yellow,
            currentPenWidth = 4f,
            isHighlighter = true
        )
        val nextPenStroke = DrawingStrokeStylePolicy.capture(
            currentPenColor = Color(0xFFAB47BC),
            currentPenWidth = 3f,
            isHighlighter = false
        )

        assertTrue(oldHighlighterStroke.isHighlighter)
        assertEquals(10.4f, oldHighlighterStroke.strokeWidth, 0.001f)
        assertFalse(nextPenStroke.isHighlighter)
        assertEquals(Color(0xFFAB47BC), nextPenStroke.color)
        assertEquals(3f, nextPenStroke.strokeWidth, 0.001f)
    }

    @Test
    fun eachStrokeSnapshotRemainsStableWhenTheNextToolSelectionChanges() {
        val capturedAtStrokeStart = DrawingStrokeStylePolicy.capture(
            currentPenColor = Color(0xFF008577),
            currentPenWidth = 5f,
            isHighlighter = true
        )
        DrawingStrokeStylePolicy.capture(
            currentPenColor = Color.Red,
            currentPenWidth = 2f,
            isHighlighter = false
        )

        assertEquals(Color(0xFF008577), capturedAtStrokeStart.color.copy(alpha = 1f))
        assertEquals(0.38f, capturedAtStrokeStart.color.alpha, 0.001f)
        assertEquals(13f, capturedAtStrokeStart.strokeWidth, 0.001f)
        assertTrue(capturedAtStrokeStart.isHighlighter)
    }
}
