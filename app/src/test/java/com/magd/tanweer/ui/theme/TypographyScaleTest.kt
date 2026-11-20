package com.magd.tanweer.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class TypographyScaleTest {
    @Test
    fun legacyFontScalesNormalizeToTheThreeSupportedPresets() {
        assertEquals(1.0f, normalizeTanweerFontScale(0.9167f), 0.001f)
        assertEquals(1.0f, normalizeTanweerFontScale(1.0f), 0.001f)
        assertEquals(14f / 12f, normalizeTanweerFontScale(1.1667f), 0.001f)
        assertEquals(16f / 12f, normalizeTanweerFontScale(1.3333f), 0.001f)
        assertEquals(16f / 12f, normalizeTanweerFontScale(1.5f), 0.001f)
    }

    @Test
    fun invalidFontScalesFallBackToTheDefault() {
        assertEquals(1.0f, normalizeTanweerFontScale(Float.NaN), 0.001f)
        assertEquals(1.0f, normalizeTanweerFontScale(Float.POSITIVE_INFINITY), 0.001f)
    }
}
