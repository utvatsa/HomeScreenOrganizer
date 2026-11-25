package com.homestudio.local.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homestudio.local.data.HomeStudioConfig
import com.homestudio.local.data.StudioPage
import com.homestudio.local.wallpaper.WallpaperEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Samsung One UI-style Home Screen Wallpaper selector dialog.
 * Strictly applies wallpapers to unlocked Home Screen ONLY (WallpaperManager.FLAG_SYSTEM);
 * the lock screen is never modified.
 */
@Composable
fun OneUiWallpaperSheet(
    config: HomeStudioConfig,
    previewPage: StudioPage?,
    currentWallpaperBitmap: Bitmap?,
    onDismiss: () -> Unit,
    onPreviewSelected: (sourceId: String, bitmap: Bitmap) -> Unit,
    onAppliedToHomeScreen: (sourceId: String, bitmap: Bitmap) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val p = LocalStudioPalette.current
    val scope = rememberCoroutineScope()

    var selectedSource by remember { mutableStateOf(config.preferences.activeWallpaperSource) }
    var rawCandidate by remember(currentWallpaperBitmap) {
        mutableStateOf(
            currentWallpaperBitmap ?: WallpaperEngine.renderPresetBitmap("obsidian-aura", 540, 1170),
        )
    }
    var dimAmount by remember { mutableFloatStateOf(0.12f) }
    var isApplying by remember { mutableStateOf(false) }

    val effectivePreview = remember(rawCandidate, dimAmount) {
        WallpaperEngine.applyDimOverlay(rawCandidate, dimAmount)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    WallpaperEngine.decodeUriToBitmap(context, uri, maxDimPx = 1600)
                }
                if (bmp != null) {
                    rawCandidate = bmp
                    selectedSource = "gallery"
                } else {
                    onError("Could not decode the selected gallery image.")
                }
            }
        }
    }

    val demoPage = remember(previewPage, config.pages) {
        previewPage
            ?: config.pages.firstOrNull { it.isDefaultHome }
            ?: config.pages.firstOrNull()
            ?: StudioPage(id = "preview", title = "Daily Essentials", isDefaultHome = true)
    }

    AlertDialog(
        onDismissRequest = { if (!isApplying) onDismiss() },
        title = { Text("Home Screen Wallpaper") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = p.positiveSurface,
                    border = BorderStroke(1.dp, p.positive.copy(alpha = 0.4f)),
                ) {
                    Text(
                        text = "Unlocked Home Screen Only • Lock screen untouched",
                        style = MaterialTheme.typography.labelSmall,
                        color = p.positiveText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }

                // Live One UI Home Preview with Category Header & Icons over Candidate Wallpaper
                MiniPhoneGridPreview(
                    page = demoPage,
                    config = config,
                    wallpaperBitmap = effectivePreview,
                    showWallpaper = true,
                )

                Text(
                    text = "Contrast Dim Overlay: ${(dimAmount * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = p.textPrimary,
                )
                @Suppress("DEPRECATION")
                Slider(
                    value = dimAmount,
                    onValueChange = { dimAmount = it },
                    valueRange = 0f..0.65f,
                )

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Choose Image from Gallery")
                }

                Text(
                    text = "One UI Curated Wallpapers",
                    style = MaterialTheme.typography.labelLarge,
                    color = p.textPrimary,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WallpaperEngine.CURATED_PRESETS.forEach { preset ->
                        val sourceKey = "preset:${preset.id}"
                        val isSelected = selectedSource == sourceKey
                        val thumb = remember(preset.id) {
                            WallpaperEngine.renderPresetBitmap(preset.id, 180, 320).asImageBitmap()
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(84.dp)
                                .clickable {
                                    selectedSource = sourceKey
                                    rawCandidate = WallpaperEngine.renderPresetBitmap(preset.id, 720, 1560)
                                },
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(78.dp)
                                    .height(116.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) p.accent else p.border,
                                        shape = RoundedCornerShape(14.dp),
                                    ),
                            ) {
                                Image(
                                    bitmap = thumb,
                                    contentDescription = preset.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(5.dp)
                                            .size(16.dp)
                                            .background(p.accent, RoundedCornerShape(999.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("✓", color = p.onAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Text(
                                text = preset.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) p.accent else p.textSecondary,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                WallpaperEngine.savePreviewBitmap(context, effectivePreview)
                            }
                            onPreviewSelected(selectedSource, effectivePreview)
                        }
                    },
                    enabled = !isApplying,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Use in App Previews Only")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isApplying,
                onClick = {
                    isApplying = true
                    scope.launch {
                        val res = withContext(Dispatchers.IO) {
                            WallpaperEngine.applyUnlockedHomeWallpaper(context, effectivePreview)
                        }
                        isApplying = false
                        res.onSuccess {
                            onAppliedToHomeScreen(selectedSource, effectivePreview)
                        }.onFailure { err ->
                            onError("Failed to set home wallpaper: ${err.message}")
                        }
                    }
                },
            ) {
                Text(if (isApplying) "Applying..." else "Set Home Wallpaper")
            }
        },
        dismissButton = {
            TextButton(enabled = !isApplying, onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
