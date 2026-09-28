package com.example.util

data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val detectedLanguage: String = "AR" // "AR" or "EN"
)

object SmartNameValidator {
    // Pure Arabic letters & diacritics
    private val arabicCharRegex = Regex("^[\\u0621-\\u064A\\u0671-\\u06D3]+$")
    // Pure English/Latin letters
    private val englishCharRegex = Regex("^[a-zA-Z]+$")
    // Forbidden characters: numbers, symbols, emojis, mathematical/box drawing characters, etc.
    private val forbiddenCharsRegex = Regex("[0-9\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}\\p{Punct}□♡£■€¡¤{}<>\\[\\]_+=|\\\\/@#$%^&*~`!?,:;\"']")

    fun validate(fullName: String): ValidationResult {
        val trimmed = fullName.trim().replace("\\s+".toRegex(), " ")
        if (trimmed.isEmpty()) {
            return ValidationResult(false, "يرجى كتابة الاسم الثلاثي (عربي أو إنجليزي)")
        }

        // Check for forbidden symbols/numbers/emojis
        if (forbiddenCharsRegex.containsMatchIn(trimmed)) {
            return ValidationResult(
                false,
                "الاسم يحتوي على رموز أو أرقام أو أشكال غير مسموح بها. يجب أن يتكون من حروف فقط"
            )
        }

        // Split into name segments
        val parts = trimmed.split(" ").filter { it.isNotBlank() }

        if (parts.size < 3) {
            return ValidationResult(
                false,
                "يجب أن يكون الاسم ثلاثياً على الأقل: الاسم الأول، اسم الأب، واسم العائلة (أدخلت ${parts.size} فقط)"
            )
        }

        if (parts.size > 6) {
            return ValidationResult(
                false,
                "الاسم طويل جداً، يرجى الاكتفاء بالاسم الثلاثي أو الرباعي"
            )
        }

        var arabicCount = 0
        var englishCount = 0

        for (part in parts) {
            val isArabic = arabicCharRegex.matches(part)
            val isEnglish = englishCharRegex.matches(part)

            if (!isArabic && !isEnglish) {
                return ValidationResult(
                    false,
                    "المقطع «$part» يحتوي على حروف مختلطة أو غير صالحة"
                )
            }

            if (isArabic) arabicCount++
            if (isEnglish) englishCount++

            // Check minimum length for each segment
            if (part.length < 2) {
                return ValidationResult(
                    false,
                    "المقطع «$part» قصير جداً (حرف واحد). كل اسم يجب أن يتكون من حرفين على الأقل"
                )
            }

            if (part.length > 25) {
                return ValidationResult(
                    false,
                    "المقطع «$part» طويل جداً بشكل غير طبيعي"
                )
            }

            // Check for repetitive characters (e.g. "aaaaaa", "مممممم")
            if (hasAbnormalRepetition(part)) {
                return ValidationResult(
                    false,
                    "المقطع «$part» يحتوي على تكرار غير طبيعي للحروف"
                )
            }
        }

        // Enforce script consistency across the full name (all Arabic or all English)
        if (arabicCount > 0 && englishCount > 0) {
            return ValidationResult(
                false,
                "يرجى كتابة الاسم كاملاً باللغة العربية أو كاملاً باللغة الإنجليزية، دون خلط اللغتين"
            )
        }

        val lang = if (arabicCount > 0) "AR" else "EN"
        return ValidationResult(true, null, detectedLanguage = lang)
    }

    private fun hasAbnormalRepetition(word: String): Boolean {
        if (word.length < 4) return false
        var repeatCount = 1
        for (i in 1 until word.length) {
            if (word[i].lowercaseChar() == word[i - 1].lowercaseChar()) {
                repeatCount++
                if (repeatCount >= 4) return true
            } else {
                repeatCount = 1
            }
        }
        return false
    }
}

// Backward-compatibility alias
object ArabicNameValidator {
    fun validate(fullName: String): ValidationResult = SmartNameValidator.validate(fullName)
}

data class PhoneValidationResult(
    val isValid: Boolean,
    val formattedDisplay: String,
    val localDigits: String,
    val fullE164: String,
    val errorMessage: String? = null
)

object YemenPhoneValidator {
    /**
     * Strictly validates Yemeni mobile numbers:
     * - Fixed +967 prefix.
     * - Exactly 9 digits local number.
     * - Must start with 7 (e.g. 77, 78, 73, 71, 70, 79).
     */
    fun formatAndValidate(input: String): PhoneValidationResult {
        // Extract raw digits
        var rawDigits = input.replace(Regex("[^0-9]"), "")

        // Strip any leading 00967, +967, or 967 if pasted
        if (rawDigits.startsWith("00967")) {
            rawDigits = rawDigits.substring(5)
        } else if (rawDigits.startsWith("967")) {
            rawDigits = rawDigits.substring(3)
        } else if (rawDigits.startsWith("0") && rawDigits.length > 1) {
            // Strip leading zero if typed
            rawDigits = rawDigits.substring(1)
        }

        // Limit to 9 digits maximum
        val localDigits = rawDigits.take(9)

        // Build formatted display: 7XX XXX XXX
        val formatted = buildString {
            if (localDigits.isNotEmpty()) {
                val p1 = localDigits.take(3)
                append(p1)
                if (localDigits.length > 3) {
                    append(" ")
                    val p2 = localDigits.drop(3).take(3)
                    append(p2)
                    if (localDigits.length > 6) {
                        append(" ")
                        val p3 = localDigits.drop(6).take(3)
                        append(p3)
                    }
                }
            }
        }

        val fullE164 = "+967$localDigits"

        if (localDigits.isEmpty()) {
            return PhoneValidationResult(
                isValid = false,
                formattedDisplay = "",
                localDigits = "",
                fullE164 = "+967",
                errorMessage = "يرجى إدخال رقم الهاتف المكون من 9 أرقام"
            )
        }

        // Validate starts with 7
        if (!localDigits.startsWith("7")) {
            return PhoneValidationResult(
                isValid = false,
                formattedDisplay = formatted,
                localDigits = localDigits,
                fullE164 = fullE164,
                errorMessage = "رقم الهاتف اليمني يجب أن يبدأ دائماً بالرقم 7 (مثل 77 أو 73 أو 71 أو 78 أو 70)"
            )
        }

        if (localDigits.length < 9) {
            return PhoneValidationResult(
                isValid = false,
                formattedDisplay = formatted,
                localDigits = localDigits,
                fullE164 = fullE164,
                errorMessage = "أدخلت ${localDigits.length} أرقام من أصل 9 أرقام مطلوبة"
            )
        }

        return PhoneValidationResult(
            isValid = true,
            formattedDisplay = formatted,
            localDigits = localDigits,
            fullE164 = fullE164,
            errorMessage = null
        )
    }
}

enum class PasswordStrengthLevel(val label: String, val scorePercent: Float, val colorHex: Long) {
    VERY_WEAK("ضعيفة جداً (غير مقبولة)", 0.15f, 0xFFFF3366),
    WEAK("ضعيفة", 0.35f, 0xFFFF9100),
    MEDIUM("متوسطة ومقبولة", 0.65f, 0xFFFFD54F),
    STRONG("قوية ومحمية", 0.85f, 0xFF00E676),
    LEGENDARY("أسطورية فائقة الأمان 🛡️", 1.0f, 0xFF00E5FF)
}

data class PasswordAnalysisResult(
    val level: PasswordStrengthLevel,
    val score: Float, // 0.0 to 1.0
    val hasMinLength: Boolean, // >= 8
    val hasLetters: Boolean,
    val hasNumbers: Boolean,
    val hasSymbols: Boolean,
    val hasMixedCase: Boolean,
    val isAcceptable: Boolean,
    val feedbackMessage: String
)

object PasswordStrengthAnalyzer {
    // Common weak patterns
    private val weakSequences = listOf("123456", "12345678", "password", "qwerty", "admin", "112233", "777777")

    fun analyze(password: String): PasswordAnalysisResult {
        val trimmed = password.trim()
        val length = trimmed.length

        val hasMinLength = length >= 8
        val hasLetters = trimmed.any { it.isLetter() }
        val hasNumbers = trimmed.any { it.isDigit() }
        val hasSymbols = trimmed.any { !it.isLetterOrDigit() && !it.isWhitespace() }
        val hasMixedCase = trimmed.any { it.isUpperCase() } && trimmed.any { it.isLowerCase() }

        if (trimmed.isEmpty()) {
            return PasswordAnalysisResult(
                level = PasswordStrengthLevel.VERY_WEAK,
                score = 0f,
                hasMinLength = false,
                hasLetters = false,
                hasNumbers = false,
                hasSymbols = false,
                hasMixedCase = false,
                isAcceptable = false,
                feedbackMessage = "يرجى كتابة كلمة المرور"
            )
        }

        // Check for trivial weak sequence
        val isTrivialSequence = weakSequences.any { trimmed.lowercase().contains(it) }

        var points = 0f

        // Length score
        when {
            length >= 14 -> points += 35f
            length >= 10 -> points += 25f
            length >= 8 -> points += 18f
            length >= 6 -> points += 8f
            else -> points += 2f
        }

        // Variety score
        if (hasLetters) points += 15f
        if (hasNumbers) points += 18f
        if (hasSymbols) points += 22f
        if (hasMixedCase) points += 10f

        if (isTrivialSequence) {
            points = (points * 0.4f).coerceAtMost(25f)
        }

        val scoreNormalized = (points / 100f).coerceIn(0f, 1f)

        val level = when {
            scoreNormalized < 0.25f || length < 6 -> PasswordStrengthLevel.VERY_WEAK
            scoreNormalized < 0.50f -> PasswordStrengthLevel.WEAK
            scoreNormalized < 0.72f -> PasswordStrengthLevel.MEDIUM
            scoreNormalized < 0.90f -> PasswordStrengthLevel.STRONG
            else -> PasswordStrengthLevel.LEGENDARY
        }

        val isAcceptable = hasMinLength && level != PasswordStrengthLevel.VERY_WEAK && (hasLetters && hasNumbers)

        val feedback = when {
            isTrivialSequence -> "تحتوي كلمة المرور على تسلسل سهل التخمين (مثل 123456). يرجى تعقيدها."
            !hasMinLength -> "كلمة المرور قصيرة (أدخلت $length أحرف). يجب أن تكون 8 أحرف على الأقل."
            !hasLetters -> "يرجى إضافة حروف أبجدية إلى كلمة المرور."
            !hasNumbers -> "يرجى إضافة أرقام لتعزيز حماية كلمة المرور."
            !hasSymbols && scoreNormalized < 0.7f -> "أضف رموزاً خاصة (مثل @, #, $, %) للوصول لقوة فائقة."
            level == PasswordStrengthLevel.MEDIUM -> "كلمة مرور جيدة ومقبولة للنظام."
            level == PasswordStrengthLevel.STRONG -> "كلمة مرور قوية ومحمية بشكل ممتاز! 🔒"
            else -> "كلمة مرور أسطورية فائقة الحماية والتعقيد! 🛡️✨"
        }

        return PasswordAnalysisResult(
            level = level,
            score = scoreNormalized,
            hasMinLength = hasMinLength,
            hasLetters = hasLetters,
            hasNumbers = hasNumbers,
            hasSymbols = hasSymbols,
            hasMixedCase = hasMixedCase,
            isAcceptable = isAcceptable,
            feedbackMessage = feedback
        )
    }
}
