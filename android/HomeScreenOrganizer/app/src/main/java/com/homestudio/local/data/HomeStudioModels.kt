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

data class CustomAppDefinition(
    val packageName: String,
    val displayName: String,
    val categories: List<String> = emptyList(),
    val suiteFolder: String? = null,
)

data class LayoutFolderChild(
    val packageName: String,
    val activityName: String = "",
    val label: String,
)

data class LayoutSlotItem(
    val id: String,
    val type: String = "app", // app | folder
    val label: String,
    val packageName: String? = null,
    val activityName: String? = null,
    val children: List<LayoutFolderChild> = emptyList(),
    val row: Int = 4,
    val column: Int = 0,
)

data class HeaderPlacement(
    val enabled: Boolean = true,
    val row: Int = 0,
    val column: Int = 0,
    val columnSpan: Int = 4,
    val rowSpan: Int = 1,
    val verticalAlignment: String = "bottom",
    val alignmentOverride: String? = null,
)

data class StudioPage(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val order: Int = 0,
    val isDefaultHome: Boolean = false,
    val maxItemsPerPage: Int? = null, // null inherits LayoutPreferences.defaultMaxItemsPerPage
    val isWallpaperPage: Boolean = false,
    val header: HeaderPlacement = HeaderPlacement(),
    val items: List<LayoutSlotItem> = emptyList(),
    val boundWidgetId: Int? = null,
) {
    fun effectiveMaxItems(globalMax: Int): Int = (maxItemsPerPage ?: globalMax).coerceIn(1, 20)
}

data class LayoutPreferences(
    val defaultMaxItemsPerPage: Int = 8,
    val gridColumns: Int = 4,
    val gridRows: Int = 6,
    val onboardingCompleted: Boolean = false,
    val activePresetId: String? = null,
    val appThemeMode: String = "system", // system | light | dark
    val layoutViewOrientation: String = "vertical", // vertical | horizontal
    val showWallpaperInPreview: Boolean = true,
    val activeWallpaperSource: String = "device", // device | gallery | preset:<id>
    val hasCompletedTutorial: Boolean = false,
)

data class ObservedSnapshotResult(
    val pages: List<StudioPage>,
    val sourceLabel: String,
    val isLiveSnapshot: Boolean,
)

data class HomeStudioConfig(
    val schemaVersion: Int = 2,
    val globalTheme: HeaderTheme = HeaderTheme(),
    val categories: List<CategoryDefinition> = defaultCategories(),
    val widgets: Map<Int, WidgetInstanceConfig> = emptyMap(),
    val preferences: LayoutPreferences = LayoutPreferences(),
    val pages: List<StudioPage> = emptyList(),
    val customApps: List<CustomAppDefinition> = emptyList(),
) {
    companion object {
        fun defaultCategories(): List<CategoryDefinition> = listOf(
            CategoryDefinition("most-used", "Most Used"),
            CategoryDefinition("daily-essentials", "Daily Essentials"),
            CategoryDefinition("payments", "Payments"),
            CategoryDefinition("banking", "Banking Suites"),
            CategoryDefinition("upi", "UPI"),
            CategoryDefinition("finance", "Finance & Wealth"),
            CategoryDefinition("ai-suite", "AI Suite"),
            CategoryDefinition("productivity", "Productivity"),
            CategoryDefinition("food-dining", "Food & Dining"),
            CategoryDefinition("travel", "Travel & Commute"),
            CategoryDefinition("health", "Health & Fitness"),
            CategoryDefinition("smart-home", "Smart Home"),
            CategoryDefinition("shopping", "Shopping"),
            CategoryDefinition("entertainment", "Entertainment"),
            CategoryDefinition("social-comms", "Social & Comms"),
            CategoryDefinition("government-docs", "Government & Docs"),
        )
    }
}
