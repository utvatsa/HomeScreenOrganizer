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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homestudio.local.BuildConfig
import com.homestudio.local.data.CategoryDefinition
import com.homestudio.local.data.HomeStudioConfig
import com.homestudio.local.data.HomeStudioStore
import com.homestudio.local.data.WidgetInstanceConfig
import com.homestudio.local.diagnostics.ChecklistAction
import com.homestudio.local.diagnostics.ReadinessChecker
import com.homestudio.local.inventory.InventoryExporter
import com.homestudio.local.widget.CategoryHeaderWidgetProvider
import com.homestudio.local.widget.HeaderRenderer
import com.homestudio.local.widget.WallpaperPlaceholderWidgetProvider
import com.homestudio.local.widget.WallpaperSpacer1x1Provider
import com.homestudio.local.widget.WidgetConfigActivity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = HomeStudioStore(this)

        // Optional launch-time automation extras for host orchestration in debug builds
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
            MaterialTheme(colorScheme = darkColorScheme()) {
                HomeStudioApp(store)
            }
        }
    }
}

@Composable
private fun HomeStudioApp(store: HomeStudioStore) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var config by remember { mutableStateOf(store.load()) }
    var readiness by remember { mutableStateOf(ReadinessChecker.inspect(context)) }
    var statusMsg by remember { mutableStateOf("Offline & Ready (v${BuildConfig.VERSION_NAME})") }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }.onSuccess { text ->
                if (text != null) {
                    config = store.importJson(text)
                    CategoryHeaderWidgetProvider.updateAll(context)
                    readiness = ReadinessChecker.inspect(context)
                    statusMsg = "Imported configuration and refreshed widgets."
                }
            }.onFailure { err ->
                statusMsg = "Import failed: ${err.message}"
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(store.exportJson().toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                statusMsg = "Exported configuration JSON."
            }.onFailure { err ->
                statusMsg = "Export failed: ${err.message}"
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("Checklist", "Guide", "Headers", "Theme", "Tools").forEachIndexed { idx, label ->
                    NavigationBarItem(
                        selected = tab == idx,
                        onClick = {
                            tab = idx
                            if (idx == 0) {
                                readiness = ReadinessChecker.inspect(context)
                            }
                        },
                        icon = {},
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Home Studio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1F273B), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFAEC6FF),
                    )
                }
            }
            Text(statusMsg, style = MaterialTheme.typography.bodySmall, color = Color(0xFFA0A0B0))

            when (tab) {
                0 -> SetupChecklistTab(
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
                                val mgr = AppWidgetManager.getInstance(context)
                                if (mgr.isRequestPinAppWidgetSupported) {
                                    mgr.requestPinAppWidget(ComponentName(context, CategoryHeaderWidgetProvider::class.java), null, null)
                                    statusMsg = "Requested pin Category Header widget."
                                }
                            }
                            ChecklistAction.NONE -> Unit
                        }
                    },
                    onOpenGuide = { tab = 1 },
                )
                1 -> GuideTab()
                2 -> CategoriesTab(
                    config = config,
                    onUpdate = { updated ->
                        store.save(updated)
                        config = updated
                        CategoryHeaderWidgetProvider.updateAll(context)
                        statusMsg = "Categories updated."
                    },
                )
                3 -> ThemeTab(
                    config = config,
                    onUpdate = { updated ->
                        store.save(updated)
                        config = updated
                        CategoryHeaderWidgetProvider.updateAll(context)
                        statusMsg = "Global theme applied to inheriting widgets."
                    },
                )
                4 -> WidgetsToolsTab(
                    config = config,
                    onStatus = { msg ->
                        statusMsg = msg
                        readiness = ReadinessChecker.inspect(context)
                    },
                    onReload = {
                        config = store.load()
                        CategoryHeaderWidgetProvider.updateAll(context)
                        readiness = ReadinessChecker.inspect(context)
                        statusMsg = "Reloaded config and refreshed widgets."
                    },
                    onExportInventory = {
                        val count = InventoryExporter.exportLauncherApps(context)
                        statusMsg = "Exported $count launcher apps + icons."
                    },
                    onImportJson = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    onExportJson = { exportLauncher.launch("home-studio-config.json") },
                )
            }
        }
    }
}

@Composable
private fun CategoriesTab(config: HomeStudioConfig, onUpdate: (HomeStudioConfig) -> Unit) {
    var newTitle by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newTitle,
            onValueChange = { newTitle = it },
            label = { Text("New Category") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        Button(onClick = {
            val clean = newTitle.trim()
            if (clean.isNotEmpty()) {
                val id = clean.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                if (id.isNotEmpty() && config.categories.none { it.id == id }) {
                    onUpdate(config.copy(categories = config.categories + CategoryDefinition(id, clean)))
                    newTitle = ""
                }
            }
        }) { Text("Add") }
    }

    config.categories.forEach { cat ->
        val boundCount = config.widgets.values.count { it.categoryId == cat.id || it.title == cat.title }
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(cat.title, style = MaterialTheme.typography.titleMedium)
                    Text("id: ${cat.id} | widgets: $boundCount", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeTab(config: HomeStudioConfig, onUpdate: (HomeStudioConfig) -> Unit) {
    val context = LocalContext.current
    val t = config.globalTheme
    val previewBmp = remember(t) {
        HeaderRenderer.render(
            context,
            WidgetInstanceConfig(appWidgetId = 0, title = "Most Used"),
            t,
            340,
            72,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .background(Color(0xFF0E0E10), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF2C2C32), RoundedCornerShape(12.dp))
            .padding(8.dp),
    ) {
        Image(bitmap = previewBmp.asImageBitmap(), contentDescription = "Preview", modifier = Modifier.fillMaxSize())
    }

    Text("Font Size: ${t.fontSizeSp.toInt()} sp")
    Slider(
        value = t.fontSizeSp,
        onValueChange = { onUpdate(config.copy(globalTheme = t.copy(fontSizeSp = it))) },
        valueRange = 16f..36f,
    )

    Text("Opacity: ${(t.opacity * 100).toInt()}%")
    Slider(
        value = t.opacity,
        onValueChange = { onUpdate(config.copy(globalTheme = t.copy(opacity = it))) },
        valueRange = 0.3f..1.0f,
    )

    Text("Font Weight: ${t.fontWeight}")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(200, 300, 400, 500, 700).forEach { w ->
            FilterChip(
                selected = t.fontWeight == w,
                onClick = { onUpdate(config.copy(globalTheme = t.copy(fontWeight = w))) },
                label = { Text("$w") },
            )
        }
    }

    Text("Alignment")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("start", "center", "end").forEach { a ->
            FilterChip(
                selected = t.alignment == a,
                onClick = { onUpdate(config.copy(globalTheme = t.copy(alignment = a))) },
                label = { Text(a) },
            )
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Subtle Separator")
        Switch(
            checked = t.showSeparator,
            onCheckedChange = { onUpdate(config.copy(globalTheme = t.copy(showSeparator = it))) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetsToolsTab(
    config: HomeStudioConfig,
    onStatus: (String) -> Unit,
    onReload: () -> Unit,
    onExportInventory: () -> Unit,
    onImportJson: () -> Unit,
    onExportJson: () -> Unit,
) {
    val context = LocalContext.current
    val mgr = AppWidgetManager.getInstance(context)
    val headerIds = mgr.getAppWidgetIds(ComponentName(context, CategoryHeaderWidgetProvider::class.java))
    val placeholderIds = mgr.getAppWidgetIds(ComponentName(context, WallpaperPlaceholderWidgetProvider::class.java))
    val spacer1x1Ids = mgr.getAppWidgetIds(ComponentName(context, WallpaperSpacer1x1Provider::class.java))

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

    Text("Bound Header Widgets (${headerIds.size})", style = MaterialTheme.typography.titleMedium)
    headerIds.forEach { id ->
        val w = config.widgets[id]
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(w?.title ?: "Unconfigured #$id", style = MaterialTheme.typography.titleSmall)
                    Text("appWidgetId=$id | inherit=${w?.inheritTheme ?: true}", style = MaterialTheme.typography.bodySmall)
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
        color = Color.Gray,
    )

    Text("Backup & Utilities", style = MaterialTheme.typography.titleMedium)
    Button(onClick = onReload, modifier = Modifier.fillMaxWidth()) {
        Text("Reload Local Config & Refresh Widgets")
    }
    OutlinedButton(onClick = onExportJson, modifier = Modifier.fillMaxWidth()) {
        Text("Export Config JSON (SAF)")
    }
    OutlinedButton(onClick = onImportJson, modifier = Modifier.fillMaxWidth()) {
        Text("Import Config JSON (SAF)")
    }
    OutlinedButton(onClick = onExportInventory, modifier = Modifier.fillMaxWidth()) {
        Text("Export Launcher App Inventory & Icons")
    }
    Text(
        text = "Home Studio v${BuildConfig.VERSION_NAME} | 100% offline, zero analytics, $0 cost.",
        style = MaterialTheme.typography.bodySmall,
        color = Color.Gray,
    )
}
