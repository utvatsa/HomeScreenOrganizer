package com.homestudio.local.ui

import android.app.Activity
import androidx.annotation.DrawableRes
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
import com.homestudio.local.R

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

data class AppThemeSpec(
    val id: String,
    val numberLabel: String,
    val title: String,
    val subtitle: String,
    @param:DrawableRes val iconMipmapRes: Int,
    val light: StudioPalette,
    val dark: StudioPalette,
    val previewSwatches: List<Color>,
) {
    fun palette(useDark: Boolean): StudioPalette = if (useDark) dark else light
}

private fun makeDarkPalette(
    bg: Long,
    card: Long,
    cardEl: Long,
    cardTint: Long,
    border: Long,
    text1: Long,
    text2: Long,
    muted: Long,
    accent: Long,
    accentSoft: Long,
    onAccent: Long,
    sec: Long,
    secSoft: Long,
) = StudioPalette(
    isDark = true,
    background = Color(bg),
    card = Color(card),
    cardElevated = Color(cardEl),
    cardTint = Color(cardTint),
    border = Color(border),
    textPrimary = Color(text1),
    textSecondary = Color(text2),
    textMuted = Color(muted),
    accent = Color(accent),
    accentSoft = Color(accentSoft),
    onAccent = Color(onAccent),
    secondaryAccent = Color(sec),
    secondarySoft = Color(secSoft),
    positive = Color(0xFF4CAF7D),
    positiveSurface = Color(0xFF15281F),
    positiveText = Color(0xFF8BE0B0),
    warningSurface = Color(0xFF2B2113),
    warningText = Color(0xFFF3BF68),
    danger = Color(0xFFE56B6F),
    dangerSoft = Color(0xFF311719),
    wallpaperPreviewBg = Color(0xFF101318),
    wallpaperPreviewBorder = Color(0xFF2B313D),
    slotCellBg = Color(0xFF1E232D),
)

private fun makeLightPalette(
    bg: Long,
    card: Long,
    cardEl: Long,
    cardTint: Long,
    border: Long,
    text1: Long,
    text2: Long,
    muted: Long,
    accent: Long,
    accentSoft: Long,
    onAccent: Long,
    sec: Long,
    secSoft: Long,
) = StudioPalette(
    isDark = false,
    background = Color(bg),
    card = Color(card),
    cardElevated = Color(cardEl),
    cardTint = Color(cardTint),
    border = Color(border),
    textPrimary = Color(text1),
    textSecondary = Color(text2),
    textMuted = Color(muted),
    accent = Color(accent),
    accentSoft = Color(accentSoft),
    onAccent = Color(onAccent),
    secondaryAccent = Color(sec),
    secondarySoft = Color(secSoft),
    positive = Color(0xFF237A4B),
    positiveSurface = Color(0xFFE5F4EC),
    positiveText = Color(0xFF1C663F),
    warningSurface = Color(0xFFFFF4E3),
    warningText = Color(0xFFB45309),
    danger = Color(0xFFC83232),
    dangerSoft = Color(0xFFFCECEC),
    wallpaperPreviewBg = Color(0xFF101318),
    wallpaperPreviewBorder = Color(0xFF2B313D),
    slotCellBg = Color(0xFF1E232D),
)

val APP_THEMES: List<AppThemeSpec> = listOf(
    // #1 Default: Cinnabar (Warm Stone Ivory, Ink & Terracotta)
    AppThemeSpec(
        id = "warm-paper",
        numberLabel = "#1",
        title = "Cinnabar",
        subtitle = "Matte stone ivory, deep charcoal & signal terracotta",
        iconMipmapRes = R.mipmap.ic_launcher_warm_paper,
        light = makeLightPalette(
            bg = 0xFFF4F1EA, card = 0xFFFFFFFF, cardEl = 0xFFEAE5DA, cardTint = 0xFFEDE7DC,
            border = 0xFFD9D2C4, text1 = 0xFF1C1B18, text2 = 0xFF4F4B44, muted = 0xFF746E64,
            accent = 0xFFE05A2B, accentSoft = 0xFFFCE8DF, onAccent = 0xFFFFFFFF,
            sec = 0xFF6B5344, secSoft = 0xFFEFE7DE,
        ),
        dark = makeDarkPalette(
            bg = 0xFF151412, card = 0xFF201E1B, cardEl = 0xFF2B2823, cardTint = 0xFF24211D,
            border = 0xFF38342D, text1 = 0xFFF4F1EA, text2 = 0xFFC5BEB2, muted = 0xFF8C8579,
            accent = 0xFFE87043, accentSoft = 0xFF3A2117, onAccent = 0xFFFFFFFF,
            sec = 0xFFDAB092, secSoft = 0xFF2E2620,
        ),
        previewSwatches = listOf(Color(0xFFF4F1EA), Color(0xFF151412), Color(0xFFE05A2B), Color(0xFFEAE5DA)),
    ),
    // #2 Cobalt (Titanium Graphite & Vivid One UI Blue)
    AppThemeSpec(
        id = "samsung-blue",
        numberLabel = "#2",
        title = "Cobalt",
        subtitle = "Native One UI titanium graphite & cobalt",
        iconMipmapRes = R.mipmap.ic_launcher_samsung_blue,
        light = makeLightPalette(
            bg = 0xFFF2F4F8, card = 0xFFFFFFFF, cardEl = 0xFFE6EAF2, cardTint = 0xFFEAF1FE,
            border = 0xFFD6DCE7, text1 = 0xFF121519, text2 = 0xFF434C59, muted = 0xFF687385,
            accent = 0xFF0381FE, accentSoft = 0xFFE2EEFF, onAccent = 0xFFFFFFFF,
            sec = 0xFF1A56C4, secSoft = 0xFFE8EEF9,
        ),
        dark = makeDarkPalette(
            bg = 0xFF0F1114, card = 0xFF1A1D22, cardEl = 0xFF242931, cardTint = 0xFF182233,
            border = 0xFF2D333E, text1 = 0xFFF2F4F8, text2 = 0xFFB8C0CE, muted = 0xFF818B9C,
            accent = 0xFF3B99FF, accentSoft = 0xFF142A47, onAccent = 0xFFFFFFFF,
            sec = 0xFF8AB9FF, secSoft = 0xFF1E283A,
        ),
        previewSwatches = listOf(Color(0xFFF2F4F8), Color(0xFF0F1114), Color(0xFF0381FE), Color(0xFF1A1D22)),
    ),
    // #4 Botanica (Warm Sandstone & Sage-Olive)
    AppThemeSpec(
        id = "nordic-olive",
        numberLabel = "#4",
        title = "Botanica",
        subtitle = "Warm sandstone & calm muted sage-olive",
        iconMipmapRes = R.mipmap.ic_launcher_nordic_olive,
        light = makeLightPalette(
            bg = 0xFFEFECE4, card = 0xFFFAF8F3, cardEl = 0xFFE3DFD4, cardTint = 0xFFE5EAE4,
            border = 0xFFD0CBBF, text1 = 0xFF1E2520, text2 = 0xFF4B554E, muted = 0xFF6C7770,
            accent = 0xFF3E6251, accentSoft = 0xFFDDE9E2, onAccent = 0xFFFFFFFF,
            sec = 0xFF536B5E, secSoft = 0xFFE6ECE7,
        ),
        dark = makeDarkPalette(
            bg = 0xFF141815, card = 0xFF1E2420, cardEl = 0xFF29312B, cardTint = 0xFF1D2822,
            border = 0xFF313B34, text1 = 0xFFEDEAE2, text2 = 0xFFBCC4BE, muted = 0xFF838F87,
            accent = 0xFF6FA389, accentSoft = 0xFF1C3328, onAccent = 0xFF0D1B14,
            sec = 0xFFA2C4B2, secSoft = 0xFF24302A,
        ),
        previewSwatches = listOf(Color(0xFFEFECE4), Color(0xFF141815), Color(0xFF3E6251), Color(0xFFFAF8F3)),
    ),
    // #5 Cognac (Satin Espresso & Burnished Copper)
    AppThemeSpec(
        id = "copper-espresso",
        numberLabel = "#5",
        title = "Cognac",
        subtitle = "Satin espresso metal & warm burnished copper",
        iconMipmapRes = R.mipmap.ic_launcher_copper_espresso,
        light = makeLightPalette(
            bg = 0xFFFBF7F2, card = 0xFFFFFFFF, cardEl = 0xFFF0E6DC, cardTint = 0xFFF6E9DE,
            border = 0xFFE2D3C4, text1 = 0xFF1E1612, text2 = 0xFF59463B, muted = 0xFF806B5E,
            accent = 0xFFC96A38, accentSoft = 0xFFFAE6DB, onAccent = 0xFFFFFFFF,
            sec = 0xFF8F4C28, secSoft = 0xFFF3E7DD,
        ),
        dark = makeDarkPalette(
            bg = 0xFF120E0C, card = 0xFF1E1714, cardEl = 0xFF2B211C, cardTint = 0xFF271B15,
            border = 0xFF3A2C25, text1 = 0xFFF8EFE7, text2 = 0xFFD1BEB2, muted = 0xFF917D71,
            accent = 0xFFE68A57, accentSoft = 0xFF3D2214, onAccent = 0xFF1A0D07,
            sec = 0xFFE8B18E, secSoft = 0xFF2E221C,
        ),
        previewSwatches = listOf(Color(0xFFFBF7F2), Color(0xFF120E0C), Color(0xFFC96A38), Color(0xFFE68A57)),
    ),
    // #6 Cerulean (Deep Ocean Sapphire & Ice-Mist)
    AppThemeSpec(
        id = "pacific-cobalt",
        numberLabel = "#6",
        title = "Cerulean",
        subtitle = "Deep ocean sapphire & ice-mist surfaces",
        iconMipmapRes = R.mipmap.ic_launcher_pacific_cobalt,
        light = makeLightPalette(
            bg = 0xFFF3F6FB, card = 0xFFFFFFFF, cardEl = 0xFFE4ECF7, cardTint = 0xFFE6EEFC,
            border = 0xFFD2DCEB, text1 = 0xFF0F172A, text2 = 0xFF3B4B66, muted = 0xFF64748B,
            accent = 0xFF1D63D8, accentSoft = 0xFFDCE8FD, onAccent = 0xFFFFFFFF,
            sec = 0xFF144299, secSoft = 0xFFE4ECFA,
        ),
        dark = makeDarkPalette(
            bg = 0xFF0A0F1A, card = 0xFF131C2E, cardEl = 0xFF1C2840, cardTint = 0xFF14213B,
            border = 0xFF263552, text1 = 0xFFEFF5FF, text2 = 0xFFB8C7E0, muted = 0xFF7D90B0,
            accent = 0xFF5497FF, accentSoft = 0xFF152D57, onAccent = 0xFF071329,
            sec = 0xFF93C5FD, secSoft = 0xFF1A2845,
        ),
        previewSwatches = listOf(Color(0xFFF3F6FB), Color(0xFF0A0F1A), Color(0xFF1D63D8), Color(0xFF5497FF)),
    ),
    // #9 Marigold (Sunlit Ochre, Umber & Vermilion)
    AppThemeSpec(
        id = "sunlit-ochre",
        numberLabel = "#9",
        title = "Marigold",
        subtitle = "Warm sandstone daylight, umber ink & vermilion",
        iconMipmapRes = R.mipmap.ic_launcher_sunlit_ochre,
        light = makeLightPalette(
            bg = 0xFFF8F2E8, card = 0xFFFFFDF9, cardEl = 0xFFEEDFC8, cardTint = 0xFFF4E7D3,
            border = 0xFFDFCEB4, text1 = 0xFF231A14, text2 = 0xFF5C4B3D, muted = 0xFF826F5E,
            accent = 0xFFCC5218, accentSoft = 0xFFFCE4D6, onAccent = 0xFFFFFFFF,
            sec = 0xFF91431B, secSoft = 0xFFF2E6D6,
        ),
        dark = makeDarkPalette(
            bg = 0xFF16120E, card = 0xFF231C16, cardEl = 0xFF30271F, cardTint = 0xFF292018,
            border = 0xFF3E3228, text1 = 0xFFF6EBDC, text2 = 0xFFD4C1AB, muted = 0xFF96836F,
            accent = 0xFFEC7438, accentSoft = 0xFF422110, onAccent = 0xFF1E0E06,
            sec = 0xFFE7B882, secSoft = 0xFF31261E,
        ),
        previewSwatches = listOf(Color(0xFFF8F2E8), Color(0xFFDEC29B), Color(0xFF16120E), Color(0xFFCC5218)),
    ),
    // #10 Mercury (Brushed Pearl Steel & Electric Blue)
    AppThemeSpec(
        id = "arctic-chrome",
        numberLabel = "#10",
        title = "Mercury",
        subtitle = "Brushed pearl steel & vivid electric sapphire",
        iconMipmapRes = R.mipmap.ic_launcher_arctic_chrome,
        light = makeLightPalette(
            bg = 0xFFF1F4F9, card = 0xFFFFFFFF, cardEl = 0xFFE1E7F0, cardTint = 0xFFE6ECF7,
            border = 0xFFCFD8E6, text1 = 0xFF111622, text2 = 0xFF444F63, muted = 0xFF66738A,
            accent = 0xFF2457E6, accentSoft = 0xFFE0E8FD, onAccent = 0xFFFFFFFF,
            sec = 0xFF1D4ED8, secSoft = 0xFFE4EBF8,
        ),
        dark = makeDarkPalette(
            bg = 0xFF0F1218, card = 0xFF1A1F29, cardEl = 0xFF242B38, cardTint = 0xFF1A2233,
            border = 0xFF2E3748, text1 = 0xFFF1F4F9, text2 = 0xFFB8C2D4, muted = 0xFF818DA3,
            accent = 0xFF5B87FF, accentSoft = 0xFF17274D, onAccent = 0xFFFFFFFF,
            sec = 0xFF9BB6FF, secSoft = 0xFF1E2638,
        ),
        previewSwatches = listOf(Color(0xFFF1F4F9), Color(0xFFC9D3E3), Color(0xFF0F1218), Color(0xFF2457E6)),
    ),
)

fun findThemeSpec(themeId: String?): AppThemeSpec =
    APP_THEMES.firstOrNull { it.id == themeId } ?: APP_THEMES.first()

val LocalStudioPalette = staticCompositionLocalOf { APP_THEMES.first().light }

fun buildColorScheme(p: StudioPalette): ColorScheme = if (p.isDark) {
    darkColorScheme(
        primary = p.accent,
        onPrimary = p.onAccent,
        primaryContainer = p.accentSoft,
        onPrimaryContainer = p.accent,
        secondary = p.secondaryAccent,
        onSecondary = Color(0xFF0E1117),
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
        onPrimaryContainer = p.accent,
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

val StudioTypography = Typography(
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
    themeId: String,
    themeMode: String,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode.lowercase()) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    val spec = findThemeSpec(themeId)
    val targetPalette = spec.palette(useDark)
    val bgAnim by animateColorAsState(targetPalette.background, animationSpec = tween(220), label = "bg")
    val cardAnim by animateColorAsState(targetPalette.card, animationSpec = tween(220), label = "card")
    val accentAnim by animateColorAsState(targetPalette.accent, animationSpec = tween(220), label = "accent")
    val palette = targetPalette.copy(background = bgAnim, card = cardAnim, accent = accentAnim)

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
