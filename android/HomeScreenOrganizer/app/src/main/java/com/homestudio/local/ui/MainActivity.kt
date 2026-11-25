package com.homestudio.local.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homestudio.local.BuildConfig
import com.homestudio.local.catalog.AppCatalogEngine
import com.homestudio.local.data.HomeStudioConfig
import com.homestudio.local.data.HomeStudioStore
import com.homestudio.local.data.WidgetInstanceConfig
import com.homestudio.local.diagnostics.ChecklistAction
import com.homestudio.local.diagnostics.ReadinessChecker
import com.homestudio.local.inventory.InventoryExporter
import com.homestudio.local.planner.LayoutSuggestionEngine
import com.homestudio.local.widget.CategoryHeaderWidgetProvider
import com.homestudio.local.widget.HeaderRenderer
import com.homestudio.local.widget.WallpaperPlaceholderWidgetProvider
import com.homestudio.local.widget.WallpaperSpacer1x1Provider
import com.homestudio.local.widget.WidgetConfigActivity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = HomeStudioStore(this)

        if (BuildConfig.DEBUG && intent?.getBooleanExtra("export_inventory", false) == true) {
            Thread {
                InventoryExporter.exportLauncherApps(applicationContext)
                CategoryHeaderWidgetProvider.updateAll(applicationContext)
            }.start()
        } else if (intent?.getBooleanExtra("refresh_widgets", false) == true) {
            CategoryHeaderWidgetProvider.updateAll(applicationContext)
        } else if (BuildConfig.DEBUG && intent?.getBooleanExtra("pin_header", false) == true) {
            val mgr = AppWidgetManager.getInstance(this)
            if (mgr.isRequestPinAppWidgetSupported) {
                mgr.requestPinAppWidget(ComponentName(this, CategoryHeaderWidgetProvider::class.java), null, null)
            }
        } else if (BuildConfig.DEBUG && intent?.getBooleanExtra("pin_spacer", false) == true) {
            val mgr = AppWidgetManager.getInstance(this)
            if (mgr.isRequestPinAppWidgetSupported) {
                mgr.requestPinAppWidget(ComponentName(this, WallpaperPlaceholderWidgetProvider::class.java), null, null)
            }
        } else if (BuildConfig.DEBUG && intent?.getBooleanExtra("pin_spacer_1x1", false) == true) {
            val mgr = AppWidgetManager.getInstance(this)
            if (mgr.isRequestPinAppWidgetSupported) {
                mgr.requestPinAppWidget(ComponentName(this, WallpaperSpacer1x1Provider::class.java), null, null)
            }
        }

        setContent {
            HomeOrganizerRoot(store)
        }
    }
}

@Composable
private fun HomeOrganizerRoot(store: HomeStudioStore) {
    val context = LocalContext.current
    val catalog = remember { AppCatalogEngine.loadCatalogFromAssets(context) }

    var config by remember {
        val loaded = store.load()
        val seededCats = LayoutSuggestionEngine.ensureCatalogCategoriesSeeded(catalog, loaded.categories)
        val normalized = if (seededCats.size != loaded.categories.size) {
            loaded.copy(categories = seededCats).also { store.save(it) }
        } else {
            loaded
        }
        mutableStateOf(normalized)
    }

    HomeOrganizerTheme(themeMode = config.preferences.appThemeMode) {
        HomeOrganizerApp(
            store = store,
            catalog = catalog,
            config = config,
            onConfigChange = { next -> config = next },
        )
    }
}

@Composable
private fun HomeOrganizerApp(
    store: HomeStudioStore,
    catalog: com.homestudio.local.catalog.AppCatalog,
    config: HomeStudioConfig,
    onConfigChange: (HomeStudioConfig) -> Unit,
) {
    val context = LocalContext.current
    val p = LocalStudioPalette.current

    var tab by remember { mutableIntStateOf(0) }
    var installedApps by remember(config.customApps) {
        mutableStateOf(AppCatalogEngine.discoverInstalledApps(context, catalog, config.customApps))
    }
    var readiness by remember { mutableStateOf(ReadinessChecker.inspect(context)) }
    var showToolsDialog by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("Android Only | Offline & Ready (v${BuildConfig.VERSION_NAME})") }

    val mgr = AppWidgetManager.getInstance(context)
    val boundHeaderIds = remember(tab, showToolsDialog, statusMsg) {
        mgr.getAppWidgetIds(ComponentName(context, CategoryHeaderWidgetProvider::class.java))
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }.onSuccess { text ->
                if (text != null) {
                    val imported = store.importJson(text)
                    onConfigChange(imported)
                    CategoryHeaderWidgetProvider.updateAll(context)
                    readiness = ReadinessChecker.inspect(context)
                    statusMsg = "Imported configuration and refreshed widgets."
                }
            }.onFailure { err ->
                statusMsg = "Import failed: ${err.message}"
            }
        }
    }

    val exportConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(store.exportJson().toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                statusMsg = "Exported HomeScreenOrganizer config JSON."
            }.onFailure { err ->
                statusMsg = "Export failed: ${err.message}"
            }
        }
    }

    val exportHostLayoutLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(HomeStudioStore.toHostDesiredLayoutJson(config).toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                statusMsg = "Exported host DesiredLayout JSON (ready for homescreenorganizer apply)."
            }.onFailure { err ->
                statusMsg = "Layout export failed: ${err.message}"
            }
        }
    }

    val tabs = listOf(
        "Studio" to "S",
        "Catalog" to "C",
        "Settings" to "T",
        "Setup" to "✓",
        "Guide" to "?",
    )

    Scaffold(
        containerColor = p.background,
        bottomBar = {
            NavigationBar(
                containerColor = p.card,
                tonalElevation = 0.dp,
            ) {
                tabs.forEachIndexed { idx, (label, badge) ->
                    val selected = tab == idx
                    val pillScale by animateFloatAsState(
                        targetValue = if (selected) 1.08f else 1.0f,
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
                        label = "navScale",
                    )
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            tab = idx
                            if (idx == 3) readiness = ReadinessChecker.inspect(context)
                        },
                        icon = {
                            Box(
                                modifier = Modifier
                                    .scale(pillScale)
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) p.accentSoft else p.cardElevated),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = badge,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) p.accent else p.textMuted,
                                )
                            }
                        },
                        label = {
                            Text(
                                text = label,
                                maxLines = 1,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) p.textPrimary else p.textMuted,
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Top App Brand Row + Quick Theme Mode Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HomeScreenOrganizer",
                        style = MaterialTheme.typography.titleLarge,
                        color = p.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "One UI Organizer • v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = p.textMuted,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val modeLabel = when (config.preferences.appThemeMode) {
                        "light" -> "Light"
                        "dark" -> "Dark"
                        else -> "Auto"
                    }
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable {
                                val nextMode = when (config.preferences.appThemeMode) {
                                    "system" -> "light"
                                    "light" -> "dark"
                                    else -> "system"
                                }
                                val nextCfg = config.copy(
                                    preferences = config.preferences.copy(appThemeMode = nextMode),
                                )
                                store.save(nextCfg)
                                onConfigChange(nextCfg)
                                statusMsg = "Appearance set to ${nextMode.replaceFirstChar { it.uppercase() }} mode."
                            },
                        shape = RoundedCornerShape(999.dp),
                        color = p.secondarySoft,
                        border = BorderStroke(1.dp, p.border),
                    ) {
                        Text(
                            text = modeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = p.secondaryAccent,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            // Animated Tinted Live Status Pill
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                shape = RoundedCornerShape(12.dp),
                color = p.cardElevated,
                border = BorderStroke(1.dp, p.border),
            ) {
                AnimatedContent(
                    targetState = statusMsg,
                    transitionSpec = {
                        (fadeIn(tween(180)) togetherWith fadeOut(tween(120)))
                    },
                    label = "status",
                ) { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(p.accent),
                        )
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = p.textSecondary,
                        )
                    }
                }
            }

            // Animated Tab Content Switcher
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 18 })
                        .togetherWith(fadeOut(tween(140)))
                },
                label = "tabBody",
            ) { activeTab ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (activeTab) {
                        0 -> LayoutsStudioTab(
                            config = config,
                            installedApps = installedApps,
                            usageTopApps = readiness.topUsedApps,
                            boundHeaderIds = boundHeaderIds,
                            onUpdateConfig = { updated, msg ->
                                store.save(updated)
                                onConfigChange(updated)
                                CategoryHeaderWidgetProvider.updateAll(context)
                                statusMsg = msg
                            },
                            onPinHeaderForPage = {
                                if (mgr.isRequestPinAppWidgetSupported) {
                                    mgr.requestPinAppWidget(ComponentName(context, CategoryHeaderWidgetProvider::class.java), null, null)
                                    statusMsg = "Requested pin Category Header widget for '${it.title}'."
                                } else {
                                    statusMsg = "Launcher pinning unsupported; add Category Header from Home Widgets."
                                }
                            },
                            onExportHostLayout = { exportHostLayoutLauncher.launch("desired-layout.json") },
                            onOpenTools = { showToolsDialog = true },
                        )

                        1 -> CustomCatalogTab(
                            installedApps = installedApps,
                            categories = config.categories,
                            onSaveCustomApp = { customDef, newCat ->
                                val nextCustoms = config.customApps.filterNot { it.packageName == customDef.packageName } + customDef
                                val nextCats = if (newCat != null && config.categories.none { it.id == newCat.id }) {
                                    config.categories + newCat
                                } else {
                                    config.categories
                                }
                                val nextCfg = config.copy(customApps = nextCustoms, categories = nextCats)
                                store.save(nextCfg)
                                onConfigChange(nextCfg)
                                installedApps = AppCatalogEngine.discoverInstalledApps(context, catalog, nextCustoms)
                                statusMsg = "Saved custom categories for ${customDef.displayName}."
                            },
                        )

                        2 -> SettingsAndThemeTab(
                            config = config,
                            onUpdate = { updated, msg ->
                                store.save(updated)
                                onConfigChange(updated)
                                CategoryHeaderWidgetProvider.updateAll(context)
                                statusMsg = msg
                            },
                            onOpenTools = { showToolsDialog = true },
                        )

                        3 -> SetupChecklistTab(
                            report = readiness,
                            onAction = { action ->
                                when (action) {
                                    ChecklistAction.REFRESH -> {
                                        readiness = ReadinessChecker.inspect(context)
                                        statusMsg = "Checklist refreshed."
                                    }
                                    ChecklistAction.OPEN_HOME_SETTINGS -> {
                                        runCatching {
                                            context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                                        }.onFailure { statusMsg = "Open Android Settings > Apps > Default apps > Home app." }
                                    }
                                    ChecklistAction.OPEN_USAGE_ACCESS -> {
                                        runCatching {
                                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                        }.onFailure { statusMsg = "Open Android Settings > Apps > Special access > Usage data access." }
                                    }
                                    ChecklistAction.PIN_HEADER_WIDGET -> {
                                        if (mgr.isRequestPinAppWidgetSupported) {
                                            mgr.requestPinAppWidget(ComponentName(context, CategoryHeaderWidgetProvider::class.java), null, null)
                                            statusMsg = "Requested pin Category Header widget."
                                        }
                                    }
                                    ChecklistAction.NONE -> Unit
                                }
                            },
                            onOpenGuide = { tab = 4 },
                        )

                        4 -> GuideTab()
                    }
                }
            }
        }
    }

    if (showToolsDialog) {
        WidgetsToolsDialog(
            config = config,
            onDismiss = { showToolsDialog = false },
            onStatus = { msg ->
                statusMsg = msg
                readiness = ReadinessChecker.inspect(context)
            },
            onReload = {
                onConfigChange(store.load())
                CategoryHeaderWidgetProvider.updateAll(context)
                readiness = ReadinessChecker.inspect(context)
                statusMsg = "Reloaded local config and refreshed widgets."
            },
            onExportInventory = {
                val count = InventoryExporter.exportLauncherApps(context)
                statusMsg = "Exported $count launcher apps + icons."
            },
            onImportJson = { importLauncher.launch(arrayOf("application/json", "*/*")) },
            onExportConfig = { exportConfigLauncher.launch("home-organizer-config.json") },
            onExportHostLayout = { exportHostLayoutLauncher.launch("desired-layout.json") },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsAndThemeTab(
    config: HomeStudioConfig,
    onUpdate: (HomeStudioConfig, String) -> Unit,
    onOpenTools: () -> Unit,
) {
    val context = LocalContext.current
    val p = LocalStudioPalette.current
    val t = config.globalTheme

    // App Appearance Mode (System / Light / Dark)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = p.card),
        border = BorderStroke(1.dp, p.border),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("App Appearance (Light / Dark Mode)", style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
            Text(
                text = "Switch between crisp Light, Obsidian Dark, or match your Android system setting.",
                style = MaterialTheme.typography.bodySmall,
                color = p.textSecondary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "System Default", "light" to "Light Mode", "dark" to "Dark Mode").forEach { (id, label) ->
                    FilterChip(
                        selected = config.preferences.appThemeMode == id,
                        onClick = {
                            onUpdate(
                                config.copy(preferences = config.preferences.copy(appThemeMode = id)),
                                "Switched appearance to $label.",
                            )
                        },
                        label = { Text(label) },
                    )
                }
            }
        }
    }

    // Category Header Global Widget Styling
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = p.card),
        border = BorderStroke(1.dp, p.border),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Global Category Header Widget Theme", style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
            val previewBmp = remember(t) {
                HeaderRenderer.render(
                    context,
                    WidgetInstanceConfig(appWidgetId = 0, title = "Payments Suite"),
                    t,
                    340,
                    72,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .background(p.wallpaperPreviewBg, RoundedCornerShape(12.dp))
                    .border(1.dp, p.wallpaperPreviewBorder, RoundedCornerShape(12.dp))
                    .padding(8.dp),
            ) {
                Image(bitmap = previewBmp.asImageBitmap(), contentDescription = "Header Preview", modifier = Modifier.fillMaxSize())
            }

            Text("Font Size: ${t.fontSizeSp.toInt()} sp", color = p.textPrimary, style = MaterialTheme.typography.labelLarge)
            @Suppress("DEPRECATION")
            Slider(
                value = t.fontSizeSp,
                onValueChange = {
                    onUpdate(config.copy(globalTheme = t.copy(fontSizeSp = it)), "Updated header font size to ${it.toInt()} sp.")
                },
                valueRange = 16f..36f,
            )

            Text("Opacity: ${(t.opacity * 100).toInt()}%", color = p.textPrimary, style = MaterialTheme.typography.labelLarge)
            @Suppress("DEPRECATION")
            Slider(
                value = t.opacity,
                onValueChange = {
                    onUpdate(config.copy(globalTheme = t.copy(opacity = it)), "Updated header opacity.")
                },
                valueRange = 0.3f..1.0f,
            )

            Text("Font Weight: ${t.fontWeight}", color = p.textPrimary, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(200, 300, 400, 500, 700).forEach { w ->
                    FilterChip(
                        selected = t.fontWeight == w,
                        onClick = { onUpdate(config.copy(globalTheme = t.copy(fontWeight = w)), "Header weight set to $w.") },
                        label = { Text("$w") },
                    )
                }
            }

            Text("Alignment", color = p.textPrimary, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("start", "center", "end").forEach { a ->
                    FilterChip(
                        selected = t.alignment == a,
                        onClick = { onUpdate(config.copy(globalTheme = t.copy(alignment = a)), "Header alignment set to $a.") },
                        label = { Text(a) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Subtle Separator Line", color = p.textPrimary)
                Switch(
                    checked = t.showSeparator,
                    onCheckedChange = {
                        onUpdate(config.copy(globalTheme = t.copy(showSeparator = it)), "Updated separator line.")
                    },
                )
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = p.card),
        border = BorderStroke(1.dp, p.border),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Widgets, Backup & Export", style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
            Text(
                text = "Pin Category Header or Spacer widgets to One UI Home, inspect bound widget IDs, or export/import layouts cleanly.",
                style = MaterialTheme.typography.bodySmall,
                color = p.textSecondary,
            )
            Button(onClick = onOpenTools, modifier = Modifier.fillMaxWidth()) {
                Text("Open Widgets & Backup Tools")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetsToolsDialog(
    config: HomeStudioConfig,
    onDismiss: () -> Unit,
    onStatus: (String) -> Unit,
    onReload: () -> Unit,
    onExportInventory: () -> Unit,
    onImportJson: () -> Unit,
    onExportConfig: () -> Unit,
    onExportHostLayout: () -> Unit,
) {
    val context = LocalContext.current
    val p = LocalStudioPalette.current
    val mgr = AppWidgetManager.getInstance(context)
    val headerIds = mgr.getAppWidgetIds(ComponentName(context, CategoryHeaderWidgetProvider::class.java))
    val placeholderIds = mgr.getAppWidgetIds(ComponentName(context, WallpaperPlaceholderWidgetProvider::class.java))
    val spacer1x1Ids = mgr.getAppWidgetIds(ComponentName(context, WallpaperSpacer1x1Provider::class.java))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Widgets & Backup Tools") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (mgr.isRequestPinAppWidgetSupported) {
                            mgr.requestPinAppWidget(ComponentName(context, CategoryHeaderWidgetProvider::class.java), null, null)
                            onStatus("Requested pin Category Header widget.")
                        } else {
                            onStatus("Launcher does not support requestPinAppWidget.")
                        }
                    }) { Text("Pin Header Widget") }

                    OutlinedButton(onClick = {
                        if (mgr.isRequestPinAppWidgetSupported) {
                            mgr.requestPinAppWidget(ComponentName(context, WallpaperPlaceholderWidgetProvider::class.java), null, null)
                            onStatus("Requested pin Wallpaper Spacer (4x1).")
                        }
                    }) { Text("Pin Spacer 4x1") }

                    OutlinedButton(onClick = {
                        if (mgr.isRequestPinAppWidgetSupported) {
                            mgr.requestPinAppWidget(ComponentName(context, WallpaperSpacer1x1Provider::class.java), null, null)
                            onStatus("Requested pin Wallpaper Spacer (1x1).")
                        }
                    }) { Text("Pin Spacer 1x1") }
                }

                Text("Bound Header Widgets (${headerIds.size})", style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                headerIds.forEach { id ->
                    val w = config.widgets[id]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = p.cardElevated),
                        border = BorderStroke(1.dp, p.border),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(w?.title ?: "Unconfigured #$id", style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                                Text("appWidgetId=$id | inherit=${w?.inheritTheme ?: true}", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
                            }
                            OutlinedButton(onClick = {
                                context.startActivity(
                                    Intent(context, WidgetConfigActivity::class.java)
                                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                                )
                            }) { Text("Edit") }
                        }
                    }
                }

                Text(
                    text = "Bound Spacers: ${placeholderIds.size} (4x1), ${spacer1x1Ids.size} (1x1)",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textMuted,
                )

                Button(onClick = onReload, modifier = Modifier.fillMaxWidth()) {
                    Text("Reload Config & Refresh Widgets")
                }
                OutlinedButton(onClick = onExportHostLayout, modifier = Modifier.fillMaxWidth()) {
                    Text("Export Host DesiredLayout JSON")
                }
                OutlinedButton(onClick = onExportConfig, modifier = Modifier.fillMaxWidth()) {
                    Text("Export App Config JSON")
                }
                OutlinedButton(onClick = onImportJson, modifier = Modifier.fillMaxWidth()) {
                    Text("Import App Config JSON")
                }
                OutlinedButton(onClick = onExportInventory, modifier = Modifier.fillMaxWidth()) {
                    Text("Export Installed App Inventory")
                }
                Text(
                    text = "HomeScreenOrganizer v${BuildConfig.VERSION_NAME} | Android only | Offline, zero analytics, $0 cost.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textMuted,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
