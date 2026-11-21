package com.magd.tanweer.util

/** Prevents clearly corrupted PDF extraction from being spoken as page content. */
object PdfSpeechTextQuality {
    fun isSafeForSpeech(text: String): Boolean {
        if (text.isBlank()) return false
        var count = 0
        var questions = 0
        var letters = 0
        var arabicLetters = 0
        var extendedLatinLetters = 0
        var digits = 0
        var mathOperators = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (cp == 0xFFFD || cp in 0x7F..0x9F || (cp < 0x20 && cp != 9 && cp != 10 && cp != 13)) return false
            if (!Character.isWhitespace(cp)) count++
            if (cp == 63) questions++
            if (Character.isDigit(cp)) digits++
            if (cp == 43 || cp == 45 || cp == 8722 || cp == 215 || cp == 247 ||
                cp == 42 || cp == 47 || cp == 61 || cp == 94 || cp == 37 ||
                cp == 8730 || cp == 8721 || cp == 8747 || cp == 8804 || cp == 8805 ||
                cp == 8776
            ) mathOperators++
            if (Character.isLetter(cp)) {
                letters++
                if (cp in 0x0600..0x06FF || cp in 0x0750..0x077F || cp in 0x08A0..0x08FF || cp in 0xFB50..0xFDFF || cp in 0xFE70..0xFEFF) arabicLetters++
                if (cp in 0x00C0..0x024F) extendedLatinLetters++
            }
        }
        if (count < 3) return false
        // A page may contain only a mathematical expression (for example, "2 + 2 = 4").
        // Keep that readable instead of treating the absence of letters as corruption.
        if (letters == 0) return digits >= 2 && mathOperators >= 1
        if (questions >= 4 && questions.toDouble() / count >= 0.06 && extendedLatinLetters >= 2) return false
        if (arabicLetters >= 8 && extendedLatinLetters >= 4 && extendedLatinLetters.toDouble() / letters >= 0.10) return false
        return true
    }
}