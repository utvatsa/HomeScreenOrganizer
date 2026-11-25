package com.homestudio.local.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Dynamically switches the active Home/App Drawer launcher icon & background
 * via manifest <activity-alias> components to match the selected App Theme.
 */
object IconSwitcher {

    private val ALIAS_BY_THEME_ID: LinkedHashMap<String, String> = linkedMapOf(
        "warm-paper" to "com.homestudio.local.ui.LauncherWarmPaper",
        "samsung-blue" to "com.homestudio.local.ui.LauncherSamsungBlue",
        "nordic-olive" to "com.homestudio.local.ui.LauncherNordicOlive",
        "copper-espresso" to "com.homestudio.local.ui.LauncherCopperEspresso",
        "pacific-cobalt" to "com.homestudio.local.ui.LauncherPacificCobalt",
        "sunlit-ochre" to "com.homestudio.local.ui.LauncherSunlitOchre",
        "arctic-chrome" to "com.homestudio.local.ui.LauncherArcticChrome",
    )

    fun aliasForTheme(themeId: String): String =
        ALIAS_BY_THEME_ID[themeId] ?: ALIAS_BY_THEME_ID.getValue("warm-paper")

    fun allAliasNames(): Collection<String> = ALIAS_BY_THEME_ID.values

    fun applyLauncherIconForTheme(context: Context, themeId: String): Boolean {
        val pm = context.packageManager
        val targetAlias = aliasForTheme(themeId)
        val currentlyEnabled = pm.getComponentEnabledSetting(ComponentName(context, targetAlias))
        if (currentlyEnabled == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            return false
        }
        // Enable target alias BEFORE disabling prior aliases so launcher always finds at least one MAIN/LAUNCHER
        pm.setComponentEnabledSetting(
            ComponentName(context, targetAlias),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        ALIAS_BY_THEME_ID.values.forEach { alias ->
            if (alias != targetAlias) {
                pm.setComponentEnabledSetting(
                    ComponentName(context, alias),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
        return true
    }
}
