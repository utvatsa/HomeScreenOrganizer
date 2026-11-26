package com.homestudio.local.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.homestudio.local.data.HomeStudioConfig
import com.homestudio.local.data.LayoutSlotItem
import com.homestudio.local.data.StudioPage
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun LayoutViewerControlBar(
    orientation: String,
    showWallpaper: Boolean,
    onChangeOrientation: (String) -> Unit,
    onToggleWallpaper: (Boolean) -> Unit,
    onOpenWallpaperPicker: () -> Unit,
    wallpaperBitmap: Bitmap? = null,
    tutorialRegistry: TutorialTargetRegistry? = null,
) {
    val p = LocalStudioPalette.current
    val isHorizontal = orientation == "horizontal"
    val previewThumb: ImageBitmap? = remember(wallpaperBitmap) {
        wallpaperBitmap?.asImageBitmap()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tutorialTarget(tutorialRegistry, "viewer_controls")
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = p.card),
        border = BorderStroke(1.dp, p.border),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Viewer & Backdrop",
                    style = MaterialTheme.typography.labelLarge,
                    color = p.textPrimary,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (showWallpaper) p.positiveSurface else p.secondarySoft,
                ) {
                    Text(
                        text = "${if (isHorizontal) "Horizontal" else "Vertical"} • ${if (showWallpaper) "Wallpaper On" else "Clean Dark"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (showWallpaper) p.positiveText else p.secondaryAccent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
            }

            // Single unified 1-tap Orientation Toggle Pill (Vertical <-> Horizontal)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(p.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isHorizontal) "⇄" else "⇅",
                        color = p.accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "View Mode",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = p.textPrimary,
                        maxLines = 1,
                    )
                    Text(
                        text = if (isHorizontal) "Horizontal deck" else "Vertical stack",
                        style = MaterialTheme.typography.labelSmall,
                        color = p.textMuted,
                        maxLines = 1,
                    )
                }
                SlidingOrientationTogglePill(
                    isHorizontal = isHorizontal,
                    onToggle = {
                        onChangeOrientation(if (isHorizontal) "vertical" else "horizontal")
                    },
                )
            }

            HorizontalDivider(color = p.border.copy(alpha = 0.55f))

            // Wallpaper On/Off Switch + Live Swatch + Change Picker Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF101318))
                        .border(
                            width = 1.dp,
                            color = if (showWallpaper) p.accent.copy(alpha = 0.7f) else p.border,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable { onOpenWallpaperPicker() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (showWallpaper && previewThumb != null) {
                        Image(
                            bitmap = previewThumb,
                            contentDescription = "Wallpaper swatch",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = if (showWallpaper) "◐" else "○",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wallpaper",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = p.textPrimary,
                        maxLines = 1,
                    )
                    Text(
                        text = if (showWallpaper) "Visible in preview" else "Clean dark canvas",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (showWallpaper) p.positiveText else p.textMuted,
                        maxLines = 1,
                    )
                }
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onOpenWallpaperPicker),
                    shape = RoundedCornerShape(999.dp),
                    color = p.secondarySoft,
                    border = BorderStroke(1.dp, p.border),
                ) {
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelMedium,
                        color = p.secondaryAccent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    )
                }
                Switch(
                    checked = showWallpaper,
                    onCheckedChange = onToggleWallpaper,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = p.onAccent,
                        checkedTrackColor = p.accent,
                        uncheckedThumbColor = p.textSecondary,
                        uncheckedTrackColor = p.cardElevated,
                        uncheckedBorderColor = p.border,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SlidingOrientationTogglePill(
    isHorizontal: Boolean,
    onToggle: () -> Unit,
) {
    val p = LocalStudioPalette.current
    val pillWidth = 136.dp
    val pillHeight = 36.dp
    val thumbOffset by animateDpAsState(
        targetValue = if (isHorizontal) 67.dp else 3.dp,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "orientationThumb",
    )

    Box(
        modifier = Modifier
            .width(pillWidth)
            .height(pillHeight)
            .clip(RoundedCornerShape(999.dp))
            .background(p.cardElevated)
            .border(1.dp, p.border, RoundedCornerShape(999.dp))
            .clickable(onClick = onToggle),
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset, y = 3.dp)
                .width(66.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(p.accent),
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Vertical",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (!isHorizontal) FontWeight.Bold else FontWeight.Medium,
                    color = if (!isHorizontal) p.onAccent else p.textSecondary,
                    maxLines = 1,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Horiz.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isHorizontal) FontWeight.Bold else FontWeight.Medium,
                    color = if (isHorizontal) p.onAccent else p.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun HorizontalPagesDeck(
    pages: List<StudioPage>,
    cardContent: @Composable (index: Int, page: StudioPage) -> Unit,
) {
    val p = LocalStudioPalette.current
    if (pages.isEmpty()) return

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Swipe horizontally • Page ${pagerState.currentPage + 1} of ${pages.size}",
                style = MaterialTheme.typography.labelMedium,
                color = p.secondaryAccent,
            )
            val curr = pages.getOrNull(pagerState.currentPage)
            if (curr?.isDefaultHome == true) {
                Box(
                    modifier = Modifier
                        .background(p.positiveSurface, RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("Default Home", style = MaterialTheme.typography.labelSmall, color = p.positiveText)
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 10.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { idx ->
            val pageOffset = ((pagerState.currentPage - idx) + pagerState.currentPageOffsetFraction).absoluteValue
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val scale = lerp(0.94f, 1f, 1f - pageOffset.coerceIn(0f, 1f))
                        scaleX = scale
                        scaleY = scale
                        alpha = lerp(0.68f, 1f, 1f - pageOffset.coerceIn(0f, 1f))
                    },
            ) {
                cardContent(idx, pages[idx])
            }
        }

        // Interactive One UI page indicator dots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.forEachIndexed { idx, pg ->
                val active = pagerState.currentPage == idx
                val dotWidth by animateDpAsState(
                    targetValue = if (active) 20.dp else 7.dp,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                    label = "dotW",
                )
                val dotColor by animateColorAsState(
                    targetValue = when {
                        active -> p.accent
                        pg.isDefaultHome -> p.secondaryAccent.copy(alpha = 0.7f)
                        else -> p.border
                    },
                    label = "dotColor",
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(7.dp)
                        .width(dotWidth)
                        .clip(CircleShape)
                        .background(dotColor)
                        .clickable {
                            scope.launch { pagerState.animateScrollToPage(idx) }
                        },
                )
            }
        }
    }
}

@Composable
fun MiniPhoneGridPreview(
    page: StudioPage,
    config: HomeStudioConfig,
    wallpaperBitmap: Bitmap? = null,
    showWallpaper: Boolean = true,
    tallPhoneAspect: Boolean = true,
    onFolderClick: ((LayoutSlotItem) -> Unit)? = null,
) {
    val rows = config.preferences.gridRows
    val cols = config.preferences.gridColumns
    val alignment = page.header.alignmentOverride ?: config.globalTheme.alignment
    val imageBitmap: ImageBitmap? = remember(wallpaperBitmap, showWallpaper) {
        if (showWallpaper && wallpaperBitmap != null) wallpaperBitmap.asImageBitmap() else null
    }

    // Simulated Home Screen canvas intentionally stays neutral dark charcoal so layout icons,
    // folders, widgets, and wallpaper remain vibrant and distinct regardless of app chrome theme.
    val frameModifier = if (tallPhoneAspect) {
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.54f)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF101318))
            .border(1.5.dp, Color(0xFF2B313D), RoundedCornerShape(24.dp))
    } else {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF101318))
            .border(1.dp, Color(0xFF2B313D), RoundedCornerShape(18.dp))
    }

    Box(modifier = frameModifier) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Home screen wallpaper backdrop",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            // Subtle bottom/top gradient scrim so light header typography remains crisp over photos
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.24f),
                                Color.Black.copy(alpha = 0.08f),
                                Color.Black.copy(alpha = 0.36f),
                            ),
                        ),
                    ),
            )
        }

        Column(
            modifier = if (tallPhoneAspect) {
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (tallPhoneAspect) {
                // Subtle simulated Samsung One UI status bar + punch-hole camera dot
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                ) {
                    Text(
                        text = "9:41",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.72f))
                            .border(0.75.dp, Color.White.copy(alpha = 0.22f), CircleShape),
                    )
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 10.dp, height = 5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.45f)),
                        )
                    }
                }
            }

            for (r in 0 until rows) {
                val rowModifier = if (tallPhoneAspect) {
                    Modifier.fillMaxWidth().weight(1f)
                } else {
                    Modifier.fillMaxWidth()
                }
                if (page.header.enabled && !page.isWallpaperPage && page.header.row == r) {
                    Box(
                        modifier = rowModifier,
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (tallPhoneAspect) 40.dp else 34.dp)
                                .background(
                                    Color.Black.copy(alpha = if (imageBitmap != null) 0.14f else 0f),
                                    RoundedCornerShape(8.dp),
                                )
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.22f),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 8.dp),
                            contentAlignment = when (alignment) {
                                "center" -> Alignment.Center
                                "end" -> Alignment.CenterEnd
                                else -> Alignment.CenterStart
                            },
                        ) {
                            Text(
                                text = page.title,
                                color = Color.White.copy(alpha = config.globalTheme.opacity),
                                fontSize = if (tallPhoneAspect) 16.sp else 15.sp,
                                fontWeight = FontWeight.Light,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    val rowItems = page.items.filter { it.row == r }
                    if (rowItems.isEmpty()) {
                        Box(
                            modifier = if (tallPhoneAspect) {
                                rowModifier
                            } else {
                                Modifier.fillMaxWidth().height(if (page.isWallpaperPage && r == 3) 44.dp else 18.dp)
                            },
                        ) {
                            if (page.isWallpaperPage && r == 3) {
                                Surface(
                                    modifier = Modifier.align(Alignment.Center),
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color.Black.copy(alpha = 0.38f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                ) {
                                    Text(
                                        text = if (imageBitmap != null) "Clean Wallpaper View" else "Clean Wallpaper Page",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        color = Color.White.copy(alpha = 0.88f),
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = rowModifier,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            for (c in 0 until cols) {
                                val item = rowItems.firstOrNull { it.column == c }
                                Box(
                                    modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (item != null) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = if (item.type == "folder" && onFolderClick != null) {
                                                Modifier.clickable { onFolderClick(item) }
                                            } else {
                                                Modifier
                                            },
                                        ) {
                                            when (item.type) {
                                                "folder" -> MiniFolderSquircle(item)
                                                "widget" -> Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                                                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Text(
                                                        text = "W",
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                }
                                                else -> InstalledAppIcon(
                                                    packageName = item.packageName,
                                                    fallbackLabel = item.label,
                                                    size = 34.dp,
                                                )
                                            }
                                            Text(
                                                text = item.label,
                                                color = Color(0xFFF1F5F9),
                                                fontSize = 9.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (tallPhoneAspect) {
                // Subtle simulated One UI dock & gesture pill at screen bottom
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.76f)
                            .height(28.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            repeat(4) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.24f)),
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.35f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniFolderSquircle(folder: LayoutSlotItem) {
    val previewChildren = folder.children.take(4)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .border(1.dp, Color.White.copy(alpha = 0.34f), RoundedCornerShape(11.dp))
            .padding(3.dp),
    ) {
        if (previewChildren.isEmpty()) {
            Text(
                text = "0",
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxSize()) {
                for (r in 0..1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        for (c in 0..1) {
                            val idx = r * 2 + c
                            val ch = previewChildren.getOrNull(idx)
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (ch != null) {
                                    InstalledAppIcon(
                                        packageName = ch.packageName,
                                        fallbackLabel = ch.label,
                                        size = 13.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
