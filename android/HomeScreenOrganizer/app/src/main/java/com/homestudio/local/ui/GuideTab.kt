package com.homestudio.local.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class GuideStep(
    val number: String,
    val title: String,
    val body: String,
)

private val ANDROID_SUBAPPS = listOf(
    "Samsung One UI Home (com.sec.android.app.launcher) — Required active launcher for widgets, folders & multi-page setup.",
    "Samsung Good Lock (com.samsung.android.goodlock, Free on Galaxy Store) — Required to install and open Home Up.",
    "Samsung Home Up (com.samsung.android.app.homestar, Free Good Lock module) — Recommended to create an Immediate Backup before reorganizing.",
    "Samsung Smart Switch (com.sec.android.easyMover, Free) — Optional safety net for full phone & launcher backup.",
    "KWGT Kustom Widget Maker (org.kustom.widget, Free edition) — Strictly optional; built-in Category Header widgets work standalone.",
)

private val GUIDE_STEPS = listOf(
    GuideStep(
        number = "Step 1",
        title = "Choose a First-Use Collection Preset or Start Custom",
        body = "In the Studio tab, pick Brief (3 layouts), Balanced (6 layouts), or Detailed (8-12+ layouts covering all installed apps). Only apps already installed on your phone appear in suggestions.",
    ),
    GuideStep(
        number = "Step 2",
        title = "Customize Max Apps Per Page & One-Hand Grid",
        body = "Set a global Max Apps Per Page limit (default 8 for rows 4-5 one-hand reach) or override it per page. Realign packs shortcuts cleanly upward from the bottom rows so top headers stay clear.",
    ),
    GuideStep(
        number = "Step 3",
        title = "Review Suggested Suites, Folders & Custom Layouts",
        body = "Tap + Suggested (e.g. Payments Suite) to check/uncheck any app or folder, rename folders, check/uncheck apps inside folders, or break a folder apart into solo shortcuts. Tap + Custom to build any layout from scratch.",
    ),
    GuideStep(
        number = "Step 4",
        title = "Teach Custom Apps in Catalog",
        body = "Open the Catalog tab to inspect all installed apps detected on-device. Tap Tag on any custom, work, or niche app to assign categories and a suite folder so presets automatically include it.",
    ),
    GuideStep(
        number = "Step 5",
        title = "Edit, Reposition & Pin Transparent Category Headers",
        body = "Tap Edit Header on any page card to rename, toggle, or reposition the Category Header (Row 0-2 & alignment), or use Settings to switch Light/Dark mode and style all inheriting headers live. Pin headers via Widgets & Tools.",
    ),
    GuideStep(
        number = "Step 6",
        title = "Reorder Pages, Back Up & Apply Automation",
        body = "Use Up/Down arrows and Set Home on page cards to rearrange or mark your Default Home page (such as keeping Most Used to the left of Daily Essentials). Export Layout JSON cleanly to sync with the host CLI.",
    ),
)

@Composable
fun GuideTab() {
    val p = LocalStudioPalette.current
    var expandedIdx by remember { mutableStateOf(0) }

    Text("How to Use HomeScreenOrganizer", style = MaterialTheme.typography.titleMedium, color = p.textPrimary)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = p.cardTint),
        border = BorderStroke(1.dp, p.border),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Android-Only Notes & Sub-Apps Needed",
                style = MaterialTheme.typography.titleSmall,
                color = p.secondaryAccent,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "HomeScreenOrganizer is built exclusively for Android (Android 12+ / API 31+, tailored for Samsung One UI Home). Open the Setup tab to automatically check whether these companion sub-apps are installed:",
                style = MaterialTheme.typography.bodySmall,
                color = p.textSecondary,
            )
            ANDROID_SUBAPPS.forEach { line ->
                Text(
                    text = "• $line",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textPrimary,
                )
            }
        }
    }

    GUIDE_STEPS.forEachIndexed { idx, step ->
        val isExpanded = expandedIdx == idx
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 380f))
                .clickable { expandedIdx = if (isExpanded) -1 else idx },
            colors = CardDefaults.cardColors(containerColor = p.card),
            border = BorderStroke(1.dp, if (isExpanded) p.accent.copy(alpha = 0.5f) else p.border),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = step.number,
                        style = MaterialTheme.typography.labelMedium,
                        color = p.accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (isExpanded) "Hide" else "Read",
                        style = MaterialTheme.typography.labelSmall,
                        color = p.textMuted,
                    )
                }
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = p.textPrimary,
                )
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Text(
                        text = step.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
