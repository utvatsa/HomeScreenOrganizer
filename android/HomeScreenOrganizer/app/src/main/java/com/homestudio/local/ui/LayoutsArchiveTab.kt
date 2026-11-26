package com.homestudio.local.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homestudio.local.data.HomeStudioConfig
import com.homestudio.local.data.HomeStudioStore
import com.homestudio.local.data.LayoutArchiveEntry
import com.homestudio.local.data.LayoutSnapshotKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ArchiveFilter(val label: String) {
    ALL("All"),
    APPLIED("Applied Archive"),
    DRAFTS("Saved Drafts"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayoutsArchiveTab(
    config: HomeStudioConfig,
    archiveEntries: List<LayoutArchiveEntry>,
    wallpaperBitmap: Bitmap?,
    onSaveCurrentAsDraft: (name: String, notes: String?) -> Unit,
    onLoadIntoStudio: (LayoutArchiveEntry) -> Unit,
    onSwitchActive: (LayoutArchiveEntry) -> Unit,
    onRenameEntry: (LayoutArchiveEntry, String) -> Unit,
    onDeleteDraft: (LayoutArchiveEntry) -> Unit,
    onExportEntryHostJson: (LayoutArchiveEntry) -> Unit,
) {
    val p = LocalStudioPalette.current
    var filter by remember { mutableStateOf(ArchiveFilter.ALL) }
    var previewEntry by remember { mutableStateOf<LayoutArchiveEntry?>(null) }
    var renameTarget by remember { mutableStateOf<LayoutArchiveEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<LayoutArchiveEntry?>(null) }
    var showNewDraftDialog by remember { mutableStateOf(false) }

    val appliedCount = archiveEntries.count { it.kind == LayoutSnapshotKind.APPLIED }
    val draftCount = archiveEntries.count { it.kind == LayoutSnapshotKind.DRAFT }
    val activeEntry = archiveEntries.firstOrNull { it.isActive }

    val visibleEntries = remember(archiveEntries, filter) {
        when (filter) {
            ArchiveFilter.ALL -> archiveEntries
            ArchiveFilter.APPLIED -> archiveEntries.filter { it.kind == LayoutSnapshotKind.APPLIED }
            ArchiveFilter.DRAFTS -> archiveEntries.filter { it.kind == LayoutSnapshotKind.DRAFT }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = p.cardTint),
        border = BorderStroke(1.dp, p.border),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Saved & Applied Layouts",
                style = MaterialTheme.typography.titleMedium,
                color = p.textPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (activeEntry != null) {
                    "Active: ${activeEntry.name} • $appliedCount applied snapshot(s) • $draftCount draft(s) retained."
                } else {
                    "$appliedCount applied snapshot(s) • $draftCount draft(s). Every applied layout is preserved in history."
                },
                style = MaterialTheme.typography.bodySmall,
                color = p.textSecondary,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = filter == ArchiveFilter.ALL,
                    onClick = { filter = ArchiveFilter.ALL },
                    label = { Text("All (${archiveEntries.size})") },
                )
                FilterChip(
                    selected = filter == ArchiveFilter.APPLIED,
                    onClick = { filter = ArchiveFilter.APPLIED },
                    label = { Text("Applied ($appliedCount)") },
                )
                FilterChip(
                    selected = filter == ArchiveFilter.DRAFTS,
                    onClick = { filter = ArchiveFilter.DRAFTS },
                    label = { Text("Drafts ($draftCount)") },
                )
            }

            Button(
                onClick = { showNewDraftDialog = true },
                enabled = config.pages.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("+ Save Current Studio as JSON Draft")
            }
        }
    }

    if (visibleEntries.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = p.card),
            border = BorderStroke(1.dp, p.border),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = when (filter) {
                        ArchiveFilter.ALL -> "No Saved or Applied Snapshots Yet"
                        ArchiveFilter.APPLIED -> "No Applied Snapshots Recorded Yet"
                        ArchiveFilter.DRAFTS -> "No Saved JSON Drafts Yet"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = p.textPrimary,
                )
                Text(
                    text = "Build a layout in Studio and use Step 4 to save a named JSON draft or Apply / Mark Applied. Each applied setup is retained chronologically without overwriting prior history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary,
                )
            }
        }
    } else {
        visibleEntries.forEach { entry ->
            ArchiveEntryCard(
                entry = entry,
                onPreview = { previewEntry = entry },
                onLoadIntoStudio = { onLoadIntoStudio(entry) },
                onSwitchActive = { onSwitchActive(entry) },
                onRename = { renameTarget = entry },
                onDelete = { deleteTarget = entry },
            )
        }
    }

    previewEntry?.let { entry ->
        ArchiveSnapshotPreviewDialog(
            entry = entry,
            wallpaperBitmap = wallpaperBitmap,
            onDismiss = { previewEntry = null },
            onLoadIntoStudio = {
                previewEntry = null
                onLoadIntoStudio(entry)
            },
            onSwitchActive = {
                previewEntry = null
                onSwitchActive(entry)
            },
            onExportHostJson = { onExportEntryHostJson(entry) },
        )
    }

    renameTarget?.let { entry ->
        var draftName by remember(entry.id) { mutableStateOf(entry.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Snapshot") },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    label = { Text("Snapshot Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = draftName.trim()
                        if (clean.isNotEmpty()) {
                            onRenameEntry(entry, clean)
                            renameTarget = null
                        }
                    },
                    enabled = draftName.isNotBlank(),
                ) { Text("Save Name") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
        )
    }

    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Draft?") },
            text = {
                Text(
                    text = "Delete non-active draft '${entry.name}' (${entry.pageCount} pages)? Applied archive history is untouched.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary,
                )
            },
            confirmButton = {
                Button(onClick = {
                    onDeleteDraft(entry)
                    deleteTarget = null
                }) { Text("Delete Draft") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }

    if (showNewDraftDialog) {
        SaveSnapshotDialog(
            title = "Save Studio JSON Draft",
            defaultName = HomeStudioStore.defaultSnapshotTitle(
                kind = LayoutSnapshotKind.DRAFT,
                config = config,
                epochMs = System.currentTimeMillis(),
            ),
            confirmLabel = "Save Draft",
            onDismiss = { showNewDraftDialog = false },
            onConfirm = { name, notes ->
                showNewDraftDialog = false
                onSaveCurrentAsDraft(name, notes)
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArchiveEntryCard(
    entry: LayoutArchiveEntry,
    onPreview: () -> Unit,
    onLoadIntoStudio: () -> Unit,
    onSwitchActive: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = LocalStudioPalette.current
    val isApplied = entry.kind == LayoutSnapshotKind.APPLIED

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = 0.82f, stiffness = 360f)),
        colors = CardDefaults.cardColors(containerColor = p.card),
        border = BorderStroke(1.dp, if (entry.isActive) p.positive.copy(alpha = 0.65f) else p.border),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = p.textPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (entry.isActive) {
                        StatusPillBadge("Active", p.positiveSurface, p.positiveText)
                    }
                    StatusPillBadge(
                        text = if (isApplied) "Applied" else "Draft",
                        bg = if (isApplied) p.accentSoft else p.secondarySoft,
                        fg = if (isApplied) p.accent else p.secondaryAccent,
                    )
                }
            }

            Text(
                text = "${entry.pageCount} pages • Default Home: ${entry.defaultHomeTitle ?: "None"}",
                style = MaterialTheme.typography.bodySmall,
                color = p.textSecondary,
            )
            Text(
                text = "${entry.gridSummary} • ${formatArchiveTimestamp(entry.updatedAtEpochMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = p.textMuted,
            )
            if (!entry.notes.isNullOrBlank()) {
                Text(
                    text = entry.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textMuted,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(onClick = onPreview) { Text("Preview") }
                Button(onClick = onLoadIntoStudio) { Text("Load in Studio") }
                if (!entry.isActive) {
                    OutlinedButton(onClick = onSwitchActive) {
                        Text(if (isApplied) "Switch Active" else "Apply Setup")
                    }
                }
                OutlinedButton(onClick = onRename) { Text("Rename") }
                if (!entry.isActive && entry.kind == LayoutSnapshotKind.DRAFT) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = p.danger)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveSnapshotPreviewDialog(
    entry: LayoutArchiveEntry,
    wallpaperBitmap: Bitmap?,
    onDismiss: () -> Unit,
    onLoadIntoStudio: () -> Unit,
    onSwitchActive: () -> Unit,
    onExportHostJson: () -> Unit,
) {
    val p = LocalStudioPalette.current
    var showRawJson by remember(entry.id) { mutableStateOf(false) }
    val sortedPages = remember(entry.id) { entry.config.pages.sortedBy { it.order } }
    val rawJson = remember(entry.id, showRawJson) {
        if (showRawJson) HomeStudioStore.toJson(entry.config) else ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${entry.pageCount} pages • ${entry.gridSummary} • ${formatArchiveTimestamp(entry.updatedAtEpochMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = p.textMuted,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !showRawJson,
                        onClick = { showRawJson = false },
                        label = { Text("Pages (${sortedPages.size})") },
                    )
                    FilterChip(
                        selected = showRawJson,
                        onClick = { showRawJson = true },
                        label = { Text("JSON Snapshot") },
                    )
                }

                if (showRawJson) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState()),
                        shape = RoundedCornerShape(12.dp),
                        color = p.cardElevated,
                        border = BorderStroke(1.dp, p.border),
                    ) {
                        Text(
                            text = rawJson,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = p.textPrimary,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (sortedPages.isEmpty()) {
                            Text("Snapshot has 0 pages.", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
                        } else {
                            sortedPages.forEachIndexed { idx, pg ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "${idx + 1}. ${pg.title}" + (if (pg.isDefaultHome) " (Home)" else ""),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = p.textPrimary,
                                    )
                                    MiniPhoneGridPreview(
                                        page = pg,
                                        config = entry.config,
                                        wallpaperBitmap = wallpaperBitmap,
                                        showWallpaper = entry.config.preferences.showWallpaperInPreview,
                                        tallPhoneAspect = false,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!entry.isActive) {
                    OutlinedButton(onClick = onSwitchActive) {
                        Text(if (entry.kind == LayoutSnapshotKind.APPLIED) "Switch Active" else "Apply")
                    }
                }
                Button(onClick = onLoadIntoStudio) { Text("Load in Studio") }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onExportHostJson) { Text("Export JSON") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
fun SaveSnapshotDialog(
    title: String,
    defaultName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, notes: String?) -> Unit,
) {
    var name by remember(defaultName) { mutableStateOf(defaultName) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Snapshot Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim().ifBlank { defaultName }, notes.trim().ifBlank { null }) },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun StatusPillBadge(
    text: String,
    bg: androidx.compose.ui.graphics.Color,
    fg: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

fun formatArchiveTimestamp(epochMs: Long): String {
    val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)
    return fmt.format(Date(epochMs))
}
