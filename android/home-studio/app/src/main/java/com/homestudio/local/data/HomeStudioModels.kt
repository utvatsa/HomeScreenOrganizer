package com.homestudio.local.data

import android.graphics.Color

data class HeaderTheme(
    val fontFamily: String = "sans-serif",
    val fontWeight: Int = 300,
    val fontSizeSp: Float = 26f,
    val textColor: String = "#FFFFFFFF",
    val opacity: Float = 0.92f,
    val alignment: String = "start", // start | center | end
    val verticalAlignment: String = "bottom", // top | center | bottom
    val backgroundColor: String = "#00000000",
    val paddingDp: Int = 8,
    val letterSpacing: Float = 0.03f,
    val showSeparator: Boolean = false,
    val separatorColor: String = "#33FFFFFF",
    val shadowEnabled: Boolean = false,
) {
    fun parsedTextColor(): Int = parseHexColor(textColor, Color.WHITE)
    fun parsedBackgroundColor(): Int = parseHexColor(backgroundColor, Color.TRANSPARENT)
    fun parsedSeparatorColor(): Int = parseHexColor(separatorColor, 0x33FFFFFF)

    companion object {
        fun parseHexColor(hex: String, fallback: Int): Int = try {
            Color.parseColor(hex)
        } catch (_: Throwable) {
            fallback
        }
    }
}

data class CategoryDefinition(
    val id: String,
    val title: String,
    val subtitle: String? = null,
)

data class WidgetInstanceConfig(
    val appWidgetId: Int,
    val categoryId: String? = null,
    val title: String = "Category",
    val subtitle: String? = null,
    val inheritTheme: Boolean = true,
    val fontFamily: String? = null,
    val fontWeight: Int? = null,
    val fontSizeSp: Float? = null,
    val textColor: String? = null,
    val opacity: Float? = null,
    val alignment: String? = null,
    val verticalAlignment: String? = null,
    val backgroundColor: String? = null,
    val paddingDp: Int? = null,
    val letterSpacing: Float? = null,
    val showSeparator: Boolean? = null,
    val shadowEnabled: Boolean? = null,
) {
    fun resolveTheme(global: HeaderTheme): HeaderTheme {
        if (inheritTheme) return global
        return HeaderTheme(
            fontFamily = fontFamily ?: global.fontFamily,
            fontWeight = fontWeight ?: global.fontWeight,
            fontSizeSp = fontSizeSp ?: global.fontSizeSp,
            textColor = textColor ?: global.textColor,
            opacity = opacity ?: global.opacity,
            alignment = alignment ?: global.alignment,
            verticalAlignment = verticalAlignment ?: global.verticalAlignment,
            backgroundColor = backgroundColor ?: global.backgroundColor,
            paddingDp = paddingDp ?: global.paddingDp,
            letterSpacing = letterSpacing ?: global.letterSpacing,
            showSeparator = showSeparator ?: global.showSeparator,
            separatorColor = global.separatorColor,
            shadowEnabled = shadowEnabled ?: global.shadowEnabled,
        )
    }
}

data class HomeStudioConfig(
    val schemaVersion: Int = 1,
    val globalTheme: HeaderTheme = HeaderTheme(),
    val categories: List<CategoryDefinition> = defaultCategories(),
    val widgets: Map<Int, WidgetInstanceConfig> = emptyMap(),
) {
    companion object {
        fun defaultCategories(): List<CategoryDefinition> = listOf(
            CategoryDefinition("most-used", "Most Used"),
            CategoryDefinition("daily-essentials", "Daily Essentials"),
            CategoryDefinition("payments", "Payments"),
            CategoryDefinition("banking-suites", "Banking Suites"),
            CategoryDefinition("food-dining", "Food & Dining"),
            CategoryDefinition("travel-cabs", "Travel & Cabs"),
            CategoryDefinition("upi", "UPI"),
            CategoryDefinition("finance-wealth", "Finance & Wealth"),
            CategoryDefinition("ai-suite", "AI Suite"),
        )
    }
}
