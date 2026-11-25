package com.homestudio.local

import com.homestudio.local.data.HeaderTheme
import com.homestudio.local.data.HomeStudioConfig
import com.homestudio.local.data.HomeStudioStore
import com.homestudio.local.data.WidgetInstanceConfig
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
                )
            ),
        )
        val json = HomeStudioStore.toJson(original)
        val restored = HomeStudioStore.fromJson(json)
        assertEquals(28f, restored.globalTheme.fontSizeSp)
        assertEquals(200, restored.globalTheme.fontWeight)
        assertEquals("Payments", restored.widgets[42]?.title)
        assertFalse(restored.widgets[42]?.inheritTheme ?: true)
        assertEquals(22f, restored.widgets[42]?.fontSizeSp)
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
}
