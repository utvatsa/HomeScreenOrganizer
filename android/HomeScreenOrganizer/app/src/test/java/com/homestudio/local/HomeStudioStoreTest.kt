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
import com.homestudio.local.data.LayoutSnapshotKind
import com.homestudio.local.data.StudioPage
import com.homestudio.local.data.WidgetInstanceConfig
import com.homestudio.local.planner.LayoutSuggestionEngine
import com.homestudio.local.planner.PresetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HomeStudioStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()
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

    @Test
    fun saveDraftAndReloadIntoStudioPreservesAppChrome() {
        val store = HomeStudioStore(tempFolder.newFolder("drafts"))
        val draftConfig = HomeStudioConfig(
            globalTheme = HeaderTheme(fontSizeSp = 30f, alignment = "center"),
            preferences = LayoutPreferences(
                defaultMaxItemsPerPage = 6,
                gridColumns = 5,
                gridRows = 7,
                activePresetId = "balanced",
                appThemeMode = "light",
                appColorTheme = "warm-paper",
                hasCompletedTutorial = false,
            ),
            pages = listOf(
                StudioPage(id = "daily", title = "Daily Essentials", order = 0, isDefaultHome = true),
                StudioPage(id = "wallpaper", title = "Wallpaper", order = 1, isWallpaperPage = true),
            ),
        )

        val savedDraft = store.saveDraft(
            config = draftConfig,
            name = "Weekend Draft",
            notes = "Compact 5x7 test",
            nowEpochMs = 1_700_000_001_000L,
        )
        assertEquals(LayoutSnapshotKind.DRAFT, savedDraft.kind)
        assertFalse(savedDraft.isActive)
        assertEquals(2, savedDraft.pageCount)
        assertEquals("Daily Essentials", savedDraft.defaultHomeTitle)
        assertEquals("5×7 • Max 6", savedDraft.gridSummary)

        val liveChromeConfig = HomeStudioConfig(
            preferences = LayoutPreferences(
                defaultMaxItemsPerPage = 8,
                gridColumns = 4,
                gridRows = 6,
                appThemeMode = "dark",
                appColorTheme = "pacific-cobalt",
                syncLauncherIcon = false,
                hasCompletedTutorial = true,
            ),
            pages = listOf(StudioPage(id = "other", title = "Other", order = 0)),
        )

        val loaded = store.loadSnapshotIntoStudio(savedDraft.id, liveChromeConfig)
        assertNotNull(loaded)
        val restored = loaded!!.first
        assertEquals(2, restored.pages.size)
        assertEquals("Daily Essentials", restored.pages[0].title)
        assertEquals(5, restored.preferences.gridColumns)
        assertEquals(7, restored.preferences.gridRows)
        assertEquals(6, restored.preferences.defaultMaxItemsPerPage)
        assertEquals(30f, restored.globalTheme.fontSizeSp)
        // App chrome settings remain untouched on draft reload
        assertEquals("dark", restored.preferences.appThemeMode)
        assertEquals("pacific-cobalt", restored.preferences.appColorTheme)
        assertFalse(restored.preferences.syncLauncherIcon)
        assertTrue(restored.preferences.hasCompletedTutorial)
    }

    @Test
    fun applyRetainsChronologicalSnapshotsWithoutOverwriting() {
        val store = HomeStudioStore(tempFolder.newFolder("applied"))
        val v1 = HomeStudioConfig(
            preferences = LayoutPreferences(activePresetId = "brief"),
            pages = listOf(StudioPage(id = "p1", title = "Brief Home", order = 0, isDefaultHome = true)),
        )
        val v2 = HomeStudioConfig(
            preferences = LayoutPreferences(activePresetId = "balanced"),
            pages = listOf(
                StudioPage(id = "p1", title = "Most Used", order = 0),
                StudioPage(id = "p2", title = "Daily Essentials", order = 1, isDefaultHome = true),
            ),
        )

        val first = store.recordAppliedSnapshot(v1, name = "Brief Setup", nowEpochMs = 1_700_000_010_000L)
        val second = store.recordAppliedSnapshot(v2, name = "Balanced Setup", nowEpochMs = 1_700_000_020_000L)

        val archive = store.loadArchive()
        assertEquals(2, archive.size)
        assertEquals(second.id, archive.first { it.isActive }.id)
        assertEquals(1, archive.count { it.isActive })
        assertTrue(archive.any { it.id == first.id && !it.isActive && it.pageCount == 1 })
        assertTrue(archive.any { it.id == second.id && it.isActive && it.pageCount == 2 })
        assertTrue(HomeStudioStore.configMatchesEntry(store.load(), second))
    }

    @Test
    fun switchActiveSnapshotAndManageNonActiveDrafts() {
        val store = HomeStudioStore(tempFolder.newFolder("switch"))
        val briefCfg = HomeStudioConfig(
            preferences = LayoutPreferences(activePresetId = "brief", appThemeMode = "dark", hasCompletedTutorial = true),
            pages = listOf(StudioPage(id = "p1", title = "Brief Home", order = 0, isDefaultHome = true)),
        )
        val detailedCfg = HomeStudioConfig(
            preferences = LayoutPreferences(activePresetId = "detailed", appThemeMode = "dark", hasCompletedTutorial = true),
            pages = listOf(
                StudioPage(id = "p1", title = "Most Used", order = 0),
                StudioPage(id = "p2", title = "Daily Essentials", order = 1, isDefaultHome = true),
                StudioPage(id = "p3", title = "Payments", order = 2),
            ),
        )
        val draftCfg = HomeStudioConfig(
            preferences = LayoutPreferences(activePresetId = null, defaultMaxItemsPerPage = 10),
            pages = listOf(StudioPage(id = "custom", title = "Weekend", order = 0, isDefaultHome = true)),
        )

        val applied1 = store.recordAppliedSnapshot(briefCfg, name = "Brief v1", nowEpochMs = 1_700_000_100_000L)
        val applied2 = store.recordAppliedSnapshot(detailedCfg, name = "Detailed v2", nowEpochMs = 1_700_000_200_000L)
        val draft = store.saveDraft(draftCfg, name = "Weekend Draft", nowEpochMs = 1_700_000_300_000L)

        // Switch back to older applied snapshot cleanly without losing history
        val switchedTo1 = store.switchActiveSnapshot(applied1.id, detailedCfg, nowEpochMs = 1_700_000_400_000L)
        assertNotNull(switchedTo1)
        assertEquals(1, switchedTo1!!.first.pages.size)
        val afterSwitchArchive = store.loadArchive()
        assertEquals(3, afterSwitchArchive.size)
        assertTrue(afterSwitchArchive.first { it.id == applied1.id }.isActive)
        assertFalse(afterSwitchArchive.first { it.id == applied2.id }.isActive)

        // Cannot delete active snapshot or applied history; can rename & delete non-active draft
        assertFalse(store.deleteDraft(applied1.id))
        assertFalse(store.deleteDraft(applied2.id))
        assertTrue(store.renameArchiveEntry(draft.id, "Weekend Draft Renamed", nowEpochMs = 1_700_000_500_000L))
        assertEquals("Weekend Draft Renamed", store.loadArchive().first { it.id == draft.id }.name)

        // Reapply draft -> creates new active applied snapshot while keeping draft intact
        val appliedFromDraft = store.switchActiveSnapshot(draft.id, switchedTo1.first, nowEpochMs = 1_700_000_600_000L)
        assertNotNull(appliedFromDraft)
        assertTrue(appliedFromDraft!!.second.isActive)
        assertEquals(LayoutSnapshotKind.APPLIED, appliedFromDraft.second.kind)
        assertEquals(4, store.loadArchive().size)

        // Delete non-active draft cleanly
        assertTrue(store.deleteDraft(draft.id))
        assertEquals(3, store.loadArchive().size)
    }
}
