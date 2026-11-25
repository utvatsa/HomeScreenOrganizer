package com.homestudio.local.ui

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Clean, high-contrast Light & Dark design tokens inspired by modern personal finance apps:
 * deep crisp surfaces, subtle 1dp hairline borders, emerald positive accents, and warm alerts.
 */
data class StudioPalette(
    val isDark: Boolean,
    val background: Color,
    val card: Color,
    val cardElevated: Color,
    val cardTint: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val secondaryAccent: Color,
    val secondarySoft: Color,
    val positive: Color,
    val positiveSurface: Color,
    val positiveText: Color,
    val warningSurface: Color,
    val warningText: Color,
    val danger: Color,
    val dangerSoft: Color,
    val wallpaperPreviewBg: Color,
    val wallpaperPreviewBorder: Color,
    val slotCellBg: Color,
)

private val DarkStudioPalette = StudioPalette(
    isDark = true,
    background = Color(0xFF0B0F17),
    card = Color(0xFF131A26),
    cardElevated = Color(0xFF1A2334),
    cardTint = Color(0xFF162033),
    border = Color(0xFF263248),
    textPrimary = Color(0xFFF3F6FC),
    textSecondary = Color(0xFFC2CDDF),
    textMuted = Color(0xFF8C9AB2),
    accent = Color(0xFF2FE6A0),
    accentSoft = Color(0xFF13382D),
    onAccent = Color(0xFF062016),
    secondaryAccent = Color(0xFF82A4FF),
    secondarySoft = Color(0xFF1A2847),
    positive = Color(0xFF34D399),
    positiveSurface = Color(0xFF102A22),
    positiveText = Color(0xFF7BEAB9),
    warningSurface = Color(0xFF2B2113),
    warningText = Color(0xFFF7C26B),
    danger = Color(0xFFFF7A85),
    dangerSoft = Color(0xFF36171C),
    wallpaperPreviewBg = Color(0xFF0D111A),
    wallpaperPreviewBorder = Color(0xFF2A364E),
    slotCellBg = Color(0xFF1B2436),
)

private val LightStudioPalette = StudioPalette(
    isDark = false,
    background = Color(0xFFF5F7FB),
    card = Color(0xFFFFFFFF),
    cardElevated = Color(0xFFF0F4FC),
    cardTint = Color(0xFFEAF2FF),
    border = Color(0xFFDDE4F0),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF3E4C63),
    textMuted = Color(0xFF64748B),
    accent = Color(0xFF0E9F6E),
    accentSoft = Color(0xFFDFF7EE),
    onAccent = Color(0xFFFFFFFF),
    secondaryAccent = Color(0xFF3B5BDB),
    secondarySoft = Color(0xFFE8EEFF),
    positive = Color(0xFF059669),
    positiveSurface = Color(0xFFE6F9F1),
    positiveText = Color(0xFF047857),
    warningSurface = Color(0xFFFFF6E5),
    warningText = Color(0xFFB45309),
    danger = Color(0xFFDC2626),
    dangerSoft = Color(0xFFFEECEE),
    wallpaperPreviewBg = Color(0xFF182235), // Wallpaper/header preview stays realistic dark-backdrop Contrast
    wallpaperPreviewBorder = Color(0xFFCBD5E1),
    slotCellBg = Color(0xFF25324B),
)

val LocalStudioPalette = staticCompositionLocalOf { DarkStudioPalette }

private fun buildColorScheme(p: StudioPalette): ColorScheme = if (p.isDark) {
    darkColorScheme(
        primary = p.accent,
        onPrimary = p.onAccent,
        primaryContainer = p.accentSoft,
        onPrimaryContainer = p.accent,
        secondary = p.secondaryAccent,
        onSecondary = Color(0xFF09142B),
        secondaryContainer = p.secondarySoft,
        onSecondaryContainer = p.secondaryAccent,
        background = p.background,
        onBackground = p.textPrimary,
        surface = p.card,
        onSurface = p.textPrimary,
        surfaceVariant = p.cardElevated,
        onSurfaceVariant = p.textSecondary,
        surfaceContainer = p.card,
        surfaceContainerHigh = p.cardElevated,
        surfaceContainerLow = p.background,
        outline = p.border,
        outlineVariant = p.border.copy(alpha = 0.6f),
        error = p.danger,
        onError = Color.White,
    )
} else {
    lightColorScheme(
        primary = p.accent,
        onPrimary = p.onAccent,
        primaryContainer = p.accentSoft,
        onPrimaryContainer = p.positiveText,
        secondary = p.secondaryAccent,
        onSecondary = Color.White,
        secondaryContainer = p.secondarySoft,
        onSecondaryContainer = p.secondaryAccent,
        background = p.background,
        onBackground = p.textPrimary,
        surface = p.card,
        onSurface = p.textPrimary,
        surfaceVariant = p.cardElevated,
        onSurfaceVariant = p.textSecondary,
        surfaceContainer = p.card,
        surfaceContainerHigh = p.cardElevated,
        surfaceContainerLow = p.background,
        outline = p.border,
        outlineVariant = p.border,
        error = p.danger,
        onError = Color.White,
    )
}

private val StudioTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun HomeOrganizerTheme(
    themeMode: String,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode.lowercase()) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    val targetPalette = if (useDark) DarkStudioPalette else LightStudioPalette
    val bgAnim by animateColorAsState(targetPalette.background, animationSpec = tween(220), label = "bg")
    val cardAnim by animateColorAsState(targetPalette.card, animationSpec = tween(220), label = "card")
    val palette = targetPalette.copy(background = bgAnim, card = cardAnim)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                @Suppress("DEPRECATION")
                window.statusBarColor = palette.background.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = palette.card.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    CompositionLocalProvider(LocalStudioPalette provides palette) {
        MaterialTheme(
            colorScheme = buildColorScheme(palette),
            typography = StudioTypography,
            content = content,
        )
    }
}
