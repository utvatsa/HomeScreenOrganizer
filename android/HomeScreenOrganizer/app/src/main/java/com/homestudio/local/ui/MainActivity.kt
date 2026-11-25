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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.homestudio.local.BuildConfig
import com.homestudio.local.catalog.AppCatalogEngine
import com.homestudio.local.data.HomeStudioConfig
import com.homestudio.local.data.HomeStudioStore
import com.homestudio.local.data.StudioPage
import com.homestudio.local.data.WidgetInstanceConfig
import com.homestudio.local.diagnostics.ChecklistAction
import com.homestudio.local.diagnostics.ReadinessChecker
import com.homestudio.local.inventory.InventoryExporter
import com.homestudio.local.planner.LayoutSuggestionEngine
import com.homestudio.local.wallpaper.WallpaperEngine
import com.homestudio.local.widget.CategoryHeaderWidgetProvider
import com.homestudio.local.widget.HeaderRenderer
import com.homestudio.local.widget.WallpaperPlaceholderWidgetProvider
import com.homestudio.local.widget.WallpaperSpacer1x1Provider
import com.homestudio.local.widget.WidgetConfigActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class OrganizerDest(val title: String) {
    STUDIO("Studio"),
    CURRENT("Current"),
    CATALOG("Catalog"),
    SETTINGS("Settings"),
    SETUP("Setup"),
    GUIDE("Guide"),
    ABOUT("About"),
}

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

        val initialDestName = intent?.getStringExtra("start_dest")
        setContent {
            HomeOrganizerRoot(store, initialDestName)
        }
    }
}

@Composable
private fun HomeOrganizerRoot(store: HomeStudioStore, startDestName: String?) {
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
            startDestName = startDestName,
            onConfigChange = { next -> config = next },
        )
    }
}

@Composable
private fun HomeOrganizerApp(
    store: HomeStudioStore,
    catalog: com.homestudio.local.catalog.AppCatalog,
    config: HomeStudioConfig,
    startDestName: String?,
    onConfigChange: (HomeStudioConfig) -> Unit,
) {
    val context = LocalContext.current
    val p = LocalStudioPalette.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val initialReadiness = remember { ReadinessChecker.inspect(context) }
    var readiness by remember { mutableStateOf(initialReadiness) }

    val startAtSetup = remember {
        val explicit = OrganizerDest.entries.firstOrNull { it.name.equals(startDestName, ignoreCase = true) }
        explicit ?: if (!initialReadiness.allRequiredReady) OrganizerDest.SETUP else OrganizerDest.STUDIO
    }
    var dest by remember { mutableStateOf(startAtSetup) }
    var isFirstRunGate by remember { mutableStateOf(!initialReadiness.allRequiredReady) }

    var installedApps by remember(config.customApps) {
        mutableStateOf(AppCatalogEngine.discoverInstalledApps(context, catalog, config.customApps))
    }
    var observedSnapshot by remember(config.widgets) {
        mutableStateOf(store.loadObservedSnapshot(config))
    }
    var wallpaperBitmap by remember(config.preferences.activeWallpaperSource) {
        mutableStateOf(WallpaperEngine.loadActiveWallpaperBitmap(context, config.preferences.activeWallpaperSource))
    }

    var showToolsDialog by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var wallpaperPreviewPage by remember { mutableStateOf<StudioPage?>(null) }
    var showSplash by remember { mutableStateOf(true) }
    var showTutorial by remember { mutableStateOf(false) }
    var tutorialStepIndex by remember { mutableIntStateOf(0) }
    val tutorialRegistry = remember { TutorialTargetRegistry() }
    val mainScrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        delay(850L)
        showSplash = false
        if (!config.preferences.hasCompletedTutorial) {
            tutorialStepIndex = 0
            showTutorial = true
        }
    }

    LaunchedEffect(showTutorial, tutorialStepIndex) {
        if (showTutorial && tutorialStepIndex in VERBOSE_TUTORIAL_STEPS.indices) {
            val step = VERBOSE_TUTORIAL_STEPS[tutorialStepIndex]
            drawerState.close()
            dest = step.dest
            mainScrollState.animateScrollTo(if (step.scrollToBottom) 760 else 0)
        }
    }
    var statusMsg by remember {
        mutableStateOf(
            if (!initialReadiness.allRequiredReady) {
                "First-load check: complete pending Setup items below."
            } else {
                "One UI 9 Ready • ${observedSnapshot.pages.size} live / ${config.pages.size} studio pages"
            },
        )
    }

    // Auto-refresh readiness & live widget bindings whenever user returns from Settings/Launcher
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val refreshed = ReadinessChecker.inspect(context)
                readiness = refreshed
                observedSnapshot = store.loadObservedSnapshot(config)
                wallpaperBitmap = WallpaperEngine.loadActiveWallpaperBitmap(context, config.preferences.activeWallpaperSource)
                if (refreshed.allRequiredReady) isFirstRunGate = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val mgr = AppWidgetManager.getInstance(context)
    val boundHeaderIds = remember(dest, showToolsDialog, statusMsg) {
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

    val importObservedLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }.onSuccess { text ->
                if (text != null) {
                    observedSnapshot = store.saveObservedSnapshotJson(text)
                    statusMsg = "Loaded ${observedSnapshot.pages.size} pages into Current Layout viewer."
                    dest = OrganizerDest.CURRENT
                }
            }.onFailure { err ->
                statusMsg = "Snapshot import failed: ${err.message}"
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

    Box(modifier = Modifier.fillMaxSize()) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = p.card,
                drawerContentColor = p.textPrimary,
                modifier = Modifier.width(304.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = p.cardTint,
                        border = BorderStroke(1.dp, p.border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "HomeScreenOrganizer",
                                style = MaterialTheme.typography.titleMedium,
                                color = p.textPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} • Samsung One UI Studio",
                                style = MaterialTheme.typography.labelSmall,
                                color = p.secondaryAccent,
                            )
                        }
                    }

                    Text("WORKSPACE", style = MaterialTheme.typography.labelSmall, color = p.textMuted, modifier = Modifier.padding(top = 6.dp, start = 8.dp))
                    listOf(
                        Triple(OrganizerDest.STUDIO, "Layout Studio", "${config.pages.size}"),
                        Triple(OrganizerDest.CURRENT, "Current Phone Layout", "${observedSnapshot.pages.size}"),
                        Triple(OrganizerDest.CATALOG, "App Catalog & Tagging", "${installedApps.size}"),
                    ).forEach { (d, label, badge) ->
                        DrawerNavItem(
                            label = label,
                            badge = badge,
                            selected = dest == d,
                            onClick = {
                                dest = d
                                scope.launch { drawerState.close() }
                            },
                        )
                    }

                    HorizontalDivider(color = p.border, modifier = Modifier.padding(vertical = 4.dp))
                    Text("SYSTEM & PREFERENCES", style = MaterialTheme.typography.labelSmall, color = p.textMuted, modifier = Modifier.padding(start = 8.dp))

                    DrawerNavItem(
                        label = "Settings & Theme",
                        badge = config.preferences.appThemeMode.replaceFirstChar { it.uppercase() },
                        selected = dest == OrganizerDest.SETTINGS,
                        onClick = {
                            dest = OrganizerDest.SETTINGS
                            scope.launch { drawerState.close() }
                        },
                    )
                    DrawerNavItem(
                        label = "Setup Checklist",
                        badge = "${readiness.readyRequiredCount}/${readiness.requiredItems.size}",
                        badgePositive = readiness.allRequiredReady,
                        selected = dest == OrganizerDest.SETUP,
                        onClick = {
                            readiness = ReadinessChecker.inspect(context)
                            dest = OrganizerDest.SETUP
                            scope.launch { drawerState.close() }
                        },
                    )
                    DrawerNavItem(
                        label = "User Guide",
                        badge = null,
                        selected = dest == OrganizerDest.GUIDE,
                        onClick = {
                            dest = OrganizerDest.GUIDE
                            scope.launch { drawerState.close() }
                        },
                    )
                    DrawerNavItem(
                        label = "Interactive Tutorial",
                        badge = "Tour",
                        badgePositive = true,
                        selected = showTutorial,
                        onClick = {
                            scope.launch { drawerState.close() }
                            tutorialStepIndex = 0
                            showTutorial = true
                        },
                    )
                    DrawerNavItem(
                        label = "About App",
                        badge = "✨",
                        selected = dest == OrganizerDest.ABOUT,
                        onClick = {
                            dest = OrganizerDest.ABOUT
                            scope.launch { drawerState.close() }
                        },
                    )
                }
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(p.background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(mainScrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(modifier = Modifier.height(6.dp))

                // Modern Top Header Bar with Hamburger Icon (≡) + Quick Light/Dark Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .tutorialTarget(tutorialRegistry, "hamburger_btn")
                            .clip(CircleShape)
                            .clickable { scope.launch { drawerState.open() } },
                        shape = CircleShape,
                        color = p.card.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, p.border),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .clip(CircleShape)
                                        .background(p.textPrimary),
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "HomeScreenOrganizer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = p.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${dest.title} • v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = p.textMuted,
                        )
                    }

                    val modeLabel = when (config.preferences.appThemeMode) {
                        "light" -> "Light"
                        "dark" -> "Dark"
                        else -> "Auto"
                    }
                    Surface(
                        modifier = Modifier
                            .tutorialTarget(tutorialRegistry, "theme_pill")
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
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }

                // Subtle Animated Status Strip
                Surface(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    shape = RoundedCornerShape(14.dp),
                    color = p.card,
                    border = BorderStroke(1.dp, p.border),
                ) {
                    AnimatedContent(
                        targetState = statusMsg,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "statusMsg",
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

                AnimatedContent(
                    targetState = dest,
                    transitionSpec = {
                        (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                            slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 20 })
                            .togetherWith(fadeOut(tween(140)))
                    },
                    label = "destBody",
                ) { activeDest ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (activeDest) {
                            OrganizerDest.STUDIO -> LayoutsStudioTab(
                                config = config,
                                installedApps = installedApps,
                                usageTopApps = readiness.topUsedApps,
                                boundHeaderIds = boundHeaderIds,
                                wallpaperBitmap = wallpaperBitmap,
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
                                onOpenWallpaperPicker = { page ->
                                    wallpaperPreviewPage = page
                                    showWallpaperDialog = true
                                },
                                tutorialRegistry = tutorialRegistry,
                            )

                            OrganizerDest.CURRENT -> CurrentLayoutTab(
                                config = config,
                                snapshot = observedSnapshot,
                                wallpaperBitmap = wallpaperBitmap,
                                onChangeOrientation = { ori ->
                                    val next = config.copy(
                                        preferences = config.preferences.copy(layoutViewOrientation = ori),
                                    )
                                    store.save(next)
                                    onConfigChange(next)
                                    statusMsg = "Switched layout viewer to ${ori.replaceFirstChar { it.uppercase() }} mode."
                                },
                                onToggleWallpaper = { enabled ->
                                    val next = config.copy(
                                        preferences = config.preferences.copy(showWallpaperInPreview = enabled),
                                    )
                                    store.save(next)
                                    onConfigChange(next)
                                    statusMsg = if (enabled) "Showing layouts with wallpaper." else "Showing clean backdrop without wallpaper."
                                },
                                onOpenWallpaperPicker = {
                                    wallpaperPreviewPage = observedSnapshot.pages.firstOrNull { it.isDefaultHome }
                                        ?: observedSnapshot.pages.firstOrNull()
                                    showWallpaperDialog = true
                                },
                                onImportObservedJson = { importObservedLauncher.launch(arrayOf("application/json", "*/*")) },
                                onRefreshSnapshot = {
                                    observedSnapshot = store.loadObservedSnapshot(config)
                                    wallpaperBitmap = WallpaperEngine.loadActiveWallpaperBitmap(context, config.preferences.activeWallpaperSource)
                                    statusMsg = "Refreshed Current Layout snapshot (${observedSnapshot.pages.size} pages)."
                                },
                                onCloneToStudio = { pages ->
                                    val cloned = LayoutSuggestionEngine.syncCategoriesAndWidgetsFromPages(
                                        config.copy(pages = LayoutSuggestionEngine.normalizePageOrders(pages)),
                                    )
                                    store.save(cloned)
                                    onConfigChange(cloned)
                                    dest = OrganizerDest.STUDIO
                                    statusMsg = "Loaded ${cloned.pages.size} current pages into Studio for editing."
                                },
                                tutorialRegistry = tutorialRegistry,
                            )

                            OrganizerDest.CATALOG -> CustomCatalogTab(
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
                                tutorialRegistry = tutorialRegistry,
                            )

                            OrganizerDest.SETTINGS -> SettingsAndThemeTab(
                                config = config,
                                onUpdate = { updated, msg ->
                                    store.save(updated)
                                    onConfigChange(updated)
                                    CategoryHeaderWidgetProvider.updateAll(context)
                                    statusMsg = msg
                                },
                                onOpenTools = { showToolsDialog = true },
                                onOpenWallpaperPicker = {
                                    wallpaperPreviewPage = config.pages.firstOrNull { it.isDefaultHome }
                                    showWallpaperDialog = true
                                },
                            )

                            OrganizerDest.SETUP -> SetupChecklistTab(
                                report = readiness,
                                isFirstRunGate = isFirstRunGate,
                                onAction = { action ->
                                    when (action) {
                                        ChecklistAction.REFRESH -> {
                                            readiness = ReadinessChecker.inspect(context)
                                            if (readiness.allRequiredReady) isFirstRunGate = false
                                            statusMsg = "Checklist refreshed (${readiness.readyRequiredCount}/${readiness.requiredItems.size})."
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
                                onOpenGuide = { dest = OrganizerDest.GUIDE },
                                onContinueToStudio = {
                                    isFirstRunGate = false
                                    dest = OrganizerDest.STUDIO
                                },
                            )

                            OrganizerDest.GUIDE -> GuideTab()

                            OrganizerDest.ABOUT -> AboutTab()
                        }
                    }
                }

                // Bottom breathing space so content scrolls comfortably above floating One UI 9 dock
                Spacer(modifier = Modifier.height(104.dp))
            }

            // Floating One UI 9 Expressive Capsule Bottom Navigation Dock
            OneUi9FloatingBottomDock(
                activeDest = dest,
                onSelect = { chosen -> dest = chosen },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .tutorialTarget(tutorialRegistry, "dock_root"),
            )
        }
    }

    if (showTutorial && !showSplash) {
        TutorialCoachmarkOverlay(
            stepIndex = tutorialStepIndex,
            steps = VERBOSE_TUTORIAL_STEPS,
            registry = tutorialRegistry,
            onPrevious = {
                if (tutorialStepIndex > 0) tutorialStepIndex--
            },
            onNext = {
                if (tutorialStepIndex < VERBOSE_TUTORIAL_STEPS.lastIndex) {
                    tutorialStepIndex++
                } else {
                    val updated = config.copy(
                        preferences = config.preferences.copy(hasCompletedTutorial = true),
                    )
                    store.save(updated)
                    onConfigChange(updated)
                    showTutorial = false
                    dest = if (!readiness.allRequiredReady) OrganizerDest.SETUP else OrganizerDest.STUDIO
                    statusMsg = "Interactive tutorial finished!"
                }
            },
            onSkip = {
                val updated = config.copy(
                    preferences = config.preferences.copy(hasCompletedTutorial = true),
                )
                store.save(updated)
                onConfigChange(updated)
                showTutorial = false
                dest = if (!readiness.allRequiredReady) OrganizerDest.SETUP else OrganizerDest.STUDIO
                statusMsg = "Tutorial skipped — replay anytime via ≡ Interactive Tutorial."
            },
        )
    }

    BrandSplashOverlay(visible = showSplash)
    }

    if (showWallpaperDialog) {
        OneUiWallpaperSheet(
            config = config,
            previewPage = wallpaperPreviewPage,
            currentWallpaperBitmap = wallpaperBitmap,
            onDismiss = { showWallpaperDialog = false },
            onPreviewSelected = { sourceId, bmp ->
                wallpaperBitmap = bmp
                val next = config.copy(
                    preferences = config.preferences.copy(
                        activeWallpaperSource = sourceId,
                        showWallpaperInPreview = true,
                    ),
                )
                store.save(next)
                onConfigChange(next)
                showWallpaperDialog = false
                statusMsg = "Updated preview wallpaper cleanly."
            },
            onAppliedToHomeScreen = { sourceId, bmp ->
                wallpaperBitmap = bmp
                val next = config.copy(
                    preferences = config.preferences.copy(
                        activeWallpaperSource = sourceId,
                        showWallpaperInPreview = true,
                    ),
                )
                store.save(next)
                onConfigChange(next)
                showWallpaperDialog = false
                statusMsg = "Applied unlocked Home Screen wallpaper (lock screen untouched)."
            },
            onError = { errMsg ->
                showWallpaperDialog = false
                statusMsg = errMsg
            },
        )
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
                val reloaded = store.load()
                onConfigChange(reloaded)
                observedSnapshot = store.loadObservedSnapshot(reloaded)
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

@Composable
private fun DrawerNavItem(
    label: String,
    badge: String?,
    badgePositive: Boolean = false,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val p = LocalStudioPalette.current
    NavigationDrawerItem(
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        selected = selected,
        onClick = onClick,
        badge = badge?.let {
            {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (badgePositive) p.positiveSurface else p.cardElevated,
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (badgePositive) p.positiveText else p.textSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = p.accentSoft,
            selectedTextColor = p.accent,
            unselectedContainerColor = Color.Transparent,
            unselectedTextColor = p.textPrimary,
        ),
        shape = RoundedCornerShape(14.dp),
    )
}

/**
 * One UI 9-inspired Floating Expressive Bottom Dock with translucent pill surface,
 * crisp 1dp border, shadow elevation, and expanding emerald active indicator pill.
 */
@Composable
private fun OneUi9FloatingBottomDock(
    activeDest: OrganizerDest,
    onSelect: (OrganizerDest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalStudioPalette.current
    val items = listOf(
        Triple(OrganizerDest.STUDIO, "Studio", "✦"),
        Triple(OrganizerDest.CURRENT, "Current", "▣"),
        Triple(OrganizerDest.CATALOG, "Catalog", "⊞"),
    )

    Surface(
        modifier = modifier
            .shadow(
                elevation = if (p.isDark) 18.dp else 14.dp,
                shape = RoundedCornerShape(32.dp),
                ambientColor = Color.Black.copy(alpha = 0.4f),
                spotColor = Color.Black.copy(alpha = 0.4f),
            ),
        shape = RoundedCornerShape(32.dp),
        color = p.card.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, p.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (itemDest, label, glyph) ->
                val selected = activeDest == itemDest
                val bgColor by animateColorAsState(
                    targetValue = if (selected) p.accentSoft else Color.Transparent,
                    animationSpec = tween(220),
                    label = "dockBg",
                )
                val pillScale by animateFloatAsState(
                    targetValue = if (selected) 1.03f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.68f, stiffness = 420f),
                    label = "dockScale",
                )
                val hPad by animateDpAsState(
                    targetValue = if (selected) 18.dp else 14.dp,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 400f),
                    label = "dockPad",
                )
                Box(
                    modifier = Modifier
                        .scale(pillScale)
                        .clip(RoundedCornerShape(26.dp))
                        .background(bgColor)
                        .clickable { onSelect(itemDest) }
                        .padding(horizontal = hPad, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = glyph,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) p.accent else p.textMuted,
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) p.accent else p.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsAndThemeTab(
    config: HomeStudioConfig,
    onUpdate: (HomeStudioConfig, String) -> Unit,
    onOpenTools: () -> Unit,
    onOpenWallpaperPicker: () -> Unit,
) {
    val context = LocalContext.current
    val p = LocalStudioPalette.current
    val t = config.globalTheme

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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = p.card),
        border = BorderStroke(1.dp, p.border),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Unlocked Home Wallpaper", style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
            Text(
                text = "Choose from gallery or One UI curated styles with live preview (lock screen stays untouched).",
                style = MaterialTheme.typography.bodySmall,
                color = p.textSecondary,
            )
            Button(onClick = onOpenWallpaperPicker, modifier = Modifier.fillMaxWidth()) {
                Text("Open Wallpaper Picker")
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
            OutlinedButton(onClick = onOpenTools, modifier = Modifier.fillMaxWidth()) {
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
