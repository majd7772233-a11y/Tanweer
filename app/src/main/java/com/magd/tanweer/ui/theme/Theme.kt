package com.magd.tanweer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val TanweerDarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = TextOnAccent,
    primaryContainer = Color(0xFF004D5A),
    onPrimaryContainer = CyanAccent,
    secondary = ElectricBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF0D3273),
    onSecondaryContainer = Color(0xFFBFD7FF),
    tertiary = WarmAmber,
    onTertiary = TextOnAccent,
    tertiaryContainer = Color(0xFF593E00),
    onTertiaryContainer = WarmAmber,
    error = RubyRed,
    onError = Color.White,
    background = MidnightBackground,
    onBackground = TextPrimary,
    surface = MidnightSurface,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF162238),
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder,
    outlineVariant = GlassBorderSubtle
)

private val TanweerAmoledColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = TextOnAccent,
    primaryContainer = Color(0xFF00363F),
    onPrimaryContainer = CyanAccent,
    secondary = ElectricBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF071F47),
    onSecondaryContainer = Color(0xFFBFD7FF),
    tertiary = WarmAmber,
    onTertiary = TextOnAccent,
    tertiaryContainer = Color(0xFF3B2900),
    onTertiaryContainer = WarmAmber,
    error = RubyRed,
    onError = Color.White,
    background = Color.Black,
    onBackground = TextPrimary,
    surface = Color(0xFF080808),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF101010),
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF222222),
    outlineVariant = Color(0xFF151515)
)

@Composable
fun TanweerTheme(
    fontScale: Float = 1.0f,
    themeMode: String = "DARK",
    content: @Composable () -> Unit
) {
    val currentDensity = LocalDensity.current
    val customDensity = Density(
        density = currentDensity.density,
        fontScale = fontScale
    )

    val colorScheme = if (themeMode == "AMOLED") TanweerAmoledColorScheme else TanweerDarkColorScheme

    CompositionLocalProvider(
        LocalDensity provides customDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// Backward compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    TanweerTheme(content = content)
}
