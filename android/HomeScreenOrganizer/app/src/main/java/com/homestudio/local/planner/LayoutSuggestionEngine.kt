package com.homestudio.local.planner

import com.homestudio.local.catalog.AppCatalog
import com.homestudio.local.catalog.InstalledAppInfo
import com.homestudio.local.data.CategoryDefinition
import com.homestudio.local.data.HeaderPlacement
import com.homestudio.local.data.HomeStudioConfig
import com.homestudio.local.data.LayoutFolderChild
import com.homestudio.local.data.LayoutSlotItem
import com.homestudio.local.data.StudioPage
import com.homestudio.local.diagnostics.UsageAppInfo
import kotlin.math.min

enum class PresetKind(
    val id: String,
    val title: String,
    val subtitle: String,
) {
    BRIEF(
        id = "brief",
        title = "Brief Collection (3 Layouts)",
        subtitle = "Most Used, Core Suite & Clean Wallpaper — minimal clutter",
    ),
    BALANCED(
        id = "balanced",
        title = "Balanced Collection (6 Layouts)",
        subtitle = "Most Used, Essentials, Money, AI & Work, Lifestyle + Wallpaper",
    ),
    DETAILED(
        id = "detailed",
        title = "Detailed Collection (Full 8-12 Layouts)",
        subtitle = "All recognized installed apps intelligently grouped across dedicated pages & suite folders",
    ),
}

data class SelectableApp(
    val packageName: String,
    val activityName: String,
    val displayName: String,
    val checked: Boolean = true,
)

data class SelectableFolder(
    val id: String,
    val title: String,
    val checked: Boolean = true,
    val children: List<SelectableApp>,
)

data class SuggestedPageDraft(
    val templateId: String,
    val title: String,
    val subtitle: String?,
    val isDefaultHome: Boolean = false,
    val maxItemsPerPage: Int? = null,
    val soloApps: List<SelectableApp>,
    val folders: List<SelectableFolder>,
) {
    fun selectedTopSlotCount(): Int {
        val soloCount = soloApps.count { it.checked }
        val folderCount = folders.count { f -> f.checked && f.children.any { it.checked } }
        return soloCount + folderCount
    }
}

data class SuggestionTemplateInfo(
    val id: String,
    val title: String,
    val description: String,
    val categoryIds: List<String>,
)

object LayoutSuggestionEngine {

    val TEMPLATES: List<SuggestionTemplateInfo> = listOf(
        SuggestionTemplateInfo("most-used", "Most Used", "Top foreground apps detected on this device", listOf("most-used")),
        SuggestionTemplateInfo("daily-essentials", "Daily Essentials", "Messaging, browser, mail, calendar, notes & Google Suite", listOf("daily-essentials")),
        SuggestionTemplateInfo("payments", "Payments Suite", "UPI, wallets, bill pay, cards & More Payments suite", listOf("payments", "upi")),
        SuggestionTemplateInfo("banking", "Banking Suites", "Bank suites & insurance folders", listOf("banking")),
        SuggestionTemplateInfo("upi", "UPI", "Dedicated instant UPI & bank UPI suite", listOf("upi")),
        SuggestionTemplateInfo("finance", "Finance & Wealth", "Brokers, funds, crypto & credit tracking", listOf("finance")),
        SuggestionTemplateInfo("ai-suite", "AI Suite", "AI assistants, copilots & AI tools", listOf("ai-suite")),
        SuggestionTemplateInfo("productivity", "Productivity", "Office suites, notes, authenticators & dev tools", listOf("productivity")),
        SuggestionTemplateInfo("food-dining", "Food & Dining", "Restaurants, delivery & quick groceries", listOf("food-dining")),
        SuggestionTemplateInfo("travel", "Travel & Commute", "Rides, maps, metro, flights & stays", listOf("travel")),
        SuggestionTemplateInfo("health", "Health & Fitness", "Wearables, Garmin suite, workouts & care", listOf("health")),
        SuggestionTemplateInfo("smart-home", "Smart Home", "IoT hubs, lights, cameras & utilities", listOf("smart-home")),
        SuggestionTemplateInfo("shopping", "Shopping", "Marketplaces, fashion & deals", listOf("shopping")),
        SuggestionTemplateInfo("entertainment", "Entertainment", "Video OTT, music & gaming", listOf("entertainment")),
        SuggestionTemplateInfo("social-comms", "Social & Comms", "Social networks & communities", listOf("social-comms")),
        SuggestionTemplateInfo("government-docs", "Government & Docs", "Official IDs, locker & travel documents", listOf("government-docs")),
    )

    /** Build an interactive suggestion draft using ONLY installed apps on the device. */
    fun buildSuggestionDraft(
        templateId: String,
        installedApps: List<InstalledAppInfo>,
        usageTopApps: List<UsageAppInfo>,
        maxItemsPerPage: Int,
        customTitle: String? = null,
    ): SuggestedPageDraft {
        val tmpl = TEMPLATES.firstOrNull { it.id == templateId }
            ?: SuggestionTemplateInfo(templateId, customTitle ?: templateId.replace('-', ' ').replaceFirstChar { it.uppercase() }, "", listOf(templateId))

        if (templateId == "most-used") {
            val byPkg = installedApps.associateBy { it.packageName }
            val topInstalled = usageTopApps.mapNotNull { u -> byPkg[u.packageName] }
            val priorityCategories = listOf("daily-essentials", "upi", "payments", "ai-suite", "entertainment", "travel", "food-dining")
            val everydayFallback = installedApps
                .filter { !it.isSystemApp || it.categories.any { c -> c in priorityCategories } }
                .sortedWith(
                    compareByDescending<InstalledAppInfo> { it.isCatalogMatched }
                        .thenBy { app ->
                            val idx = app.categories.mapNotNull { c -> priorityCategories.indexOf(c).takeIf { it >= 0 } }.minOrNull()
                            idx ?: Int.MAX_VALUE
                        }
                        .thenBy { it.displayName.lowercase() },
                )
            val fallback = if (topInstalled.isEmpty()) everydayFallback else topInstalled
            val capped = fallback.distinctBy { it.packageName }.take(min(maxItemsPerPage, installedApps.size))
            return SuggestedPageDraft(
                templateId = "most-used",
                title = customTitle ?: "Most Used",
                subtitle = if (topInstalled.isEmpty()) {
                    "Smart everyday picks (enable Usage Access in Setup for live ranking)"
                } else {
                    "Top apps ranked by device foreground usage"
                },
                isDefaultHome = false,
                soloApps = capped.map {
                    SelectableApp(it.packageName, it.activityName, it.displayName, checked = true)
                },
                folders = emptyList(),
            )
        }

        val candidates = installedApps.filter { app ->
            app.categories.any { it in tmpl.categoryIds }
        }.distinctBy { it.packageName }

        // Group apps with an explicit suiteFolder into folders; un-suited apps start as solo candidates
        val folderGroups = linkedMapOf<String, MutableList<InstalledAppInfo>>()
        val soloCandidates = mutableListOf<InstalledAppInfo>()

        for (app in candidates) {
            val sf = app.suiteFolder
            if (!sf.isNullOrBlank()) {
                folderGroups.getOrPut(sf) { mutableListOf() }.add(app)
            } else {
                soloCandidates.add(app)
            }
        }

        // Convert 1-item suite folders into solo items unless user explicitly needs grouping
        val realFolders = mutableListOf<SelectableFolder>()
        val extraSolos = mutableListOf<InstalledAppInfo>()
        for ((folderName, members) in folderGroups) {
            if (members.size >= 2) {
                realFolders.add(
                    SelectableFolder(
                        id = "${tmpl.id}:${folderName.lowercase().replace(Regex("[^a-z0-9]+"), "-")}",
                        title = folderName,
                        checked = true,
                        children = members.map {
                            SelectableApp(it.packageName, it.activityName, it.displayName, checked = true)
                        },
                    ),
                )
            } else {
                extraSolos.addAll(members)
            }
        }

        val allSolos = (soloCandidates + extraSolos).distinctBy { it.packageName }
        // Intelligently fold excess solos into a "More <Title>" folder so page respects maxItemsPerPage!
        val availableSoloSlots = (maxItemsPerPage - realFolders.size).coerceAtLeast(1)
        val keepSolos: List<SelectableApp>
        if (allSolos.size > availableSoloSlots && allSolos.size - availableSoloSlots >= 2) {
            val splitAt = (availableSoloSlots - 1).coerceAtLeast(0)
            keepSolos = allSolos.take(splitAt).map {
                SelectableApp(it.packageName, it.activityName, it.displayName, checked = true)
            }
            val overflow = allSolos.drop(splitAt)
            if (overflow.isNotEmpty()) {
                realFolders.add(
                    SelectableFolder(
                        id = "${tmpl.id}:more",
                        title = "More ${tmpl.title.split(" ").first()}",
                        checked = true,
                        children = overflow.map {
                            SelectableApp(it.packageName, it.activityName, it.displayName, checked = true)
                        },
                    ),
                )
            }
        } else {
            keepSolos = allSolos.take(availableSoloSlots).map {
                SelectableApp(it.packageName, it.activityName, it.displayName, checked = true)
            }
        }

        // Trim folder count cleanly if maxItemsPerPage is small (e.g. 4)
        val cappedFolders = if (keepSolos.size + realFolders.size > maxItemsPerPage) {
            realFolders.take((maxItemsPerPage - keepSolos.size).coerceAtLeast(1))
        } else {
            realFolders
        }

        return SuggestedPageDraft(
            templateId = tmpl.id,
            title = customTitle ?: tmpl.title,
            subtitle = "${candidates.size} installed apps detected",
            isDefaultHome = tmpl.id == "daily-essentials",
            soloApps = keepSolos,
            folders = cappedFolders,
        )
    }

    fun breakDownFolder(draft: SuggestedPageDraft, folderId: String): SuggestedPageDraft {
        val target = draft.folders.firstOrNull { it.id == folderId } ?: return draft
        val remainingFolders = draft.folders.filterNot { it.id == folderId }
        val promoted = target.children.filter { it.checked }.filter { ch ->
            draft.soloApps.none { it.packageName == ch.packageName }
        }
        return draft.copy(
            soloApps = draft.soloApps + promoted,
            folders = remainingFolders,
        )
    }

    fun finalizeDraftToPage(
        draft: SuggestedPageDraft,
        order: Int,
        columns: Int,
        rows: Int,
        globalMax: Int,
    ): StudioPage {
        val rawItems = mutableListOf<LayoutSlotItem>()
        draft.soloApps.filter { it.checked }.forEachIndexed { idx, app ->
            rawItems.add(
                LayoutSlotItem(
                    id = "${draft.templateId}:app:$idx:${app.packageName}",
                    type = "app",
                    label = app.displayName,
                    packageName = app.packageName,
                    activityName = app.activityName,
                ),
            )
        }
        draft.folders.filter { it.checked }.forEachIndexed { idx, folder ->
            val checkedChildren = folder.children.filter { it.checked }
            if (checkedChildren.isNotEmpty()) {
                rawItems.add(
                    LayoutSlotItem(
                        id = folder.id.ifBlank { "${draft.templateId}:fld:$idx" },
                        type = "folder",
                        label = folder.title.ifBlank { "Suite" },
                        children = checkedChildren.map {
                            LayoutFolderChild(
                                packageName = it.packageName,
                                activityName = it.activityName,
                                label = it.displayName,
                            )
                        },
                    ),
                )
            }
        }
        val maxItems = (draft.maxItemsPerPage ?: globalMax).coerceIn(1, columns * (rows - 1))
        val packed = realignPageItems(rawItems.take(maxItems), columns = columns, rows = rows, maxItems = maxItems)
        return StudioPage(
            id = draft.templateId,
            title = draft.title.ifBlank { "Category" },
            order = order,
            isDefaultHome = draft.isDefaultHome,
            maxItemsPerPage = draft.maxItemsPerPage,
            header = HeaderPlacement(enabled = true, row = 0, column = 0, columnSpan = columns, rowSpan = 1),
            items = packed,
        )
    }

    /** Generate Brief (2-3 layouts), Balanced (5-6 layouts), or Detailed (8-12 all-app layouts). */
    fun generateCollectionPreset(
        preset: PresetKind,
        installedApps: List<InstalledAppInfo>,
        usageTopApps: List<UsageAppInfo>,
        columns: Int = 4,
        rows: Int = 6,
        maxItemsPerPage: Int = 8,
    ): List<StudioPage> {
        val pages = mutableListOf<StudioPage>()
        when (preset) {
            PresetKind.BRIEF -> {
                val daily = finalizeDraftToPage(
                    buildSuggestionDraft("daily-essentials", installedApps, usageTopApps, maxItemsPerPage)
                        .copy(isDefaultHome = true),
                    order = 0, columns = columns, rows = rows, globalMax = maxItemsPerPage,
                )
                pages.add(daily)

                // Combine top money & work apps into a crisp Essentials Hub page
                val hubApps = installedApps.filter { app ->
                    app.categories.any { it in setOf("payments", "upi", "banking", "productivity", "ai-suite") }
                }.distinctBy { it.packageName }
                if (hubApps.isNotEmpty()) {
                    val draft = packAppsIntoMixedDraft(
                        id = "money-and-work",
                        title = "Money & Work",
                        apps = hubApps,
                        maxItemsPerPage = maxItemsPerPage,
                    )
                    pages.add(finalizeDraftToPage(draft, order = 1, columns = columns, rows = rows, globalMax = maxItemsPerPage))
                }
                pages.add(createWallpaperPage(order = pages.size, columns = columns))
            }

            PresetKind.BALANCED -> {
                val buckets: List<Triple<String, String, Set<String>>> = listOf(
                    Triple("most-used", "Most Used", setOf("most-used")),
                    Triple("daily-essentials", "Daily Essentials", setOf("daily-essentials")),
                    Triple("payments", "Payments & Banking", setOf("payments", "upi", "banking", "finance")),
                    Triple("productivity", "AI & Work", setOf("ai-suite", "productivity")),
                    Triple("entertainment", "Media & Lifestyle", setOf("entertainment", "food-dining", "travel", "shopping", "health")),
                )
                for ((id, title, cats) in buckets) {
                    if (id == "most-used") {
                        val mu = buildSuggestionDraft("most-used", installedApps, usageTopApps, maxItemsPerPage, customTitle = title)
                        if (mu.soloApps.isNotEmpty()) {
                            pages.add(finalizeDraftToPage(mu, order = pages.size, columns = columns, rows = rows, globalMax = maxItemsPerPage))
                        }
                    } else if (id == "daily-essentials") {
                        val d = buildSuggestionDraft("daily-essentials", installedApps, usageTopApps, maxItemsPerPage).copy(isDefaultHome = true)
                        pages.add(finalizeDraftToPage(d, order = pages.size, columns = columns, rows = rows, globalMax = maxItemsPerPage))
                    } else {
                        val subset = installedApps.filter { app -> app.categories.any { it in cats } }.distinctBy { it.packageName }
                        if (subset.isNotEmpty()) {
                            val d = packAppsIntoMixedDraft(id = id, title = title, apps = subset, maxItemsPerPage = maxItemsPerPage)
                            pages.add(finalizeDraftToPage(d, order = pages.size, columns = columns, rows = rows, globalMax = maxItemsPerPage))
                        }
                    }
                }
                if (pages.none { it.isDefaultHome } && pages.isNotEmpty()) {
                    pages[0] = pages[0].copy(isDefaultHome = true)
                }
                pages.add(createWallpaperPage(order = pages.size, columns = columns))
            }

            PresetKind.DETAILED -> {
                val orderedTemplates = listOf(
                    "most-used",
                    "daily-essentials",
                    "payments",
                    "banking",
                    "finance",
                    "food-dining",
                    "travel",
                    "ai-suite",
                    "productivity",
                    "health",
                    "smart-home",
                    "shopping",
                    "entertainment",
                    "government-docs",
                )
                val placedPackages = mutableSetOf<String>()
                for (tmplId in orderedTemplates) {
                    val draft = buildSuggestionDraft(tmplId, installedApps, usageTopApps, maxItemsPerPage)
                    if (draft.selectedTopSlotCount() == 0) continue
                    val pg = finalizeDraftToPage(
                        draft.copy(isDefaultHome = tmplId == "daily-essentials"),
                        order = pages.size,
                        columns = columns,
                        rows = rows,
                        globalMax = maxItemsPerPage,
                    )
                    pg.items.forEach { slot ->
                        slot.packageName?.let { placedPackages.add(it) }
                        slot.children.forEach { ch -> placedPackages.add(ch.packageName) }
                    }
                    pages.add(pg)
                }

                // Cover remaining installed user apps so Detailed preset includes ALL apps across layouts
                val unplacedUserApps = installedApps.filter { !it.isSystemApp && it.packageName !in placedPackages }
                if (unplacedUserApps.isNotEmpty()) {
                    val extraDraft = packAppsIntoMixedDraft(
                        id = "more-apps",
                        title = "More Apps",
                        apps = unplacedUserApps,
                        maxItemsPerPage = maxItemsPerPage,
                    )
                    pages.add(finalizeDraftToPage(extraDraft, order = pages.size, columns = columns, rows = rows, globalMax = maxItemsPerPage))
                }
                if (pages.none { it.isDefaultHome } && pages.isNotEmpty()) {
                    pages[0] = pages[0].copy(isDefaultHome = true)
                }
                pages.add(createWallpaperPage(order = pages.size, columns = columns))
            }
        }
        return normalizePageOrders(pages)
    }

    private fun packAppsIntoMixedDraft(
        id: String,
        title: String,
        apps: List<InstalledAppInfo>,
        maxItemsPerPage: Int,
    ): SuggestedPageDraft {
        val grouped = linkedMapOf<String, MutableList<InstalledAppInfo>>()
        val solos = mutableListOf<InstalledAppInfo>()
        for (app in apps) {
            val sf = app.suiteFolder
            if (!sf.isNullOrBlank()) {
                grouped.getOrPut(sf) { mutableListOf() }.add(app)
            } else {
                solos.add(app)
            }
        }
        val folders = mutableListOf<SelectableFolder>()
        val extraSolos = mutableListOf<InstalledAppInfo>()
        for ((name, members) in grouped) {
            if (members.size >= 2) {
                folders.add(
                    SelectableFolder(
                        id = "$id:${name.lowercase().replace(Regex("[^a-z0-9]+"), "-")}",
                        title = name,
                        checked = true,
                        children = members.map { SelectableApp(it.packageName, it.activityName, it.displayName, true) },
                    ),
                )
            } else {
                extraSolos.addAll(members)
            }
        }
        val combinedSolos = (solos + extraSolos).distinctBy { it.packageName }
        // If combined slots exceed maxItemsPerPage, bucket remaining apps into clean overflow folders
        val keepSoloSlots = (maxItemsPerPage - folders.size - 1).coerceAtLeast(0)
        val finalSolos = if (combinedSolos.size + folders.size <= maxItemsPerPage) {
            combinedSolos.map { SelectableApp(it.packageName, it.activityName, it.displayName, true) }
        } else {
            val kept = combinedSolos.take(keepSoloSlots).map { SelectableApp(it.packageName, it.activityName, it.displayName, true) }
            val overflow = combinedSolos.drop(keepSoloSlots)
            if (overflow.isNotEmpty()) {
                val chunked = overflow.chunked(12)
                chunked.forEachIndexed { idx, chunk ->
                    if (kept.size + folders.size < maxItemsPerPage) {
                        folders.add(
                            SelectableFolder(
                                id = "$id:suite-$idx",
                                title = if (idx == 0) "$title Suite" else "$title Suite ${idx + 1}",
                                checked = true,
                                children = chunk.map { SelectableApp(it.packageName, it.activityName, it.displayName, true) },
                            ),
                        )
                    } else {
                        // Append cleanly to last folder so ALL installed apps fit without dropping any!
                        val last = folders.lastOrNull()
                        if (last != null) {
                            folders[folders.lastIndex] = last.copy(
                                children = last.children + chunk.map { SelectableApp(it.packageName, it.activityName, it.displayName, true) },
                            )
                        }
                    }
                }
            }
            kept
        }
        return SuggestedPageDraft(
            templateId = id,
            title = title,
            subtitle = "${apps.size} installed apps",
            soloApps = finalSolos,
            folders = folders,
        )
    }

    fun createWallpaperPage(order: Int, columns: Int): StudioPage = StudioPage(
        id = "wallpaper",
        title = "Wallpaper",
        subtitle = "Clean wallpaper viewing page",
        order = order,
        isDefaultHome = false,
        isWallpaperPage = true,
        header = HeaderPlacement(enabled = false, row = 0, column = 0, columnSpan = columns, rowSpan = 1),
        items = emptyList(),
    )

    /** Pack up to `maxItems` neatly into bottom rows upward without touching the header row. */
    fun realignPageItems(
        items: List<LayoutSlotItem>,
        columns: Int = 4,
        rows: Int = 6,
        maxItems: Int = 8,
        headerRow: Int = 0,
    ): List<LayoutSlotItem> {
        val trimmed = items.take(maxItems.coerceIn(1, columns * (rows - 1)))
        val rowsNeeded = ((trimmed.size + columns - 1) / columns).coerceAtLeast(1)
        val startRow = (rows - rowsNeeded).coerceAtLeast(if (headerRow == 0) 1 else 0)
        return trimmed.mapIndexed { idx, item ->
            val r = (startRow + idx / columns).coerceIn(0, rows - 1)
            val c = idx % columns
            item.copy(row = r, column = c)
        }
    }

    fun realignAllPages(pages: List<StudioPage>, globalMax: Int, columns: Int = 4, rows: Int = 6): List<StudioPage> {
        return normalizePageOrders(pages).map { pg ->
            if (pg.isWallpaperPage) return@map pg
            val cap = pg.effectiveMaxItems(globalMax)
            val realigned = realignPageItems(
                items = pg.items,
                columns = columns,
                rows = rows,
                maxItems = cap,
                headerRow = if (pg.header.enabled) pg.header.row else -1,
            )
            pg.copy(items = realigned)
        }
    }

    fun normalizePageOrders(pages: List<StudioPage>): List<StudioPage> =
        pages.mapIndexed { idx, pg -> pg.copy(order = idx) }

    fun syncCategoriesAndWidgetsFromPages(config: HomeStudioConfig): HomeStudioConfig {
        val pageCategories = config.pages
            .filter { !it.isWallpaperPage && it.title.isNotBlank() }
            .sortedBy { it.order }
            .map { CategoryDefinition(id = it.id, title = it.title, subtitle = it.subtitle) }
        val mergedCategories = if (pageCategories.isNotEmpty()) pageCategories else config.categories

        val updatedWidgets = config.widgets.toMutableMap()
        config.pages.forEach { pg ->
            val wid = pg.boundWidgetId
            if (wid != null && updatedWidgets.containsKey(wid)) {
                val cur = updatedWidgets[wid]!!
                updatedWidgets[wid] = cur.copy(
                    categoryId = pg.id,
                    title = pg.title,
                    alignment = pg.header.alignmentOverride ?: cur.alignment,
                    verticalAlignment = pg.header.verticalAlignment,
                )
            }
        }
        return config.copy(categories = mergedCategories, widgets = updatedWidgets)
    }

    fun ensureCatalogCategoriesSeeded(catalog: AppCatalog, categories: List<CategoryDefinition>): List<CategoryDefinition> {
        val existingIds = categories.map { it.id }.toSet()
        val additions = catalog.categories
            .filter { it.id !in existingIds }
            .map { CategoryDefinition(it.id, it.title, it.description.ifBlank { null }) }
        return categories + additions
    }
}
