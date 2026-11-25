package com.homestudio.local.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class HomeStudioStore(private val context: Context) {

    private val configFile: File
        get() = File(context.filesDir, "home_studio_config.json")

    @Synchronized
    fun load(): HomeStudioConfig {
        val file = configFile
        if (!file.exists()) {
            val defaults = HomeStudioConfig()
            save(defaults)
            return defaults
        }
        return try {
            fromJson(file.readText(Charsets.UTF_8))
        } catch (_: Throwable) {
            HomeStudioConfig()
        }
    }

    @Synchronized
    fun save(config: HomeStudioConfig) {
        val tmp = File(context.filesDir, "home_studio_config.json.tmp")
        tmp.writeText(toJson(config), Charsets.UTF_8)
        if (!tmp.renameTo(configFile)) {
            configFile.writeText(toJson(config), Charsets.UTF_8)
            tmp.delete()
        }
    }

    fun getWidgetConfig(appWidgetId: Int): WidgetInstanceConfig {
        val cfg = load()
        return cfg.widgets[appWidgetId] ?: WidgetInstanceConfig(
            appWidgetId = appWidgetId,
            categoryId = "daily-essentials",
            title = "Category",
        )
    }

    fun putWidgetConfig(widgetConfig: WidgetInstanceConfig) {
        val cfg = load()
        val updated = cfg.widgets.toMutableMap()
        updated[widgetConfig.appWidgetId] = widgetConfig
        save(cfg.copy(widgets = updated))
    }

    fun removeWidgets(appWidgetIds: IntArray) {
        val cfg = load()
        val updated = cfg.widgets.toMutableMap()
        appWidgetIds.forEach { updated.remove(it) }
        save(cfg.copy(widgets = updated))
    }

    fun remapWidgetIds(oldIds: IntArray, newIds: IntArray) {
        val cfg = load()
        val updated = cfg.widgets.toMutableMap()
        for (i in oldIds.indices) {
            if (i < newIds.size) {
                val existing = updated.remove(oldIds[i])
                if (existing != null) {
                    updated[newIds[i]] = existing.copy(appWidgetId = newIds[i])
                }
            }
        }
        save(cfg.copy(widgets = updated))
    }

    fun exportJson(): String = toJson(load())

    fun importJson(rawJson: String): HomeStudioConfig {
        val parsed = fromJson(rawJson)
        save(parsed)
        return parsed
    }

    private val observedSnapshotFile: File
        get() = File(context.filesDir, "observed_layout_snapshot.json")

    private val externalObservedSnapshotFile: File?
        get() = context.getExternalFilesDir(null)?.let { File(it, "observed_layout_snapshot.json") }

    fun saveObservedSnapshotJson(rawJson: String): ObservedSnapshotResult {
        observedSnapshotFile.writeText(rawJson, Charsets.UTF_8)
        return loadObservedSnapshot(load())
    }

    fun loadObservedSnapshot(cfg: HomeStudioConfig): ObservedSnapshotResult {
        val candidates = listOfNotNull(observedSnapshotFile, externalObservedSnapshotFile)
            .filter { it.exists() && it.length() > 0 }
            .sortedByDescending { it.lastModified() }
        for (snapFile in candidates) {
            val parsed = runCatching {
                parseObservedSnapshotJson(snapFile.readText(Charsets.UTF_8), cfg)
            }.getOrNull()
            if (parsed != null && parsed.pages.isNotEmpty()) {
                return parsed
            }
        }
        // Fallback: surface currently bound Category Header widgets directly if snapshot hasn't been synced yet
        val widgetPages = cfg.widgets.values.sortedBy { it.appWidgetId }.mapIndexed { idx, w ->
            StudioPage(
                id = w.categoryId ?: "widget-${w.appWidgetId}",
                title = w.title,
                order = idx,
                isDefaultHome = idx == 0,
                header = HeaderPlacement(enabled = true, row = 0, column = 0, columnSpan = cfg.preferences.gridColumns, rowSpan = 1),
                boundWidgetId = w.appWidgetId,
            )
        }
        return ObservedSnapshotResult(
            pages = widgetPages,
            sourceLabel = if (widgetPages.isNotEmpty()) {
                "Showing ${widgetPages.size} live bound header widgets (sync snapshot via ADB or Import JSON for full icon grid)"
            } else {
                "No live launcher snapshot synced yet"
            },
            isLiveSnapshot = false,
        )
    }

    companion object {
        fun toJson(cfg: HomeStudioConfig): String {
            val root = JSONObject()
            root.put("schemaVersion", cfg.schemaVersion)

            val t = cfg.globalTheme
            root.put("globalTheme", JSONObject().apply {
                put("fontFamily", t.fontFamily)
                put("fontWeight", t.fontWeight)
                put("fontSizeSp", t.fontSizeSp.toDouble())
                put("textColor", t.textColor)
                put("opacity", t.opacity.toDouble())
                put("alignment", t.alignment)
                put("verticalAlignment", t.verticalAlignment)
                put("backgroundColor", t.backgroundColor)
                put("paddingDp", t.paddingDp)
                put("letterSpacing", t.letterSpacing.toDouble())
                put("showSeparator", t.showSeparator)
                put("separatorColor", t.separatorColor)
                put("shadowEnabled", t.shadowEnabled)
            })

            val cats = JSONArray()
            cfg.categories.forEach { c ->
                cats.put(JSONObject().apply {
                    put("id", c.id)
                    put("title", c.title)
                    if (c.subtitle != null) put("subtitle", c.subtitle)
                })
            }
            root.put("categories", cats)

            val widgetsArr = JSONArray()
            cfg.widgets.values.sortedBy { it.appWidgetId }.forEach { w ->
                widgetsArr.put(JSONObject().apply {
                    put("appWidgetId", w.appWidgetId)
                    if (w.categoryId != null) put("categoryId", w.categoryId)
                    put("title", w.title)
                    if (w.subtitle != null) put("subtitle", w.subtitle)
                    put("inheritTheme", w.inheritTheme)
                    w.fontFamily?.let { put("fontFamily", it) }
                    w.fontWeight?.let { put("fontWeight", it) }
                    w.fontSizeSp?.let { put("fontSizeSp", it.toDouble()) }
                    w.textColor?.let { put("textColor", it) }
                    w.opacity?.let { put("opacity", it.toDouble()) }
                    w.alignment?.let { put("alignment", it) }
                    w.verticalAlignment?.let { put("verticalAlignment", it) }
                    w.backgroundColor?.let { put("backgroundColor", it) }
                    w.paddingDp?.let { put("paddingDp", it) }
                    w.letterSpacing?.let { put("letterSpacing", it.toDouble()) }
                    w.showSeparator?.let { put("showSeparator", it) }
                    w.shadowEnabled?.let { put("shadowEnabled", it) }
                })
            }
            root.put("widgets", widgetsArr)

            val p = cfg.preferences
            root.put("preferences", JSONObject().apply {
                put("defaultMaxItemsPerPage", p.defaultMaxItemsPerPage)
                put("gridColumns", p.gridColumns)
                put("gridRows", p.gridRows)
                put("onboardingCompleted", p.onboardingCompleted)
                if (p.activePresetId != null) put("activePresetId", p.activePresetId)
                put("appThemeMode", p.appThemeMode)
                put("appColorTheme", p.appColorTheme)
                put("syncLauncherIcon", p.syncLauncherIcon)
                put("layoutViewOrientation", p.layoutViewOrientation)
                put("showWallpaperInPreview", p.showWallpaperInPreview)
                put("activeWallpaperSource", p.activeWallpaperSource)
                put("hasCompletedTutorial", p.hasCompletedTutorial)
            })

            val pagesArr = JSONArray()
            cfg.pages.sortedBy { it.order }.forEach { pg ->
                pagesArr.put(JSONObject().apply {
                    put("id", pg.id)
                    put("title", pg.title)
                    if (pg.subtitle != null) put("subtitle", pg.subtitle)
                    put("order", pg.order)
                    put("isDefaultHome", pg.isDefaultHome)
                    if (pg.maxItemsPerPage != null) put("maxItemsPerPage", pg.maxItemsPerPage)
                    put("isWallpaperPage", pg.isWallpaperPage)
                    if (pg.boundWidgetId != null) put("boundWidgetId", pg.boundWidgetId)
                    put("header", JSONObject().apply {
                        put("enabled", pg.header.enabled)
                        put("row", pg.header.row)
                        put("column", pg.header.column)
                        put("columnSpan", pg.header.columnSpan)
                        put("rowSpan", pg.header.rowSpan)
                        put("verticalAlignment", pg.header.verticalAlignment)
                        if (pg.header.alignmentOverride != null) put("alignmentOverride", pg.header.alignmentOverride)
                    })
                    val itemsArr = JSONArray()
                    pg.items.forEach { it ->
                        itemsArr.put(JSONObject().apply {
                            put("id", it.id)
                            put("type", it.type)
                            put("label", it.label)
                            if (it.packageName != null) put("packageName", it.packageName)
                            if (it.activityName != null) put("activityName", it.activityName)
                            put("row", it.row)
                            put("column", it.column)
                            if (it.children.isNotEmpty()) {
                                val chArr = JSONArray()
                                it.children.forEach { ch ->
                                    chArr.put(JSONObject().apply {
                                        put("packageName", ch.packageName)
                                        put("activityName", ch.activityName)
                                        put("label", ch.label)
                                    })
                                }
                                put("children", chArr)
                            }
                        })
                    }
                    put("items", itemsArr)
                })
            }
            root.put("pages", pagesArr)

            val customArr = JSONArray()
            cfg.customApps.forEach { ca ->
                customArr.put(JSONObject().apply {
                    put("packageName", ca.packageName)
                    put("displayName", ca.displayName)
                    put("categories", JSONArray(ca.categories))
                    if (ca.suiteFolder != null) put("suiteFolder", ca.suiteFolder)
                })
            }
            root.put("customApps", customArr)

            return root.toString(2)
        }

        fun fromJson(raw: String): HomeStudioConfig {
            val root = JSONObject(raw)
            val schemaVersion = root.optInt("schemaVersion", 1)
            val tObj = root.optJSONObject("globalTheme") ?: JSONObject()
            val theme = HeaderTheme(
                fontFamily = tObj.optString("fontFamily", "sans-serif"),
                fontWeight = tObj.optInt("fontWeight", 300),
                fontSizeSp = tObj.optDouble("fontSizeSp", 26.0).toFloat(),
                textColor = tObj.optString("textColor", "#FFFFFFFF"),
                opacity = tObj.optDouble("opacity", 0.92).toFloat().coerceIn(0f, 1f),
                alignment = tObj.optString("alignment", "start"),
                verticalAlignment = tObj.optString("verticalAlignment", "bottom"),
                backgroundColor = tObj.optString("backgroundColor", "#00000000"),
                paddingDp = tObj.optInt("paddingDp", 8),
                letterSpacing = tObj.optDouble("letterSpacing", 0.03).toFloat(),
                showSeparator = tObj.optBoolean("showSeparator", false),
                separatorColor = tObj.optString("separatorColor", "#33FFFFFF"),
                shadowEnabled = tObj.optBoolean("shadowEnabled", false),
            )

            val catsArr = root.optJSONArray("categories")
            val cats = if (catsArr != null) {
                (0 until catsArr.length()).map { i ->
                    val o = catsArr.getJSONObject(i)
                    CategoryDefinition(
                        id = o.getString("id"),
                        title = o.getString("title"),
                        subtitle = if (o.has("subtitle") && !o.isNull("subtitle")) o.getString("subtitle") else null,
                    )
                }
            } else {
                HomeStudioConfig.defaultCategories()
            }

            val widgetsMap = mutableMapOf<Int, WidgetInstanceConfig>()
            val wArr = root.optJSONArray("widgets")
            if (wArr != null) {
                for (i in 0 until wArr.length()) {
                    val o = wArr.getJSONObject(i)
                    val id = o.getInt("appWidgetId")
                    widgetsMap[id] = WidgetInstanceConfig(
                        appWidgetId = id,
                        categoryId = if (o.has("categoryId") && !o.isNull("categoryId")) o.getString("categoryId") else null,
                        title = o.optString("title", "Category"),
                        subtitle = if (o.has("subtitle") && !o.isNull("subtitle")) o.getString("subtitle") else null,
                        inheritTheme = o.optBoolean("inheritTheme", true),
                        fontFamily = o.optString("fontFamily").takeIf { o.has("fontFamily") && !o.isNull("fontFamily") },
                        fontWeight = if (o.has("fontWeight") && !o.isNull("fontWeight")) o.getInt("fontWeight") else null,
                        fontSizeSp = if (o.has("fontSizeSp") && !o.isNull("fontSizeSp")) o.getDouble("fontSizeSp").toFloat() else null,
                        textColor = o.optString("textColor").takeIf { o.has("textColor") && !o.isNull("textColor") },
                        opacity = if (o.has("opacity") && !o.isNull("opacity")) o.getDouble("opacity").toFloat() else null,
                        alignment = o.optString("alignment").takeIf { o.has("alignment") && !o.isNull("alignment") },
                        verticalAlignment = o.optString("verticalAlignment").takeIf { o.has("verticalAlignment") && !o.isNull("verticalAlignment") },
                        backgroundColor = o.optString("backgroundColor").takeIf { o.has("backgroundColor") && !o.isNull("backgroundColor") },
                        paddingDp = if (o.has("paddingDp") && !o.isNull("paddingDp")) o.getInt("paddingDp") else null,
                        letterSpacing = if (o.has("letterSpacing") && !o.isNull("letterSpacing")) o.getDouble("letterSpacing").toFloat() else null,
                        showSeparator = if (o.has("showSeparator") && !o.isNull("showSeparator")) o.getBoolean("showSeparator") else null,
                        shadowEnabled = if (o.has("shadowEnabled") && !o.isNull("shadowEnabled")) o.getBoolean("shadowEnabled") else null,
                    )
                }
            }

            val prefObj = root.optJSONObject("preferences")
            val prefs = if (prefObj != null) {
                LayoutPreferences(
                    defaultMaxItemsPerPage = prefObj.optInt("defaultMaxItemsPerPage", 8).coerceIn(1, 20),
                    gridColumns = prefObj.optInt("gridColumns", 4).coerceIn(4, 6),
                    gridRows = prefObj.optInt("gridRows", 6).coerceIn(5, 7),
                    onboardingCompleted = prefObj.optBoolean("onboardingCompleted", false),
                    activePresetId = prefObj.optString("activePresetId").takeIf { prefObj.has("activePresetId") && !prefObj.isNull("activePresetId") },
                    appThemeMode = prefObj.optString("appThemeMode", "system").ifBlank { "system" },
                    appColorTheme = prefObj.optString("appColorTheme", "warm-paper").ifBlank { "warm-paper" },
                    syncLauncherIcon = prefObj.optBoolean("syncLauncherIcon", true),
                    layoutViewOrientation = prefObj.optString("layoutViewOrientation", "vertical").ifBlank { "vertical" },
                    showWallpaperInPreview = prefObj.optBoolean("showWallpaperInPreview", true),
                    activeWallpaperSource = prefObj.optString("activeWallpaperSource", "device").ifBlank { "device" },
                    hasCompletedTutorial = prefObj.optBoolean("hasCompletedTutorial", false),
                )
            } else {
                LayoutPreferences()
            }

            val pagesList = mutableListOf<StudioPage>()
            val pagesArr = root.optJSONArray("pages")
            if (pagesArr != null) {
                for (i in 0 until pagesArr.length()) {
                    val po = pagesArr.getJSONObject(i)
                    val ho = po.optJSONObject("header") ?: JSONObject()
                    val header = HeaderPlacement(
                        enabled = ho.optBoolean("enabled", true),
                        row = ho.optInt("row", 0),
                        column = ho.optInt("column", 0),
                        columnSpan = ho.optInt("columnSpan", 4),
                        rowSpan = ho.optInt("rowSpan", 1),
                        verticalAlignment = ho.optString("verticalAlignment", "bottom"),
                        alignmentOverride = ho.optString("alignmentOverride").takeIf { ho.has("alignmentOverride") && !ho.isNull("alignmentOverride") },
                    )
                    val itemsList = mutableListOf<LayoutSlotItem>()
                    val iArr = po.optJSONArray("items")
                    if (iArr != null) {
                        for (j in 0 until iArr.length()) {
                            val io = iArr.getJSONObject(j)
                            val chList = mutableListOf<LayoutFolderChild>()
                            val cArr = io.optJSONArray("children")
                            if (cArr != null) {
                                for (k in 0 until cArr.length()) {
                                    val co = cArr.getJSONObject(k)
                                    chList.add(
                                        LayoutFolderChild(
                                            packageName = co.getString("packageName"),
                                            activityName = co.optString("activityName", ""),
                                            label = co.optString("label", co.getString("packageName")),
                                        ),
                                    )
                                }
                            }
                            itemsList.add(
                                LayoutSlotItem(
                                    id = io.optString("id", "${po.optString("id")}_$j"),
                                    type = io.optString("type", "app"),
                                    label = io.optString("label", ""),
                                    packageName = io.optString("packageName").takeIf { io.has("packageName") && !io.isNull("packageName") },
                                    activityName = io.optString("activityName").takeIf { io.has("activityName") && !io.isNull("activityName") },
                                    children = chList,
                                    row = io.optInt("row", 4),
                                    column = io.optInt("column", 0),
                                ),
                            )
                        }
                    }
                    pagesList.add(
                        StudioPage(
                            id = po.getString("id"),
                            title = po.optString("title", "Page ${i + 1}"),
                            subtitle = po.optString("subtitle").takeIf { po.has("subtitle") && !po.isNull("subtitle") },
                            order = po.optInt("order", i),
                            isDefaultHome = po.optBoolean("isDefaultHome", false),
                            maxItemsPerPage = if (po.has("maxItemsPerPage") && !po.isNull("maxItemsPerPage")) po.getInt("maxItemsPerPage") else null,
                            isWallpaperPage = po.optBoolean("isWallpaperPage", false),
                            header = header,
                            items = itemsList,
                            boundWidgetId = if (po.has("boundWidgetId") && !po.isNull("boundWidgetId")) po.getInt("boundWidgetId") else null,
                        ),
                    )
                }
            }

            val customAppsList = mutableListOf<CustomAppDefinition>()
            val caArr = root.optJSONArray("customApps")
            if (caArr != null) {
                for (i in 0 until caArr.length()) {
                    val o = caArr.getJSONObject(i)
                    val catArr = o.optJSONArray("categories")
                    val cList = if (catArr != null) {
                        (0 until catArr.length()).map { idx -> catArr.getString(idx) }
                    } else {
                        emptyList()
                    }
                    customAppsList.add(
                        CustomAppDefinition(
                            packageName = o.getString("packageName"),
                            displayName = o.optString("displayName", o.getString("packageName")),
                            categories = cList,
                            suiteFolder = o.optString("suiteFolder").takeIf { o.has("suiteFolder") && !o.isNull("suiteFolder") },
                        ),
                    )
                }
            }

            return HomeStudioConfig(
                schemaVersion = schemaVersion,
                globalTheme = theme,
                categories = cats,
                widgets = widgetsMap,
                preferences = prefs,
                pages = pagesList.sortedBy { it.order },
                customApps = customAppsList,
            )
        }

        /** Convert on-device Studio pages to the host DesiredLayout v1 JSON format (`home-studio apply`). */
        fun toHostDesiredLayoutJson(cfg: HomeStudioConfig): String {
            val prefs = cfg.preferences
            val t = cfg.globalTheme
            val root = JSONObject().apply {
                put("schemaVersion", 1)
                put("launcher", JSONObject().apply {
                    put("type", "samsung-one-ui")
                    put("preserveExistingWallpaper", true)
                    put("grid", JSONObject().apply {
                        put("columns", prefs.gridColumns)
                        put("rows", prefs.gridRows)
                    })
                })
                put("theme", JSONObject().apply {
                    put("header", JSONObject().apply {
                        put("fontFamily", t.fontFamily)
                        put("fontWeight", t.fontWeight)
                        put("fontSizeSp", t.fontSizeSp.toDouble())
                        put("textColor", t.textColor)
                        put("opacity", t.opacity.toDouble())
                        put("alignment", t.alignment)
                        put("backgroundColor", t.backgroundColor)
                        put("paddingDp", t.paddingDp)
                        put("letterSpacing", t.letterSpacing.toDouble())
                        put("showSeparator", t.showSeparator)
                        put("separatorColor", t.separatorColor)
                        put("shadowEnabled", t.shadowEnabled)
                    })
                })
                put("wallpaper", JSONObject().apply { put("mode", "preserve") })
                put("dock", JSONObject().apply { put("mode", "preserve"); put("items", JSONArray()) })
            }

            val pagesArr = JSONArray()
            cfg.pages.sortedBy { it.order }.forEachIndexed { idx, pg ->
                val pItems = JSONArray()
                pg.items.forEach { slot ->
                    pItems.put(JSONObject().apply {
                        put("id", slot.id)
                        put("type", slot.type)
                        put("title", slot.label)
                        if (slot.packageName != null) put("packageName", slot.packageName)
                        if (slot.activityName != null) put("activityName", slot.activityName)
                        put("profileId", 0)
                        put("position", JSONObject().apply {
                            put("row", slot.row)
                            put("column", slot.column)
                            put("rowSpan", 1)
                            put("columnSpan", 1)
                        })
                        if (slot.children.isNotEmpty()) {
                            val chArr = JSONArray()
                            slot.children.forEach { ch ->
                                chArr.put(JSONObject().apply {
                                    put("packageName", ch.packageName)
                                    put("activityName", ch.activityName)
                                    put("label", ch.label)
                                    put("profileId", 0)
                                })
                            }
                            put("children", chArr)
                        }
                    })
                }
                pagesArr.put(JSONObject().apply {
                    put("id", pg.id)
                    if (pg.isWallpaperPage) put("title", JSONObject.NULL) else put("title", pg.title)
                    put("order", idx)
                    put("isDefaultHome", pg.isDefaultHome)
                    put("retainEmptyPage", true)
                    put("header", JSONObject().apply {
                        put("enabled", pg.header.enabled && !pg.isWallpaperPage)
                        put("inheritTheme", true)
                        put("position", JSONObject().apply {
                            put("row", pg.header.row)
                            put("column", pg.header.column)
                            put("rowSpan", pg.header.rowSpan)
                            put("columnSpan", pg.header.columnSpan)
                        })
                    })
                    put("items", pItems)
                })
            }
            root.put("pages", pagesArr)
            return root.toString(2)
        }

        /** Parse an observed One UI dump JSON (`observed-layout.json`) or `DesiredLayout` JSON into previewable pages. */
        fun parseObservedSnapshotJson(rawJson: String, cfg: HomeStudioConfig): ObservedSnapshotResult {
            val root = JSONObject(rawJson)
            val pagesArr = root.optJSONArray("pages") ?: JSONArray()
            val oneUiSettings = root.optString("oneUiSettings", "")
            val defaultIdx = Regex("""Default Home Page\s*:\s*(\d+)""")
                .find(oneUiSettings)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

            val pages = mutableListOf<StudioPage>()
            for (i in 0 until pagesArr.length()) {
                val po = pagesArr.getJSONObject(i)
                val pageIdx = if (po.has("page_index")) po.optInt("page_index", i) else po.optInt("order", i)
                val itemsArr = po.optJSONArray("items") ?: JSONArray()

                var headerRow = 0
                var headerEnabled = false
                var headerTitle: String? = po.optString("title").takeIf { po.has("title") && !po.isNull("title") }
                var boundWidgetId: Int? = null
                val slots = mutableListOf<LayoutSlotItem>()

                for (j in 0 until itemsArr.length()) {
                    val io = itemsArr.getJSONObject(j)
                    val type = io.optString("type", "app")
                    val x = if (io.has("position_x")) io.optInt("position_x", 0) else io.optJSONObject("position")?.optInt("column", 0) ?: 0
                    val y = if (io.has("position_y")) io.optInt("position_y", 4) else io.optJSONObject("position")?.optInt("row", 4) ?: 4
                    val comp = io.optString("component", "")
                    val pkg = io.optString("packageName").ifBlank { comp.substringBefore('/') }
                    val cls = io.optString("activityName").ifBlank { comp.substringAfter('/', "") }

                    if (type == "widget") {
                        val wid = io.optInt("app_widget_id", -1)
                        if (comp.contains("CategoryHeaderWidgetProvider")) {
                            headerEnabled = true
                            headerRow = y.coerceIn(0, 5)
                            if (wid > 0) {
                                boundWidgetId = wid
                                headerTitle = cfg.widgets[wid]?.title ?: headerTitle ?: "Page ${i + 1}"
                            }
                        } else if (comp.contains("Weather") || wid > 0) {
                            // Surface other widgets (e.g., Weather) cleanly in grid preview
                            slots.add(
                                LayoutSlotItem(
                                    id = "obs:$pageIdx:w:$j",
                                    type = "widget",
                                    label = if (comp.contains("Weather")) "Weather" else pkg.substringAfterLast('.'),
                                    packageName = pkg,
                                    row = y,
                                    column = x,
                                ),
                            )
                        }
                    } else if (type == "folder") {
                        val chArr = io.optJSONArray("children") ?: JSONArray()
                        val children = mutableListOf<LayoutFolderChild>()
                        for (k in 0 until chArr.length()) {
                            val co = chArr.getJSONObject(k)
                            val cComp = co.optString("component", "")
                            val cPkg = co.optString("packageName").ifBlank { cComp.substringBefore('/') }
                            val cCls = co.optString("activityName").ifBlank { cComp.substringAfter('/', "") }
                            children.add(
                                LayoutFolderChild(
                                    packageName = cPkg,
                                    activityName = cCls,
                                    label = co.optString("title", co.optString("label", cPkg)),
                                ),
                            )
                        }
                        slots.add(
                            LayoutSlotItem(
                                id = "obs:$pageIdx:f:$j",
                                type = "folder",
                                label = io.optString("title", "Folder"),
                                children = children,
                                row = y,
                                column = x,
                            ),
                        )
                    } else if (type == "app") {
                        slots.add(
                            LayoutSlotItem(
                                id = "obs:$pageIdx:a:$j",
                                type = "app",
                                label = io.optString("title", pkg),
                                packageName = pkg,
                                activityName = cls,
                                row = y,
                                column = x,
                            ),
                        )
                    }
                }

                val isWp = !headerEnabled && slots.isEmpty()
                val title = headerTitle ?: if (isWp) "Wallpaper" else "Page ${pageIdx + 1}"
                pages.add(
                    StudioPage(
                        id = po.optString("id", "live-$pageIdx"),
                        title = title,
                        order = pageIdx,
                        isDefaultHome = po.optBoolean("isDefaultHome", pageIdx == defaultIdx),
                        isWallpaperPage = isWp,
                        header = HeaderPlacement(
                            enabled = headerEnabled,
                            row = headerRow,
                            column = 0,
                            columnSpan = cfg.preferences.gridColumns,
                            rowSpan = 1,
                        ),
                        items = slots,
                        boundWidgetId = boundWidgetId,
                    ),
                )
            }

            return ObservedSnapshotResult(
                pages = pages.sortedBy { it.order },
                sourceLabel = "Exact One UI Home snapshot (${pages.size} live pages)",
                isLiveSnapshot = true,
            )
        }
    }
}
