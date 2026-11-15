package com.magd.tanweer.data.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfRenderSizingTest {
    @Test
    fun `thumbnail dimensions remain within target while preserving aspect`() {
        val size = calculatePdfRenderDimensions(595, 842, 240, 340)
        assertTrue(size.width <= 240)
        assertTrue(size.height <= 340)
        assertTrue(size.width > 0 && size.height > 0)
        assertTrue(kotlin.math.abs(size.width.toFloat() / size.height - 595f / 842f) < 0.01f)
    }

    @Test
    fun `wide and tall pages fit their limiting dimension`() {
        val wide = calculatePdfRenderDimensions(1600, 600, 240, 340)
        assertEquals(240, wide.width)
        assertTrue(wide.height <= 340)
        val tall = calculatePdfRenderDimensions(600, 1600, 240, 340)
        assertEquals(340, tall.height)
        assertTrue(tall.width <= 240)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero page dimensions are rejected`() {
        calculatePdfRenderDimensions(0, 800, 240, 340)
    }

    @Test
    fun `content range start is parsed strictly`() {
        assertEquals(2048L, parsePdfContentRangeStart("bytes 2048-4095/8192"))
        assertNull(parsePdfContentRangeStart("items 2048-4095/8192"))
        assertNull(parsePdfContentRangeStart("bytes malformed"))
    }

    @Test
    fun `range response appends only at the requested offset`() {
        assertEquals(2048L, pdfResponseAppendOffset(2048L, 206, 2048L))
        assertEquals(0L, pdfResponseAppendOffset(2048L, 200, null))
        assertNull(pdfResponseAppendOffset(2048L, 206, 1024L))
        assertNull(pdfResponseAppendOffset(0L, 206, 0L))
        assertTrue(shouldResumePdfPartial(2048L, "\"strong-etag\""))
        assertTrue(!shouldResumePdfPartial(2048L, null))
    }
}
