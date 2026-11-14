package com.magd.tanweer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// =========================================================================
// TANWEER CENTRALIZED TYPOGRAPHY SYSTEM
// Rooted on standard base 12sp scale and dynamically responsive
// =========================================================================

object TanweerTypographyTokens {
    // Standard Base Scale Sizes
    val SizeHero = 18.sp
    val SizeHeadline = 16.sp
    val SizeHeader = 16.sp
    val SizeTitle = 15.sp
    val SizeSubTitle = 13.sp
    val SizeBase = 12.sp
    val SizeCaption = 11.sp
    val SizeMeta = 10.sp
    val SizeBadge = 10.sp

    val DisplayHero = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 18.sp,
        lineHeight = 22.sp
    )

    val HeadlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 16.sp,
        lineHeight = 20.sp
    )

    val HeadlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp
    )

    val TitleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 19.sp
    )

    val TitleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    )

    val TitleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 17.sp
    )

    val BodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )

    val BodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )

    val BodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp
    )

    val LabelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )

    val LabelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp
    )

    val LabelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp
    )
}

// Material 3 Typography instance configured with Tanweer's centralized scale
val Typography = Typography(
    displayLarge = TanweerTypographyTokens.DisplayHero,
    headlineLarge = TanweerTypographyTokens.HeadlineLarge,
    headlineMedium = TanweerTypographyTokens.HeadlineMedium,
    titleLarge = TanweerTypographyTokens.TitleLarge,
    titleMedium = TanweerTypographyTokens.TitleMedium,
    titleSmall = TanweerTypographyTokens.TitleSmall,
    bodyLarge = TanweerTypographyTokens.BodyLarge,
    bodyMedium = TanweerTypographyTokens.BodyMedium,
    bodySmall = TanweerTypographyTokens.BodySmall,
    labelLarge = TanweerTypographyTokens.LabelLarge,
    labelMedium = TanweerTypographyTokens.LabelMedium,
    labelSmall = TanweerTypographyTokens.LabelSmall
)

// Semantic Tanweer Typography Hierarchy Accessors for unified design across screens
object AppTypography {
    val Hero: TextStyle get() = TanweerTypographyTokens.DisplayHero
    val SectionHeader: TextStyle get() = TanweerTypographyTokens.HeadlineMedium
    val CardTitle: TextStyle get() = TanweerTypographyTokens.TitleMedium
    val CardSubtitle: TextStyle get() = TanweerTypographyTokens.BodySmall
    val Title: TextStyle get() = TanweerTypographyTokens.TitleSmall
    val Body: TextStyle get() = TanweerTypographyTokens.BodyMedium
    val BodyBold: TextStyle get() = TanweerTypographyTokens.LabelLarge
    val BodyLarge: TextStyle get() = TanweerTypographyTokens.BodyLarge
    val Caption: TextStyle get() = TanweerTypographyTokens.LabelMedium
    val CaptionNormal: TextStyle get() = TanweerTypographyTokens.BodySmall
    val Meta: TextStyle get() = TanweerTypographyTokens.LabelSmall
    val Badge: TextStyle get() = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = TanweerTypographyTokens.SizeBadge, lineHeight = 12.sp)
    val Button: TextStyle get() = TanweerTypographyTokens.LabelLarge
    val Nav: TextStyle get() = TanweerTypographyTokens.LabelMedium
    val FieldLabel: TextStyle get() = TanweerTypographyTokens.BodyMedium
    val Hint: TextStyle get() = TanweerTypographyTokens.BodySmall
    val Chip: TextStyle get() = TanweerTypographyTokens.LabelMedium
}


/**
 * Keeps persisted legacy font settings aligned with the supported 12/14/16sp presets.
 * Old 11sp values fall back to 12sp; old 18sp values fall back to 16sp.
 */
fun normalizeTanweerFontScale(scale: Float): Float {
    val safeScale = if (scale.isFinite()) scale else 1f
    return listOf(1f, 14f / 12f, 16f / 12f)
        .minByOrNull { kotlin.math.abs(it - safeScale) }
        ?: 1f
}

/**
 * Maps screen-specific sizes to Tanweer's shared typography roles.
 * User-selected font size is applied once by TanweerTheme's LocalDensity.
 * Main titles use only a modest 18sp emphasis over the 12/14/16sp content scale.
 */
fun tanweerTextSize(sourceSp: Float): TextUnit = when {
    sourceSp <= 10f -> 10.sp
    sourceSp <= 12.5f -> 12.sp
    sourceSp <= 15.5f -> 14.sp
    sourceSp <= 22.5f -> 16.sp
    else -> 18.sp
}

fun tanweerLineHeight(sourceSp: Float): TextUnit =
    (tanweerTextSize(sourceSp).value + 3f).sp
