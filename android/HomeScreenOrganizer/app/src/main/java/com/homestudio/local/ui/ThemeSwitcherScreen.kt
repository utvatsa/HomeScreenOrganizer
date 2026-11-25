package com.homestudio.local.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap

/**
 * Full-screen One UI-inspired Interactive App Theme & Launcher Icon Studio.
 * Lets the user preview Light & Dark variants and synchronized Launcher Icon for
 * all 7 themes (#1 Warm Paper default, #2 Samsung Blue, #4 Nordic Olive,
 * #5 Copper Espresso, #6 Pacific Cobalt, #9 Sunlit Ochre, #10 Arctic Chrome) before applying.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeSwitcherScreen(
    currentThemeId: String,
    currentMode: String,
    currentSyncIcon: Boolean,
    onBack: () -> Unit,
    onApplyTheme: (themeId: String, mode: String, syncLauncherIcon: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val hostPalette = LocalStudioPalette.current

    var draftThemeId by remember(currentThemeId) { mutableStateOf(currentThemeId) }
    var draftMode by remember(currentMode) { mutableStateOf(currentMode) }
    var draftSyncIcon by remember(currentSyncIcon) { mutableStateOf(currentSyncIcon) }

    val draftSpec = remember(draftThemeId) { findThemeSpec(draftThemeId) }
    val sysDark = isSystemInDarkTheme()
    val previewIsDark = when (draftMode.lowercase()) {
        "light" -> false
        "dark" -> true
        else -> sysDark
    }
    val targetStagePalette = draftSpec.palette(previewIsDark)
    val stageBg by animateColorAsState(targetStagePalette.background, tween(240), label = "stageBg")
    val stageCard by animateColorAsState(targetStagePalette.card, tween(240), label = "stageCard")
    val stageAccent by animateColorAsState(targetStagePalette.accent, tween(240), label = "stageAccent")
    val stP = targetStagePalette.copy(background = stageBg, card = stageCard, accent = stageAccent)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(hostPalette.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // One UI Full-Screen Studio Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                shape = CircleShape,
                color = hostPalette.card,
                border = BorderStroke(1.dp, hostPalette.border),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("←", fontSize = 19.sp, color = hostPalette.textPrimary, fontWeight = FontWeight.Bold)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "App Themes & Icon",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = hostPalette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${draftSpec.numberLabel} ${draftSpec.title} • ${if (previewIsDark) "Dark" else "Light"} Preview",
                    style = MaterialTheme.typography.labelSmall,
                    color = hostPalette.textMuted,
                )
            }
            Button(
                onClick = { onApplyTheme(draftThemeId, draftMode, draftSyncIcon) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = stP.accent,
                    contentColor = stP.onAccent,
                ),
            ) {
                Text("Apply")
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Interactive One UI Live Sample Stage rendered dynamically in the selected theme
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(spring(dampingRatio = 0.82f, stiffness = 360f)),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = stP.background),
                border = BorderStroke(1.5.dp, stP.border),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ThemeIconThumb(iconRes = draftSpec.iconMipmapRes, sizeDp = 50)
                            Column {
                                Text(
                                    text = "Live UI & Icon Preview",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = stP.textPrimary,
                                )
                                Text(
                                    text = draftSpec.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = stP.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    // Mode Choice inside Every Theme (Dark / Light / Auto)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("system" to "Auto", "light" to "Light", "dark" to "Dark").forEach { (id, label) ->
                            val selected = draftMode == id
                            FilterChip(
                                selected = selected,
                                onClick = { draftMode = id },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = stP.accentSoft,
                                    selectedLabelColor = stP.accent,
                                    containerColor = stP.card,
                                    labelColor = stP.textSecondary,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = stP.border,
                                    selectedBorderColor = stP.accent,
                                ),
                            )
                        }
                    }

                    // Sample Studio Card
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = stP.card,
                        border = BorderStroke(1.dp, stP.border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "1. Daily Essentials",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = stP.textPrimary,
                                )
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = stP.accentSoft,
                                ) {
                                    Text(
                                        text = "Default Home",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = stP.accent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                            Text(
                                text = "One-hand 8-icon limit • Category Header Row 0 • Google Suite folder",
                                style = MaterialTheme.typography.bodySmall,
                                color = stP.textSecondary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = stP.accent,
                                ) {
                                    Text(
                                        text = "+ Suggested",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = stP.onAccent,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = stP.cardElevated,
                                    border = BorderStroke(1.dp, stP.border),
                                ) {
                                    Text(
                                        text = "Edit Header",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = stP.textPrimary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Sample Floating One UI 9 Dock Pill inside stage
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = stP.card,
                        border = BorderStroke(1.dp, stP.border),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf("Studio" to true, "Current" to false, "Catalog" to false).forEach { (lbl, sel) ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (sel) stP.accentSoft else Color.Transparent)
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = lbl,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (sel) stP.accent else stP.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Launcher Icon Sync Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = hostPalette.card),
                border = BorderStroke(1.dp, hostPalette.border),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Update App Icon Background with Theme",
                            style = MaterialTheme.typography.titleSmall,
                            color = hostPalette.textPrimary,
                        )
                        Text(
                            text = "Switches the app icon background to match while keeping the logo intact. Your home screen layouts & Studio grid canvas stay crisp and untouched.",
                            style = MaterialTheme.typography.bodySmall,
                            color = hostPalette.textSecondary,
                        )
                    }
                    Switch(
                        checked = draftSyncIcon,
                        onCheckedChange = { draftSyncIcon = it },
                    )
                }
            }

            // Curated 7-Theme One UI Picker List
            Text(
                text = "Choose Theme Palette (${APP_THEMES.size} Curated Options)",
                style = MaterialTheme.typography.titleMedium,
                color = hostPalette.textPrimary,
            )

            APP_THEMES.forEach { spec ->
                val isChosen = spec.id == draftThemeId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { draftThemeId = spec.id },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isChosen) hostPalette.accentSoft.copy(alpha = 0.45f) else hostPalette.card,
                    ),
                    border = BorderStroke(
                        width = if (isChosen) 2.dp else 1.dp,
                        color = if (isChosen) hostPalette.accent else hostPalette.border,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ThemeIconThumb(iconRes = spec.iconMipmapRes, sizeDp = 52)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = spec.light.accent,
                                ) {
                                    Text(
                                        text = spec.numberLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    )
                                }
                                Text(
                                    text = spec.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = hostPalette.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = spec.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = hostPalette.textSecondary,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 2.dp),
                            ) {
                                spec.previewSwatches.forEach { sw ->
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(sw)
                                            .border(1.dp, hostPalette.border, CircleShape),
                                    )
                                }
                            }
                        }
                        if (isChosen) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = hostPalette.accent,
                            ) {
                                Text(
                                    text = "Selected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = hostPalette.onAccent,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onApplyTheme(draftThemeId, draftMode, draftSyncIcon) },
                    modifier = Modifier.weight(1.6f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = stP.accent,
                        contentColor = stP.onAccent,
                    ),
                ) {
                    Text("Apply ${draftSpec.title}")
                }
            }
        }
    }
}

@Composable
private fun ThemeIconThumb(iconRes: Int, sizeDp: Int) {
    val context = LocalContext.current
    val bmp = remember(iconRes) {
        ContextCompat.getDrawable(context, iconRes)?.toBitmap(180, 180)?.asImageBitmap()
    }
    Box(
        modifier = Modifier.size(sizeDp.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = "Theme Launcher Icon",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
