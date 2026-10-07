package com.magd.tanweer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// =========================================================================
// TANWEER CENTRALIZED TYPOGRAPHY SYSTEM
// Rooted on standard base 12sp scale and dynamically responsive
// =========================================================================

object TanweerTypographyTokens {
    val DisplayHero = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        lineHeight = 34.sp
    )

    val HeadlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        lineHeight = 28.sp
    )

    val HeadlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    )

    val TitleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    )

    val TitleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    )

    val TitleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp
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
        fontSize = 13.sp,
        lineHeight = 18.sp
    )

    val BodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )

    val LabelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp
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
