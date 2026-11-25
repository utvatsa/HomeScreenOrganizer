package com.homestudio.local.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.homestudio.local.BuildConfig
import com.homestudio.local.R

private const val GITHUB_REPO_URL = "https://github.com/utvatsa/HomeScreenOrganizer"

private val ABOUT_FEATURES = listOf(
    "✨ One UI 9 Expressive Floating Navigation & Hamburger Drawer",
    "🧭 Interactive 4-Step Guided Studio with ✓ Progress & Expand/Collapse All",
    "🗂️ Saved JSON Drafts & Chronological Applied Layouts Snapshot Archive",
    "📱 Exact Live Current Layout Viewer with Folder Drill-Down",
    "🖼️ Unlocked Home Wallpaper Selector (Lock Screen Untouched)",
    "🔀 Vertical Stacked & Horizontal Swipeable Layout Previewer",
    "🧠 Offline App Catalog & Suite Suggestions (Installed Apps Only)",
    "📁 Folder Rename, Item Toggle, Breakdown & Custom Builder",
    "🌓 Responsive Curated Light, Dark & System Themes + Synced Launcher Icon",
    "🛡️ 100% Free ($0), Offline, Zero Tracking & Production-Safe",
)

@Composable
fun AboutTab(iconResId: Int = R.mipmap.ic_launcher_warm_paper) {
    val context = LocalContext.current
    val p = LocalStudioPalette.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = p.cardTint),
        border = BorderStroke(1.dp, p.border),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppBrandIconBadge(size = 54.dp, iconResId = iconResId)
                Column {
                    Text(
                        text = "HomeScreenOrganizer",
                        style = MaterialTheme.typography.titleLarge,
                        color = p.textPrimary,
                    )
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • Android Only",
                        style = MaterialTheme.typography.bodySmall,
                        color = p.secondaryAccent,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Text(
                text = "Autonomous, declarative Android home screen studio & Samsung One UI Home companion.",
                style = MaterialTheme.typography.bodySmall,
                color = p.textSecondary,
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = p.positiveSurface,
                border = BorderStroke(1.dp, p.positive.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Made with ✨🛠️📱 by utvatsa",
                    style = MaterialTheme.typography.titleSmall,
                    color = p.positiveText,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Key Features",
                style = MaterialTheme.typography.titleMedium,
                color = p.textPrimary,
            )
            ABOUT_FEATURES.forEach { feat ->
                Text(
                    text = feat,
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary,
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
            Text(
                text = "Open-Source Repository",
                style = MaterialTheme.typography.titleSmall,
                color = p.textPrimary,
            )
            Text(
                text = GITHUB_REPO_URL,
                style = MaterialTheme.typography.bodySmall,
                color = p.secondaryAccent,
            )
            Button(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL)))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open GitHub Repository")
            }
        }
    }
}

@Composable
fun AppBrandIconBadge(
    size: Dp,
    iconResId: Int = R.mipmap.ic_launcher_warm_paper,
) {
    val context = LocalContext.current
    val p = LocalStudioPalette.current
    val bmp = remember(iconResId) {
        ContextCompat.getDrawable(context, iconResId)?.toBitmap(192, 192)?.asImageBitmap()
    }
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(size * 0.28f),
        color = p.cardElevated,
        border = BorderStroke(1.dp, p.border),
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = "HomeScreenOrganizer Icon",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text("✦", fontSize = (size.value * 0.42f).sp, color = p.accent)
            }
        }
    }
}
