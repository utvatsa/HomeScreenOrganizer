package com.homestudio.local

import com.homestudio.local.catalog.AppCatalogEngine
import com.homestudio.local.catalog.InstalledAppInfo
import com.homestudio.local.data.CustomAppDefinition
import com.homestudio.local.data.HeaderPlacement
import com.homestudio.local.data.HeaderTheme
import com.homestudio.local.data.HomeStudioConfig
import com.homestudio.local.data.HomeStudioStore
import com.homestudio.local.data.LayoutFolderChild
import com.homestudio.local.data.LayoutPreferences
import com.homestudio.local.data.LayoutSlotItem
import com.homestudio.local.data.StudioPage
import com.homestudio.local.data.WidgetInstanceConfig
import com.homestudio.local.planner.LayoutSuggestionEngine
import com.homestudio.local.planner.PresetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStudioStoreTest {
    @Test
    fun roundTripConfigJson() {
        val original = HomeStudioConfig(
            globalTheme = HeaderTheme(fontSizeSp = 28f, fontWeight = 200, opacity = 0.85f),
            widgets = mapOf(
                42 to WidgetInstanceConfig(
                    appWidgetId = 42,
                    categoryId = "payments",
                    title = "Payments",
                    inheritTheme = false,
                    fontSizeSp = 22f,
                ),
            ),
            preferences = LayoutPreferences(
                defaultMaxItemsPerPage = 6,
                onboardingCompleted = true,
                activePresetId = "balanced",
                appThemeMode = "light",
                appColorTheme = "copper-espresso",
                syncLauncherIcon = false,
                layoutViewOrientation = "horizontal",
                showWallpaperInPreview = false,
                activeWallpaperSource = "preset:oneui-bloom",
                hasCompletedTutorial = true,
            ),
            pages = listOf(
                StudioPage(
                    id = "payments",
                    title = "Payments",
                    order = 0,
                    isDefaultHome = false,
                    maxItemsPerPage = 6,
                    header = HeaderPlacement(enabled = true, row = 1, alignmentOverride = "center"),
                    items = listOf(
                        LayoutSlotItem(
                            id = "p1",
                            type = "folder",
                            label = "UPI Suite",
                            children = listOf(LayoutFolderChild("in.org.npci.upiapp", "HomeActivity", "BHIM")),
                            row = 5,
                            column = 0,
                        ),
                    ),
                    boundWidgetId = 42,
                ),
            ),
            customApps = listOf(
                CustomAppDefinition("com.example.work", "My Work App", listOf("productivity"), "Work Tools"),
            ),
        )
        val json = HomeStudioStore.toJson(original)
        val restored = HomeStudioStore.fromJson(json)
        assertEquals(28f, restored.globalTheme.fontSizeSp)
        assertEquals(200, restored.globalTheme.fontWeight)
        assertEquals("Payments", restored.widgets[42]?.title)
        assertFalse(restored.widgets[42]?.inheritTheme ?: true)
        assertEquals(22f, restored.widgets[42]?.fontSizeSp)
        assertEquals(6, restored.preferences.defaultMaxItemsPerPage)
        assertEquals("light", restored.preferences.appThemeMode)
        assertEquals("copper-espresso", restored.preferences.appColorTheme)
        assertFalse(restored.preferences.syncLauncherIcon)
        assertEquals("horizontal", restored.preferences.layoutViewOrientation)
        assertFalse(restored.preferences.showWallpaperInPreview)
        assertEquals("preset:oneui-bloom", restored.preferences.activeWallpaperSource)
        assertTrue(restored.preferences.hasCompletedTutorial)
        assertEquals(1, restored.pages.size)
        assertEquals(1, restored.pages[0].header.row)
        assertEquals("center", restored.pages[0].header.alignmentOverride)
        assertEquals("UPI Suite", restored.pages[0].items[0].label)
        assertEquals(1, restored.customApps.size)

        val hostJson = HomeStudioStore.toHostDesiredLayoutJson(restored)
        assertTrue(hostJson.contains("\"schemaVersion\": 1"))
        assertTrue(hostJson.contains("\"UPI Suite\""))
    }

    @Test
    fun resolveThemeRespectsOverrides() {
        val global = HeaderTheme(fontSizeSp = 26f, opacity = 0.9f, showSeparator = false)
        val inheriting = WidgetInstanceConfig(appWidgetId = 1, title = "A", inheritTheme = true)
        val overridden = WidgetInstanceConfig(
            appWidgetId = 2,
            title = "B",
            inheritTheme = false,
            fontSizeSp = 20f,
            showSeparator = true,
        )
        assertEquals(26f, inheriting.resolveTheme(global).fontSizeSp)
        assertEquals(20f, overridden.resolveTheme(global).fontSizeSp)
        assertTrue(overridden.resolveTheme(global).showSeparator)
    }

    @Test
    fun catalogSuggestionsOnlyUseInstalledAppsAndSupportFolderBreakdown() {
        val catalogJson = """
            {
              "categories": [{"id": "payments", "title": "Payments", "description": "Pay"}],
              "apps": [
                {"package": "com.phonepe.app", "label": "PhonePe", "categories": ["payments"]},
                {"package": "in.org.npci.upiapp", "label": "BHIM", "categories": ["payments"], "suite": "UPI Suite"},
                {"package": "com.mobikwik_new", "label": "MobiKwik", "categories": ["payments"], "suite": "UPI Suite"},
                {"package": "com.uninstalled.fake", "label": "Uninstalled App", "categories": ["payments"]}
              ]
            }
        """.trimIndent()
        val cat = AppCatalogEngine.parseCatalogJson(catalogJson)
        assertTrue(cat.appsByPackage.containsKey("com.uninstalled.fake"))

        // Simulate device having ONLY PhonePe, BHIM, MobiKwik installed (NOT com.uninstalled.fake)
        val installed = listOf(
            InstalledAppInfo("com.phonepe.app", "Main", "PhonePe", listOf("payments"), null),
            InstalledAppInfo("in.org.npci.upiapp", "Home", "BHIM", listOf("payments"), "UPI Suite"),
            InstalledAppInfo("com.mobikwik_new", "Main", "MobiKwik", listOf("payments"), "UPI Suite"),
        )
        val draft = LayoutSuggestionEngine.buildSuggestionDraft("payments", installed, emptyList(), maxItemsPerPage = 8)
        val allPkgNames = draft.soloApps.map { it.packageName } + draft.folders.flatMap { it.children.map { c -> c.packageName } }
        assertFalse(allPkgNames.contains("com.uninstalled.fake"))
        assertEquals(1, draft.folders.size)
        assertEquals("UPI Suite", draft.folders[0].title)

        // Break down folder into solo apps
        val broken = LayoutSuggestionEngine.breakDownFolder(draft, draft.folders[0].id)
        assertEquals(0, broken.folders.size)
        assertEquals(3, broken.soloApps.size)

        // Preset generation respects Brief & Detailed layouts
        val brief = LayoutSuggestionEngine.generateCollectionPreset(PresetKind.BRIEF, installed, emptyList(), maxItemsPerPage = 4)
        assertTrue(brief.size in 2..3)
        val detailed = LayoutSuggestionEngine.generateCollectionPreset(PresetKind.DETAILED, installed, emptyList(), maxItemsPerPage = 8)
        assertTrue(detailed.any { it.id == "payments" })
    }

    @Test
    fun parseObservedSnapshotReadsOneUiPagesWidgetsAndFolders() {
        val cfg = HomeStudioConfig(
            widgets = mapOf(145 to WidgetInstanceConfig(appWidgetId = 145, categoryId = "most-used", title = "Most Used")),
        )
        val observedRaw = """
            {
              "oneUiSettings": "Default Home Page : 1 | Home Grid : 4 X 6",
              "pages": [
                {
                  "page_index": 0,
                  "items": [
                    {"type": "widget", "component": "com.homestudio.local/.widget.CategoryHeaderWidgetProvider", "app_widget_id": 145, "position_x": 0, "position_y": 0},
                    {"type": "app", "title": "Rapido", "component": "com.rapido.passenger/.Main", "position_x": 0, "position_y": 4}
                  ]
                },
                {
                  "page_index": 1,
                  "items": [
                    {"type": "folder", "title": "Google Suite", "position_x": 3, "position_y": 5, "children": [
                      {"title": "Gmail", "component": "com.google.android.gm/.Main"}
                    ]}
                  ]
                }
              ]
            }
        """.trimIndent()
        val snap = HomeStudioStore.parseObservedSnapshotJson(observedRaw, cfg)
        assertTrue(snap.isLiveSnapshot)
        assertEquals(2, snap.pages.size)
        assertEquals("Most Used", snap.pages[0].title)
        assertFalse(snap.pages[0].isDefaultHome)
        assertTrue(snap.pages[1].isDefaultHome)
        assertEquals(1, snap.pages[1].items.size)
        assertEquals("Google Suite", snap.pages[1].items[0].label)
        assertEquals(1, snap.pages[1].items[0].children.size)
    }
}
