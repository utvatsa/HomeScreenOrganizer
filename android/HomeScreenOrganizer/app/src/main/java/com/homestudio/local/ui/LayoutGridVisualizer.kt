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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayoutViewerControlBar(
    orientation: String,
    showWallpaper: Boolean,
    onChangeOrientation: (String) -> Unit,
    onToggleWallpaper: (Boolean) -> Unit,
    onOpenWallpaperPicker: () -> Unit,
) {
    val p = LocalStudioPalette.current
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = p.card),
        border = BorderStroke(1.dp, p.border),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Viewer Style & Wallpaper",
                style = MaterialTheme.typography.labelLarge,
                color = p.textPrimary,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = orientation != "horizontal",
                    onClick = { onChangeOrientation("vertical") },
                    label = { Text("Vertical Stack") },
                )
                FilterChip(
                    selected = orientation == "horizontal",
                    onClick = { onChangeOrientation("horizontal") },
                    label = { Text("Horizontal Pages") },
                )
                FilterChip(
                    selected = showWallpaper,
                    onClick = { onToggleWallpaper(!showWallpaper) },
                    label = { Text(if (showWallpaper) "Wallpaper: On" else "Wallpaper: Off") },
                )
                OutlinedButton(onClick = onOpenWallpaperPicker) {
                    Text("Choose Wallpaper")
                }
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
    onFolderClick: ((LayoutSlotItem) -> Unit)? = null,
) {
    val p = LocalStudioPalette.current
    val rows = config.preferences.gridRows
    val cols = config.preferences.gridColumns
    val alignment = page.header.alignmentOverride ?: config.globalTheme.alignment
    val imageBitmap: ImageBitmap? = remember(wallpaperBitmap, showWallpaper) {
        if (showWallpaper && wallpaperBitmap != null) wallpaperBitmap.asImageBitmap() else null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(p.wallpaperPreviewBg)
            .border(1.dp, p.wallpaperPreviewBorder, RoundedCornerShape(18.dp)),
    ) {
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
                                Color.Black.copy(alpha = 0.10f),
                                Color.Black.copy(alpha = 0.34f),
                            ),
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (r in 0 until rows) {
                if (page.header.enabled && !page.isWallpaperPage && page.header.row == r) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .background(
                                Color.Black.copy(alpha = if (imageBitmap != null) 0.14f else 0f),
                                RoundedCornerShape(8.dp),
                            )
                            .border(
                                1.dp,
                                if (imageBitmap != null) Color.White.copy(alpha = 0.18f) else p.secondaryAccent.copy(alpha = 0.25f),
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
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Light,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    val rowItems = page.items.filter { it.row == r }
                    if (rowItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(if (page.isWallpaperPage && r == 3) 44.dp else 18.dp)) {
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
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
