package com.homestudio.local.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homestudio.local.data.HomeStudioConfig
import com.homestudio.local.data.LayoutSlotItem
import com.homestudio.local.data.ObservedSnapshotResult
import com.homestudio.local.data.StudioPage

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CurrentLayoutTab(
    config: HomeStudioConfig,
    snapshot: ObservedSnapshotResult,
    wallpaperBitmap: Bitmap?,
    onChangeOrientation: (String) -> Unit,
    onToggleWallpaper: (Boolean) -> Unit,
    onOpenWallpaperPicker: () -> Unit,
    onImportObservedJson: () -> Unit,
    onRefreshSnapshot: () -> Unit,
    onCloneToStudio: (List<StudioPage>) -> Unit,
    tutorialRegistry: TutorialTargetRegistry? = null,
) {
    val p = LocalStudioPalette.current
    var inspectedFolder by remember { mutableStateOf<LayoutSlotItem?>(null) }

    val orientation = config.preferences.layoutViewOrientation
    val showWallpaper = config.preferences.showWallpaperInPreview

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tutorialTarget(tutorialRegistry, "current_summary")
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = p.cardTint),
        border = BorderStroke(1.dp, p.border),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Current Phone Layout (${snapshot.pages.size} pages)",
                    style = MaterialTheme.typography.titleMedium,
                    color = p.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .background(
                            if (snapshot.isLiveSnapshot) p.positiveSurface else p.warningSurface,
                            RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = if (snapshot.isLiveSnapshot) "Live Snapshot" else "Widgets Only",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (snapshot.isLiveSnapshot) p.positiveText else p.warningText,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            Text(
                text = snapshot.sourceLabel,
                style = MaterialTheme.typography.bodySmall,
                color = p.textSecondary,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(onClick = onRefreshSnapshot) {
                    Text("Refresh Live")
                }
                OutlinedButton(onClick = onImportObservedJson) {
                    Text("Import Snapshot JSON")
                }
                if (snapshot.pages.isNotEmpty()) {
                    OutlinedButton(onClick = { onCloneToStudio(snapshot.pages) }) {
                        Text("Load Into Studio")
                    }
                }
            }
        }
    }

    LayoutViewerControlBar(
        orientation = orientation,
        showWallpaper = showWallpaper,
        onChangeOrientation = onChangeOrientation,
        onToggleWallpaper = onToggleWallpaper,
        onOpenWallpaperPicker = onOpenWallpaperPicker,
    )

    if (snapshot.pages.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = p.card),
            border = BorderStroke(1.dp, p.border),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("No Current Layout Snapshot Loaded", style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                Text(
                    text = "Run `.venv/bin/homescreenorganizer inspect --sync-app` over ADB or tap Import Snapshot JSON to inspect your live One UI Home pages here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary,
                )
            }
        }
    } else if (orientation == "horizontal") {
        HorizontalPagesDeck(pages = snapshot.pages) { idx, pg ->
            CurrentPageCard(
                idx = idx,
                page = pg,
                config = config,
                wallpaperBitmap = wallpaperBitmap,
                showWallpaper = showWallpaper,
                onFolderClick = { inspectedFolder = it },
            )
        }
    } else {
        snapshot.pages.forEachIndexed { idx, pg ->
            CurrentPageCard(
                idx = idx,
                page = pg,
                config = config,
                wallpaperBitmap = wallpaperBitmap,
                showWallpaper = showWallpaper,
                onFolderClick = { inspectedFolder = it },
            )
        }
    }

    inspectedFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { inspectedFolder = null },
            title = { Text("Folder: ${folder.label}") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (folder.children.isEmpty()) {
                        Text("No apps recorded in folder.", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
                    } else {
                        folder.children.forEach { ch ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                InstalledAppIcon(ch.packageName, ch.label, size = 28.dp)
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(ch.label, style = MaterialTheme.typography.bodyMedium, color = p.textPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { inspectedFolder = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun CurrentPageCard(
    idx: Int,
    page: StudioPage,
    config: HomeStudioConfig,
    wallpaperBitmap: Bitmap?,
    showWallpaper: Boolean,
    onFolderClick: (LayoutSlotItem) -> Unit,
) {
    val p = LocalStudioPalette.current
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = p.card),
        border = BorderStroke(1.dp, if (page.isDefaultHome) p.accent.copy(alpha = 0.55f) else p.border),
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "${idx + 1}. ${page.title}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = p.textPrimary,
                    )
                    if (page.isDefaultHome) {
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
                    text = if (page.isWallpaperPage) "Wallpaper" else "${page.items.size} slots",
                    style = MaterialTheme.typography.labelSmall,
                    color = p.textMuted,
                )
            }

            MiniPhoneGridPreview(
                page = page,
                config = config,
                wallpaperBitmap = wallpaperBitmap,
                showWallpaper = showWallpaper,
                onFolderClick = onFolderClick,
            )
        }
    }
}
