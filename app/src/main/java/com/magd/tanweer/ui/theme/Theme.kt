package com.magd.tanweer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

@Composable
fun TanweerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TanweerDarkColorScheme,
        typography = Typography,
        content = content
    )
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
