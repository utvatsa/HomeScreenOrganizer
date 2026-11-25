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

            return HomeStudioConfig(
                schemaVersion = schemaVersion,
                globalTheme = theme,
                categories = cats,
                widgets = widgetsMap,
            )
        }
    }
}
