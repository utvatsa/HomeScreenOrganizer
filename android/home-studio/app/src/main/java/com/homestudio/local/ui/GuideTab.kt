package com.homestudio.local.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class GuideStep(
    val number: String,
    val title: String,
    val body: String,
)

private val GUIDE_STEPS = listOf(
    GuideStep(
        number = "Step 1",
        title = "Verify Setup Checklist & Back Up with Home Up",
        body = "Open the Checklist tab first to verify Samsung One UI Home is active and Home Up is installed. Before reorganizing pages, open Good Lock -> Home Up -> Backup and Restore -> Create Immediate Backup so you can restore your prior home screen anytime.",
    ),
    GuideStep(
        number = "Step 2",
        title = "Keep Each Page Clean & One-Hand Reachable (<= 8 Icons)",
        body = "On a 4x6 One UI grid, keep rows 0-3 clear for your Category Header and wallpaper breathing room, and place up to 8 individual apps or suite folders in rows 4-5 for easy one-thumb access.",
    ),
    GuideStep(
        number = "Step 3",
        title = "Add & Configure Transparent Category Header Widgets",
        body = "Open the Tools tab -> tap Pin Category Header (or long-press the Home screen -> Widgets -> Home Studio -> Category Header). Drag it to row 0, then long-press the widget -> Settings to pick a category or customize its title.",
    ),
    GuideStep(
        number = "Step 4",
        title = "Style Headers Globally or Per-Page",
        body = "Use the Theme tab to adjust Font Size, Opacity, Weight, Alignment, and Separator across all inheriting headers at once. Toggle off 'Inherit Global Theme' inside an individual widget's Settings to style a single page independently.",
    ),
    GuideStep(
        number = "Step 5",
        title = "Left-of-Home 'Most Used' & Dedicated 'Wallpaper' Page",
        body = "Place a 'Most Used' page to the left of your Default Home page ('Daily Essentials') so one swipe left opens your top 8 daily apps, while keeping Home button navigation centered. Use Wallpaper Spacer on an extra end page only if your launcher trims empty pages.",
    ),
    GuideStep(
        number = "Step 6",
        title = "Host Automation & Interactive HTML Layout Editor",
        body = "Connect via ADB on your computer and run `.venv/bin/home-studio serve` (http://127.0.0.1:8765) or open `reports/layout-editor.html` to visually rearrange pages, folders, and themes, pull live `Most Used` stats (`home-studio most-used`), dry-run, and apply changes.",
    ),
    GuideStep(
        number = "Step 7",
        title = "Safety Guarantees & Backup Export",
        body = "Home Studio runs 100% offline with zero network, SMS, contacts, or storage permissions. It never uninstalls apps or clears launcher data. Use Tools -> Export Config JSON to save a portable backup of your category & header theme configuration.",
    ),
)

@Composable
fun GuideTab() {
    Text("How to Use Home Studio", style = MaterialTheme.typography.titleMedium)
    Text(
        text = "Everything needed to design, customize, and maintain a clean one-handed Samsung One UI home screen.",
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFFA9B1C2),
    )

    GUIDE_STEPS.forEach { step ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = step.number,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF91B4FA),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = step.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD0D5E2),
                )
            }
        }
    }
}
