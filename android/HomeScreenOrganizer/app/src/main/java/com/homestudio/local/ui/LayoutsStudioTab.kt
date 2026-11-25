package com.homestudio.local.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homestudio.local.catalog.InstalledAppInfo
import com.homestudio.local.data.HeaderPlacement
import com.homestudio.local.data.HomeStudioConfig
import com.homestudio.local.data.LayoutFolderChild
import com.homestudio.local.data.LayoutSlotItem
import com.homestudio.local.data.StudioPage
import com.homestudio.local.diagnostics.UsageAppInfo
import com.homestudio.local.planner.LayoutSuggestionEngine
import com.homestudio.local.planner.PresetKind
import com.homestudio.local.planner.SelectableApp
import com.homestudio.local.planner.SelectableFolder
import com.homestudio.local.planner.SuggestedPageDraft

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayoutsStudioTab(
    config: HomeStudioConfig,
    installedApps: List<InstalledAppInfo>,
    usageTopApps: List<UsageAppInfo>,
    boundHeaderIds: IntArray,
    wallpaperBitmap: Bitmap?,
    onUpdateConfig: (HomeStudioConfig, String) -> Unit,
    onPinHeaderForPage: (StudioPage) -> Unit,
    onExportHostLayout: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenWallpaperPicker: (StudioPage?) -> Unit,
    tutorialRegistry: TutorialTargetRegistry? = null,
) {
    var showPresets by remember(config.pages.isEmpty()) { mutableStateOf(config.pages.isEmpty()) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var activeSuggestionDraft by remember { mutableStateOf<SuggestedPageDraft?>(null) }
    var editingExistingPageId by remember { mutableStateOf<String?>(null) }
    var showCustomBuilder by remember { mutableStateOf(false) }
    var editingHeaderPage by remember { mutableStateOf<StudioPage?>(null) }

    val p = LocalStudioPalette.current
    val globalMax = config.preferences.defaultMaxItemsPerPage
    val cols = config.preferences.gridColumns
    val rows = config.preferences.gridRows

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Layout Studio (${config.pages.size} pages)",
            style = MaterialTheme.typography.titleMedium,
            color = p.textPrimary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(onClick = { showPresets = !showPresets }) {
                Text(if (showPresets) "Hide Presets" else "Presets")
            }
            OutlinedButton(onClick = onOpenTools) { Text("Widgets & Tools") }
        }
    }

    AnimatedVisibility(
        visible = showPresets,
        enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 360f)) + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = p.cardTint),
            border = BorderStroke(1.dp, p.border),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (config.pages.isEmpty()) "First-Use Collection Presets" else "Collection Presets",
                    style = MaterialTheme.typography.titleSmall,
                    color = p.secondaryAccent,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Detected ${installedApps.size} installed apps (${installedApps.count { !it.isSystemApp }} user apps). Pick a starter suite — every page respects your Max Apps Per Page ($globalMax) and only includes installed apps:",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary,
                )
                PresetKind.entries.forEach { preset ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = p.card),
                        border = BorderStroke(1.dp, p.border),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(preset.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                            Text(preset.subtitle, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                            Button(onClick = {
                                val generated = LayoutSuggestionEngine.generateCollectionPreset(
                                    preset = preset,
                                    installedApps = installedApps,
                                    usageTopApps = usageTopApps,
                                    columns = cols,
                                    rows = rows,
                                    maxItemsPerPage = globalMax,
                                )
                                val next = LayoutSuggestionEngine.syncCategoriesAndWidgetsFromPages(
                                    config.copy(
                                        pages = generated,
                                        preferences = config.preferences.copy(
                                            onboardingCompleted = true,
                                            activePresetId = preset.id,
                                        ),
                                    ),
                                )
                                showPresets = false
                                onUpdateConfig(next, "Applied ${preset.id.replaceFirstChar { it.uppercase() }} preset (${generated.size} layouts).")
                            }) {
                                Text("Use ${preset.id.replaceFirstChar { it.uppercase() }}")
                            }
                        }
                    }
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tutorialTarget(tutorialRegistry, "studio_capacity")
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = p.card),
        border = BorderStroke(1.dp, p.border),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Max Apps / Items Per Layout: $globalMax (applies cleanly to new pages & Realign All)",
                style = MaterialTheme.typography.labelLarge,
                color = p.textPrimary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(4, 6, 8, 10, 12, 16).forEach { cap ->
                    FilterChip(
                        selected = globalMax == cap,
                        onClick = {
                            val updated = config.copy(
                                preferences = config.preferences.copy(defaultMaxItemsPerPage = cap),
                            )
                            onUpdateConfig(updated, "Global max items per layout set to $cap.")
                        },
                        label = { Text(if (cap == 8) "8 (One-Hand)" else "$cap") },
                    )
                }
            }
            FlowRow(
                modifier = Modifier.tutorialTarget(tutorialRegistry, "studio_builders"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(onClick = { showTemplatePicker = true }) { Text("+ Suggested") }
                OutlinedButton(onClick = { showCustomBuilder = true }) { Text("+ Custom") }
                OutlinedButton(onClick = {
                    val realigned = LayoutSuggestionEngine.realignAllPages(
                        pages = config.pages,
                        globalMax = globalMax,
                        columns = cols,
                        rows = rows,
                    )
                    val synced = LayoutSuggestionEngine.syncCategoriesAndWidgetsFromPages(config.copy(pages = realigned))
                    onUpdateConfig(synced, "Realigned all ${realigned.size} pages cleanly.")
                }) { Text("Realign All") }
                OutlinedButton(onClick = onExportHostLayout) { Text("Export Layout JSON") }
            }
        }
    }

    if (config.pages.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = p.card),
            border = BorderStroke(1.dp, p.border),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("No layouts configured yet", style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                Text(
                    text = "Choose a Collection Preset above or tap + Suggested / + Custom to create your first layout.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textMuted,
                )
            }
        }
    }

    val sortedPages = config.pages.sortedBy { it.order }
    if (sortedPages.isNotEmpty()) {
        LayoutViewerControlBar(
            orientation = config.preferences.layoutViewOrientation,
            showWallpaper = config.preferences.showWallpaperInPreview,
            onChangeOrientation = { ori ->
                onUpdateConfig(
                    config.copy(preferences = config.preferences.copy(layoutViewOrientation = ori)),
                    "Switched Studio viewer to ${ori.replaceFirstChar { it.uppercase() }} mode.",
                )
            },
            onToggleWallpaper = { enabled ->
                onUpdateConfig(
                    config.copy(preferences = config.preferences.copy(showWallpaperInPreview = enabled)),
                    if (enabled) "Showing Studio previews with wallpaper." else "Showing clean Studio backdrop without wallpaper.",
                )
            },
            onOpenWallpaperPicker = {
                onOpenWallpaperPicker(sortedPages.firstOrNull { it.isDefaultHome } ?: sortedPages.firstOrNull())
            },
            tutorialRegistry = tutorialRegistry,
        )
    }

    if (config.preferences.layoutViewOrientation == "horizontal" && sortedPages.isNotEmpty()) {
        HorizontalPagesDeck(pages = sortedPages) { idx, pg ->
            StudioPageCard(
                idx = idx,
                pg = pg,
                sortedPages = sortedPages,
                config = config,
                installedApps = installedApps,
                wallpaperBitmap = wallpaperBitmap,
                onUpdateConfig = onUpdateConfig,
                onEditHeader = { editingHeaderPage = pg },
                onEditItems = {
                    editingExistingPageId = pg.id
                    activeSuggestionDraft = pageToDraft(pg, installedApps)
                },
            )
        }
    } else {
        sortedPages.forEachIndexed { idx, pg ->
            StudioPageCard(
                idx = idx,
                pg = pg,
                sortedPages = sortedPages,
                config = config,
                installedApps = installedApps,
                wallpaperBitmap = wallpaperBitmap,
                onUpdateConfig = onUpdateConfig,
                onEditHeader = { editingHeaderPage = pg },
                onEditItems = {
                    editingExistingPageId = pg.id
                    activeSuggestionDraft = pageToDraft(pg, installedApps)
                },
            )
        }
    }

    if (showTemplatePicker) {
        SuggestionTemplatePickerDialog(
            config = config,
            onDismiss = { showTemplatePicker = false },
            onSelectTemplate = { tmplId ->
                showTemplatePicker = false
                if (tmplId == "wallpaper") {
                    val wp = LayoutSuggestionEngine.createWallpaperPage(config.pages.size, cols)
                    val merged = LayoutSuggestionEngine.normalizePageOrders(config.pages + wp)
                    onUpdateConfig(
                        LayoutSuggestionEngine.syncCategoriesAndWidgetsFromPages(config.copy(pages = merged)),
                        "Added clean Wallpaper page.",
                    )
                } else {
                    editingExistingPageId = null
                    activeSuggestionDraft = LayoutSuggestionEngine.buildSuggestionDraft(
                        templateId = tmplId,
                        installedApps = installedApps,
                        usageTopApps = usageTopApps,
                        maxItemsPerPage = globalMax,
                    )
                }
            },
        )
    }

    activeSuggestionDraft?.let { draft ->
        SuggestionReviewDialog(
            draft = draft,
            installedApps = installedApps,
            globalMax = globalMax,
            isEditingExisting = editingExistingPageId != null,
            onDismiss = {
                activeSuggestionDraft = null
                editingExistingPageId = null
            },
            onConfirm = { finalDraft ->
                val targetOrder = editingExistingPageId?.let { exId ->
                    config.pages.firstOrNull { it.id == exId }?.order
                } ?: config.pages.size
                val builtPage = LayoutSuggestionEngine.finalizeDraftToPage(
                    draft = finalDraft,
                    order = targetOrder,
                    columns = cols,
                    rows = rows,
                    globalMax = globalMax,
                )
                val nextPages = if (editingExistingPageId != null) {
                    config.pages.map { existing ->
                        if (existing.id == editingExistingPageId) {
                            builtPage.copy(
                                id = existing.id,
                                order = existing.order,
                                header = existing.header,
                                boundWidgetId = existing.boundWidgetId,
                                items = LayoutSuggestionEngine.realignPageItems(
                                    builtPage.items,
                                    columns = cols,
                                    rows = rows,
                                    maxItems = builtPage.effectiveMaxItems(globalMax),
                                    headerRow = if (existing.header.enabled) existing.header.row else -1,
                                ),
                            )
                        } else {
                            if (builtPage.isDefaultHome) existing.copy(isDefaultHome = false) else existing
                        }
                    }
                } else {
                    val uniqueId = ensureUniquePageId(builtPage.id, config.pages)
                    val basePages = if (builtPage.isDefaultHome) config.pages.map { it.copy(isDefaultHome = false) } else config.pages
                    LayoutSuggestionEngine.normalizePageOrders(basePages + builtPage.copy(id = uniqueId))
                }
                val synced = LayoutSuggestionEngine.syncCategoriesAndWidgetsFromPages(config.copy(pages = nextPages))
                val actionLabel = if (editingExistingPageId != null) "Updated" else "Added"
                activeSuggestionDraft = null
                editingExistingPageId = null
                onUpdateConfig(synced, "$actionLabel '${builtPage.title}' (${builtPage.items.size} slots).")
            },
        )
    }

    if (showCustomBuilder) {
        CustomLayoutBuilderDialog(
            installedApps = installedApps,
            globalMax = globalMax,
            onDismiss = { showCustomBuilder = false },
            onConfirm = { customPage ->
                val uniqueId = ensureUniquePageId(customPage.id, config.pages)
                val packed = LayoutSuggestionEngine.realignPageItems(
                    items = customPage.items,
                    columns = cols,
                    rows = rows,
                    maxItems = customPage.effectiveMaxItems(globalMax),
                    headerRow = if (customPage.header.enabled) customPage.header.row else -1,
                )
                val nextPages = LayoutSuggestionEngine.normalizePageOrders(
                    config.pages + customPage.copy(id = uniqueId, order = config.pages.size, items = packed),
                )
                val synced = LayoutSuggestionEngine.syncCategoriesAndWidgetsFromPages(config.copy(pages = nextPages))
                showCustomBuilder = false
                onUpdateConfig(synced, "Added custom layout '${customPage.title}'.")
            },
        )
    }

    editingHeaderPage?.let { target ->
        EditHeaderDialog(
            page = target,
            boundHeaderIds = boundHeaderIds,
            onDismiss = { editingHeaderPage = null },
            onPinNewWidget = {
                onPinHeaderForPage(target)
                editingHeaderPage = null
            },
            onSave = { updatedPage ->
                val nextPages = config.pages.map { if (it.id == updatedPage.id) updatedPage else it }
                val synced = LayoutSuggestionEngine.syncCategoriesAndWidgetsFromPages(config.copy(pages = nextPages))
                editingHeaderPage = null
                onUpdateConfig(synced, "Updated header for '${updatedPage.title}'.")
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudioPageCard(
    idx: Int,
    pg: StudioPage,
    sortedPages: List<StudioPage>,
    config: HomeStudioConfig,
    installedApps: List<InstalledAppInfo>,
    wallpaperBitmap: Bitmap?,
    onUpdateConfig: (HomeStudioConfig, String) -> Unit,
    onEditHeader: () -> Unit,
    onEditItems: () -> Unit,
) {
    val p = LocalStudioPalette.current
    val globalMax = config.preferences.defaultMaxItemsPerPage
    val cols = config.preferences.gridColumns
    val rows = config.preferences.gridRows
    val cap = pg.effectiveMaxItems(globalMax)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 360f)),
        colors = CardDefaults.cardColors(containerColor = p.card),
        border = BorderStroke(1.dp, if (pg.isDefaultHome) p.accent.copy(alpha = 0.55f) else p.border),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${idx + 1}. ${pg.title}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = p.textPrimary,
                        )
                        if (pg.isDefaultHome) {
                            Box(
                                modifier = Modifier
                                    .background(p.positiveSurface, RoundedCornerShape(999.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            ) {
                                Text("Home", style = MaterialTheme.typography.labelSmall, color = p.positiveText, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Text(
                        text = if (pg.isWallpaperPage) {
                            "Clean Wallpaper Page"
                        } else {
                            "${pg.items.size}/$cap items | Header: " +
                                (if (pg.header.enabled) "Row ${pg.header.row} (${pg.header.alignmentOverride ?: config.globalTheme.alignment})" else "Hidden") +
                                (pg.boundWidgetId?.let { " | Widget #$it" } ?: "")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textMuted,
                    )
                }
            }

            MiniPhoneGridPreview(
                page = pg,
                config = config,
                wallpaperBitmap = wallpaperBitmap,
                showWallpaper = config.preferences.showWallpaperInPreview,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (idx > 0) {
                    OutlinedButton(onClick = {
                        val swapped = swapPages(sortedPages, idx, idx - 1)
                        onUpdateConfig(
                            LayoutSuggestionEngine.syncCategoriesAndWidgetsFromPages(config.copy(pages = swapped)),
                            "Moved '${pg.title}' up.",
                        )
                    }) { Text("↑") }
                }
                if (idx < sortedPages.lastIndex) {
                    OutlinedButton(onClick = {
                        val swapped = swapPages(sortedPages, idx, idx + 1)
                        onUpdateConfig(
                            LayoutSuggestionEngine.syncCategoriesAndWidgetsFromPages(config.copy(pages = swapped)),
                            "Moved '${pg.title}' down.",
                        )
                    }) { Text("↓") }
                }
                if (!pg.isDefaultHome && !pg.isWallpaperPage) {
                    OutlinedButton(onClick = {
                        val updated = sortedPages.map { it.copy(isDefaultHome = it.id == pg.id) }
                        onUpdateConfig(config.copy(pages = updated), "Set '${pg.title}' as Default Home.")
                    }) { Text("Set Home") }
                }
                if (!pg.isWallpaperPage) {
                    OutlinedButton(onClick = onEditHeader) { Text("Edit Header") }
                    OutlinedButton(onClick = onEditItems) { Text("Edit Items") }
                    OutlinedButton(onClick = {
                        val packed = LayoutSuggestionEngine.realignPageItems(
                            items = pg.items,
                            columns = cols,
                            rows = rows,
                            maxItems = cap,
                            headerRow = if (pg.header.enabled) pg.header.row else -1,
                        )
                        val updated = sortedPages.map { if (it.id == pg.id) it.copy(items = packed) else it }
                        onUpdateConfig(config.copy(pages = updated), "Realigned '${pg.title}'.")
                    }) { Text("Realign") }
                }
                TextButton(onClick = {
                    val remaining = LayoutSuggestionEngine.normalizePageOrders(sortedPages.filterNot { it.id == pg.id })
                    val synced = LayoutSuggestionEngine.syncCategoriesAndWidgetsFromPages(config.copy(pages = remaining))
                    onUpdateConfig(synced, "Removed '${pg.title}'.")
                }) { Text("Remove", color = p.danger) }
            }
        }
    }
}

@Composable
private fun SuggestionTemplatePickerDialog(
    config: HomeStudioConfig,
    onDismiss: () -> Unit,
    onSelectTemplate: (String) -> Unit,
) {
    val p = LocalStudioPalette.current
    val customCats = config.categories.filter { c ->
        LayoutSuggestionEngine.TEMPLATES.none { it.id == c.id }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Suggested Layout") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LayoutSuggestionEngine.TEMPLATES.forEach { tmpl ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelectTemplate(tmpl.id) },
                        colors = CardDefaults.cardColors(containerColor = p.cardElevated),
                        border = BorderStroke(1.dp, p.border),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(tmpl.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                            Text(tmpl.description, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                        }
                    }
                }
                customCats.forEach { cc ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelectTemplate(cc.id) },
                        colors = CardDefaults.cardColors(containerColor = p.positiveSurface),
                        border = BorderStroke(1.dp, p.border),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("${cc.title} (Custom)", style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                            Text("Build suggestion from your custom-tagged apps", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                        }
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelectTemplate("wallpaper") },
                    colors = CardDefaults.cardColors(containerColor = p.cardElevated),
                    border = BorderStroke(1.dp, p.border),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Wallpaper (Clean)", style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                        Text("Unobstructed wallpaper viewing page", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionReviewDialog(
    draft: SuggestedPageDraft,
    installedApps: List<InstalledAppInfo>,
    globalMax: Int,
    isEditingExisting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (SuggestedPageDraft) -> Unit,
) {
    val p = LocalStudioPalette.current
    var working by remember(draft) { mutableStateOf(draft) }
    var extraQuery by remember { mutableStateOf("") }
    var showAddApps by remember { mutableStateOf(false) }
    val effMax = working.maxItemsPerPage ?: globalMax
    val usedSlots = working.selectedTopSlotCount()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEditingExisting) "Edit '${working.title}'" else "Suggested: ${working.title}")
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Only installed apps on your device are shown. Check/uncheck apps, rename or break down folders, or adjust per-page max slots:",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary,
                )
                OutlinedTextField(
                    value = working.title,
                    onValueChange = { working = working.copy(title = it) },
                    label = { Text("Page Header Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Text(
                    text = "Slots selected: $usedSlots / $effMax" + (if (usedSlots > effMax) " (only first $effMax will pack unless limit raised)" else ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (usedSlots > effMax) p.warningText else p.positiveText,
                )

                Text("Per-Page Max Items:", style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = working.maxItemsPerPage == null,
                        onClick = { working = working.copy(maxItemsPerPage = null) },
                        label = { Text("Global ($globalMax)") },
                    )
                    listOf(4, 6, 8, 12, 16).forEach { cap ->
                        FilterChip(
                            selected = working.maxItemsPerPage == cap,
                            onClick = { working = working.copy(maxItemsPerPage = cap) },
                            label = { Text("$cap") },
                        )
                    }
                }

                if (working.soloApps.isNotEmpty()) {
                    Text("Standalone Apps (${working.soloApps.count { it.checked }})", style = MaterialTheme.typography.titleSmall)
                    working.soloApps.forEachIndexed { idx, app ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = app.checked,
                                onCheckedChange = { c ->
                                    val updated = working.soloApps.toMutableList()
                                    updated[idx] = app.copy(checked = c)
                                    working = working.copy(soloApps = updated)
                                },
                            )
                            InstalledAppIcon(app.packageName, app.displayName, size = 24.dp)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(app.displayName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (working.folders.isNotEmpty()) {
                    Text("Suite Folders (${working.folders.count { it.checked }})", style = MaterialTheme.typography.titleSmall)
                    working.folders.forEachIndexed { fIdx, folder ->
                        Card(
                            modifier = Modifier.fillMaxWidth().animateContentSize(),
                            colors = CardDefaults.cardColors(containerColor = p.cardElevated),
                            border = BorderStroke(1.dp, p.border),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = folder.checked,
                                        onCheckedChange = { c ->
                                            val updated = working.folders.toMutableList()
                                            updated[fIdx] = folder.copy(checked = c)
                                            working = working.copy(folders = updated)
                                        },
                                    )
                                    OutlinedTextField(
                                        value = folder.title,
                                        onValueChange = { newName ->
                                            val updated = working.folders.toMutableList()
                                            updated[fIdx] = folder.copy(title = newName)
                                            working = working.copy(folders = updated)
                                        },
                                        label = { Text("Folder Name") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                    )
                                }
                                TextButton(onClick = {
                                    working = LayoutSuggestionEngine.breakDownFolder(working, folder.id)
                                }) { Text("Break Down Folder into Solo Apps") }

                                folder.children.forEachIndexed { cIdx, child ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = child.checked && folder.checked,
                                            onCheckedChange = { c ->
                                                val updatedChildren = folder.children.toMutableList()
                                                updatedChildren[cIdx] = child.copy(checked = c)
                                                val updatedFolders = working.folders.toMutableList()
                                                updatedFolders[fIdx] = folder.copy(children = updatedChildren)
                                                working = working.copy(folders = updatedFolders)
                                            },
                                        )
                                        InstalledAppIcon(child.packageName, child.displayName, size = 20.dp)
                                        Spacer(modifier = Modifier.size(6.dp))
                                        Text(child.displayName, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showAddApps = !showAddApps },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (showAddApps) "Hide Extra Installed Apps" else "+ Add Any Installed App") }

                if (showAddApps) {
                    OutlinedTextField(
                        value = extraQuery,
                        onValueChange = { extraQuery = it },
                        label = { Text("Filter installed apps") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    val q = extraQuery.trim().lowercase()
                    val alreadyPkgs = working.soloApps.map { it.packageName }.toSet()
                    installedApps
                        .filter { it.packageName !in alreadyPkgs && (q.isEmpty() || it.displayName.lowercase().contains(q)) }
                        .take(15)
                        .forEach { app ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    InstalledAppIcon(app.packageName, app.displayName, size = 24.dp)
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text(app.displayName, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = {
                                    working = working.copy(
                                        soloApps = working.soloApps + SelectableApp(app.packageName, app.activityName, app.displayName, true),
                                    )
                                }) { Text("Add") }
                            }
                        }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(working) }) {
                Text(if (isEditingExisting) "Save Changes" else "Add Layout")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomLayoutBuilderDialog(
    installedApps: List<InstalledAppInfo>,
    globalMax: Int,
    onDismiss: () -> Unit,
    onConfirm: (StudioPage) -> Unit,
) {
    val p = LocalStudioPalette.current
    var title by remember { mutableStateOf("") }
    var headerEnabled by remember { mutableStateOf(true) }
    var headerRow by remember { mutableStateOf(0) }
    var headerAlign by remember { mutableStateOf("start") }
    var maxItemsOverride by remember { mutableStateOf<Int?>(null) }
    var search by remember { mutableStateOf("") }
    var selectedSolo by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var folders by remember { mutableStateOf<List<SelectableFolder>>(emptyList()) }
    var newFolderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Custom Layout") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Layout Name (e.g. Weekend Suite)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Show Header Widget")
                    Switch(checked = headerEnabled, onCheckedChange = { headerEnabled = it })
                }
                if (headerEnabled) {
                    Text("Header Row:", style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 1, 2).forEach { r ->
                            FilterChip(
                                selected = headerRow == r,
                                onClick = { headerRow = r },
                                label = { Text("Row $r") },
                            )
                        }
                    }
                    Text("Header Alignment:", style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("start", "center", "end").forEach { a ->
                            FilterChip(
                                selected = headerAlign == a,
                                onClick = { headerAlign = a },
                                label = { Text(a) },
                            )
                        }
                    }
                }
                Text("Max Apps on This Page:", style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = maxItemsOverride == null,
                        onClick = { maxItemsOverride = null },
                        label = { Text("Global ($globalMax)") },
                    )
                    listOf(4, 6, 8, 12).forEach { cap ->
                        FilterChip(
                            selected = maxItemsOverride == cap,
                            onClick = { maxItemsOverride = cap },
                            label = { Text("$cap") },
                        )
                    }
                }

                if (selectedSolo.size >= 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            label = { Text("Folder name for ${selectedSolo.size} apps") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedButton(onClick = {
                            val fname = newFolderName.trim().ifBlank { "Custom Suite" }
                            folders = folders + SelectableFolder(
                                id = "custom:${fname.lowercase().replace(Regex("[^a-z0-9]+"), "-")}:${folders.size}",
                                title = fname,
                                checked = true,
                                children = selectedSolo.map { SelectableApp(it.packageName, it.activityName, it.displayName, true) },
                            )
                            selectedSolo = emptyList()
                            newFolderName = ""
                        }) { Text("Group") }
                    }
                }

                if (folders.isNotEmpty()) {
                    Text("Custom Folders: ${folders.joinToString { "${it.title} (${it.children.size})" }}", style = MaterialTheme.typography.bodySmall, color = p.secondaryAccent)
                }

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Pick installed apps (${selectedSolo.size} solo)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                val q = search.trim().lowercase()
                installedApps
                    .filter { q.isEmpty() || it.displayName.lowercase().contains(q) }
                    .take(25)
                    .forEach { app ->
                        val isChecked = selectedSolo.any { it.packageName == app.packageName }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { c ->
                                    selectedSolo = if (c) selectedSolo + app else selectedSolo.filterNot { it.packageName == app.packageName }
                                },
                            )
                            InstalledAppIcon(app.packageName, app.displayName, size = 24.dp)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(app.displayName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
            }
        },
        confirmButton = {
            Button(onClick = {
                val cleanTitle = title.trim().ifBlank { "Custom Page" }
                val slug = cleanTitle.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "custom" }
                val items = mutableListOf<LayoutSlotItem>()
                selectedSolo.forEachIndexed { idx, app ->
                    items.add(
                        LayoutSlotItem(
                            id = "$slug:app:$idx",
                            type = "app",
                            label = app.displayName,
                            packageName = app.packageName,
                            activityName = app.activityName,
                        ),
                    )
                }
                folders.forEachIndexed { idx, fld ->
                    items.add(
                        LayoutSlotItem(
                            id = "$slug:fld:$idx",
                            type = "folder",
                            label = fld.title,
                            children = fld.children.map { LayoutFolderChild(it.packageName, it.activityName, it.displayName) },
                        ),
                    )
                }
                onConfirm(
                    StudioPage(
                        id = slug,
                        title = cleanTitle,
                        maxItemsPerPage = maxItemsOverride,
                        header = HeaderPlacement(
                            enabled = headerEnabled,
                            row = headerRow,
                            column = 0,
                            alignmentOverride = headerAlign,
                        ),
                        items = items,
                    ),
                )
            }) { Text("Create Page") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditHeaderDialog(
    page: StudioPage,
    boundHeaderIds: IntArray,
    onDismiss: () -> Unit,
    onPinNewWidget: () -> Unit,
    onSave: (StudioPage) -> Unit,
) {
    var title by remember(page.id) { mutableStateOf(page.title) }
    var subtitle by remember(page.id) { mutableStateOf(page.subtitle ?: "") }
    var enabled by remember(page.id) { mutableStateOf(page.header.enabled) }
    var row by remember(page.id) { mutableStateOf(page.header.row) }
    var alignOverride by remember(page.id) { mutableStateOf(page.header.alignmentOverride) }
    var vAlign by remember(page.id) { mutableStateOf(page.header.verticalAlignment) }
    var boundWid by remember(page.id) { mutableStateOf(page.boundWidgetId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Header — ${page.title}") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Header Enabled")
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Header Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Optional Subtitle") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text("Reposition Row:", style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Row 0 (Top)", 1 to "Row 1", 2 to "Row 2").forEach { (r, label) ->
                        FilterChip(selected = row == r, onClick = { row = r }, label = { Text(label) })
                    }
                }
                Text("Horizontal Alignment:", style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = alignOverride == null, onClick = { alignOverride = null }, label = { Text("Global") })
                    listOf("start", "center", "end").forEach { a ->
                        FilterChip(selected = alignOverride == a, onClick = { alignOverride = a }, label = { Text(a) })
                    }
                }
                Text("Vertical Position inside Widget:", style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("top", "center", "bottom").forEach { v ->
                        FilterChip(selected = vAlign == v, onClick = { vAlign = v }, label = { Text(v) })
                    }
                }
                if (boundHeaderIds.isNotEmpty()) {
                    Text("Bind to Live Home Widget ID:", style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = boundWid == null, onClick = { boundWid = null }, label = { Text("None") })
                        boundHeaderIds.forEach { wid ->
                            FilterChip(selected = boundWid == wid, onClick = { boundWid = wid }, label = { Text("#$wid") })
                        }
                    }
                }
                OutlinedButton(onClick = onPinNewWidget, modifier = Modifier.fillMaxWidth()) {
                    Text("Pin New Category Header Widget")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    page.copy(
                        title = title.trim().ifBlank { page.title },
                        subtitle = subtitle.trim().ifBlank { null },
                        boundWidgetId = boundWid,
                        header = page.header.copy(
                            enabled = enabled,
                            row = row,
                            alignmentOverride = alignOverride,
                            verticalAlignment = vAlign,
                        ),
                    ),
                )
            }) { Text("Apply Header") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun pageToDraft(page: StudioPage, installedApps: List<InstalledAppInfo>): SuggestedPageDraft {
    val solos = page.items.filter { it.type == "app" && !it.packageName.isNullOrBlank() }.map { slot ->
        SelectableApp(
            packageName = slot.packageName!!,
            activityName = slot.activityName ?: "",
            displayName = slot.label,
            checked = true,
        )
    }
    val folders = page.items.filter { it.type == "folder" }.map { slot ->
        SelectableFolder(
            id = slot.id,
            title = slot.label,
            checked = true,
            children = slot.children.map { ch ->
                SelectableApp(ch.packageName, ch.activityName, ch.label, checked = true)
            },
        )
    }
    return SuggestedPageDraft(
        templateId = page.id,
        title = page.title,
        subtitle = page.subtitle,
        isDefaultHome = page.isDefaultHome,
        maxItemsPerPage = page.maxItemsPerPage,
        soloApps = solos,
        folders = folders,
    )
}

private fun swapPages(pages: List<StudioPage>, i: Int, j: Int): List<StudioPage> {
    val mut = pages.toMutableList()
    val tmp = mut[i]
    mut[i] = mut[j]
    mut[j] = tmp
    return LayoutSuggestionEngine.normalizePageOrders(mut)
}

private fun ensureUniquePageId(baseId: String, existing: List<StudioPage>): String {
    val used = existing.map { it.id }.toSet()
    if (baseId !in used) return baseId
    var n = 2
    while ("$baseId-$n" in used) n++
    return "$baseId-$n"
}
