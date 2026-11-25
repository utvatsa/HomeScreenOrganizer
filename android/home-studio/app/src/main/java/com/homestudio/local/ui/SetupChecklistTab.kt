package com.homestudio.local.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homestudio.local.diagnostics.CheckStatus
import com.homestudio.local.diagnostics.ChecklistAction
import com.homestudio.local.diagnostics.ReadinessReport

@Composable
fun SetupChecklistTab(
    report: ReadinessReport,
    onAction: (ChecklistAction) -> Unit,
    onOpenGuide: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (report.allRequiredReady) Color(0xFF14271F) else Color(0xFF282015),
        ),
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
                    text = if (report.allRequiredReady) "Device Ready" else "Setup Checks Pending",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                StatusPill(
                    text = "${report.readyRequiredCount}/${report.requiredItems.size} Core",
                    status = if (report.allRequiredReady) CheckStatus.READY else CheckStatus.ACTION_NEEDED,
                )
            }
            Text(
                text = "Active widgets: ${report.headerWidgetCount} Category Headers, ${report.spacerWidgetCount} Wallpaper Spacers.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFBFC6D4),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAction(ChecklistAction.REFRESH) }) {
                    Text("Re-check")
                }
                OutlinedButton(onClick = onOpenGuide) {
                    Text("Read Guide")
                }
            }
        }
    }

    Text("Required & Companion App Checklist", style = MaterialTheme.typography.titleMedium)

    report.items.forEach { item ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    StatusPill(
                        text = when (item.status) {
                            CheckStatus.READY -> "Installed"
                            CheckStatus.OPTIONAL_MISSING -> "Optional"
                            CheckStatus.ACTION_NEEDED -> "Action Needed"
                        },
                        status = item.status,
                    )
                }
                Text(
                    text = item.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA9B1C2),
                )
                if (item.actionLabel != null && item.action != ChecklistAction.NONE) {
                    OutlinedButton(onClick = { onAction(item.action) }) {
                        Text(item.actionLabel)
                    }
                }
            }
        }
    }

    Text("System Most Used Apps (Top 8)", style = MaterialTheme.typography.titleMedium)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!report.usageAccessGranted) {
                Text(
                    text = "Usage Access is optional. Grant it below to detect your top 8 most-used apps directly inside the app, or let the host automation pull `dumpsys usagestats` over ADB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA9B1C2),
                )
                Button(onClick = { onAction(ChecklistAction.OPEN_USAGE_ACCESS) }) {
                    Text("Grant Usage Access")
                }
            } else if (report.topUsedApps.isEmpty()) {
                Text(
                    text = "Usage Access is enabled, but no recent foreground usage records were returned yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA9B1C2),
                )
            } else {
                report.topUsedApps.forEachIndexed { idx, app ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${idx + 1}. ${app.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "${app.totalForegroundMinutes}m",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF91B4FA),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, status: CheckStatus) {
    val (bg, fg) = when (status) {
        CheckStatus.READY -> Color(0xFF1A3D2E) to Color(0xFF8BE0B0)
        CheckStatus.OPTIONAL_MISSING -> Color(0xFF272D3E) to Color(0xFFB9C4DC)
        CheckStatus.ACTION_NEEDED -> Color(0xFF4C2A1A) to Color(0xFFFFC78E)
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}
