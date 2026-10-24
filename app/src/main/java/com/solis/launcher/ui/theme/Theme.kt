package com.solis.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.solis.launcher.R

/** The whole palette: black, white and two greys. Nothing else is allowed on screen. */
object SolisColors {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Accent = Color(0xFF555555)
    val Dim = Color(0xFF222222)
}

/**
 * JetBrains Mono (SIL Open Font License, see assets/licenses), bundled so every phone shows the
 * same clean screen-style monospace instead of whatever the OEM ships as monospace.
 */
private val Mono = FontFamily(
    Font(R.font.jetbrains_mono_light, FontWeight.Light),
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private fun mono(size: Int, weight: FontWeight = FontWeight.Normal, letterSpacing: Float = 0f) =
    TextStyle(
        fontFamily = Mono,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * 1.35f).sp,
        letterSpacing = letterSpacing.sp,
    )

/** Every Material text style is monospaced, so no component can sneak in a proportional font. */
private val SolisTypography = Typography(
    displayLarge = mono(72, FontWeight.Light, -2f),
    displayMedium = mono(48, FontWeight.Light, -1f),
    displaySmall = mono(36, FontWeight.Light),
    headlineLarge = mono(32),
    headlineMedium = mono(28),
    headlineSmall = mono(24),
    titleLarge = mono(22),
    titleMedium = mono(18),
    titleSmall = mono(16),
    bodyLarge = mono(16),
    bodyMedium = mono(14),
    bodySmall = mono(12),
    labelLarge = mono(14, letterSpacing = 1f),
    labelMedium = mono(12, letterSpacing = 1f),
    labelSmall = mono(11, letterSpacing = 1f),
)

private val SolisColorScheme = darkColorScheme(
    primary = SolisColors.White,
    onPrimary = SolisColors.Black,
    secondary = SolisColors.Accent,
    onSecondary = SolisColors.White,
    tertiary = SolisColors.Accent,
    background = SolisColors.Black,
    onBackground = SolisColors.White,
    surface = SolisColors.Black,
    onSurface = SolisColors.White,
    surfaceVariant = SolisColors.Black,
    onSurfaceVariant = SolisColors.Accent,
    surfaceContainer = SolisColors.Black,
    surfaceContainerLow = SolisColors.Black,
    surfaceContainerHigh = SolisColors.Black,
    surfaceContainerHighest = SolisColors.Black,
    surfaceContainerLowest = SolisColors.Black,
    outline = SolisColors.Accent,
    outlineVariant = SolisColors.Dim,
    error = SolisColors.White,
    onError = SolisColors.Black,
    scrim = SolisColors.Black,
)

@Composable
fun SolisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SolisColorScheme,
        typography = SolisTypography,
        content = content,
    )
}
