package com.homestudio.local.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homestudio.local.diagnostics.CheckStatus
import com.homestudio.local.diagnostics.ChecklistAction
import com.homestudio.local.diagnostics.ReadinessReport

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupChecklistTab(
    report: ReadinessReport,
    isFirstRunGate: Boolean = false,
    onAction: (ChecklistAction) -> Unit,
    onOpenGuide: () -> Unit,
    onContinueToStudio: (() -> Unit)? = null,
) {
    val p = LocalStudioPalette.current
    val ratio = if (report.requiredItems.isEmpty()) 1f else {
        report.readyRequiredCount.toFloat() / report.requiredItems.size.toFloat()
    }
    val animProgress by animateFloatAsState(
        targetValue = ratio.coerceIn(0f, 1f),
        animationSpec = tween(420),
        label = "readyProgress",
    )

    if (isFirstRunGate && !report.allRequiredReady) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = p.warningSurface),
            border = BorderStroke(1.dp, p.warningText.copy(alpha = 0.5f)),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Setup Required Before First Use",
                    style = MaterialTheme.typography.titleSmall,
                    color = p.warningText,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Some required Android companion items below are missing or inactive. Resolve them for full One UI Home compatibility, or review the Guide.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textPrimary,
                )
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (report.allRequiredReady) p.positiveSurface else p.warningSurface,
        ),
        border = BorderStroke(1.dp, if (report.allRequiredReady) p.positive.copy(alpha = 0.45f) else p.warningText.copy(alpha = 0.45f)),
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
                    color = p.textPrimary,
                )
                StatusPill(
                    text = "${report.readyRequiredCount}/${report.requiredItems.size} Core",
                    status = if (report.allRequiredReady) CheckStatus.READY else CheckStatus.ACTION_NEEDED,
                )
            }
            LinearProgressIndicator(
                progress = { animProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = if (report.allRequiredReady) p.positive else p.warningText,
                trackColor = p.border,
            )
            Text(
                text = "Active widgets: ${report.headerWidgetCount} Category Headers, ${report.spacerWidgetCount} Wallpaper Spacers.",
                style = MaterialTheme.typography.bodySmall,
                color = p.textSecondary,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(onClick = { onAction(ChecklistAction.REFRESH) }) {
                    Text("Re-check")
                }
                OutlinedButton(onClick = onOpenGuide) {
                    Text("Read Guide")
                }
                if (onContinueToStudio != null) {
                    OutlinedButton(onClick = onContinueToStudio) {
                        Text(if (report.allRequiredReady) "Open Studio" else "Skip to Studio")
                    }
                }
            }
        }
    }

    Text("Required & Companion App Checklist", style = MaterialTheme.typography.titleMedium, color = p.textPrimary)

    report.items.forEach { item ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = p.card),
            border = BorderStroke(1.dp, p.border),
        ) {
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
                        color = p.textPrimary,
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
                    color = p.textSecondary,
                )
                if (item.actionLabel != null && item.action != ChecklistAction.NONE) {
                    OutlinedButton(onClick = { onAction(item.action) }) {
                        Text(item.actionLabel)
                    }
                }
            }
        }
    }

    Text("System Most Used Apps (Top 8)", style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = p.card),
        border = BorderStroke(1.dp, p.border),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!report.usageAccessGranted) {
                Text(
                    text = "Usage Access is optional. Grant it below to rank your true top 8 foreground apps inside Most Used presets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary,
                )
                Button(onClick = { onAction(ChecklistAction.OPEN_USAGE_ACCESS) }) {
                    Text("Grant Usage Access")
                }
            } else if (report.topUsedApps.isEmpty()) {
                Text(
                    text = "Usage Access is enabled, but no recent foreground usage records were returned yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary,
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
                            color = p.textPrimary,
                        )
                        Text(
                            text = "${app.totalForegroundMinutes}m",
                            style = MaterialTheme.typography.bodySmall,
                            color = p.secondaryAccent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, status: CheckStatus) {
    val p = LocalStudioPalette.current
    val (bg, fg) = when (status) {
        CheckStatus.READY -> p.positiveSurface to p.positiveText
        CheckStatus.OPTIONAL_MISSING -> p.cardElevated to p.textSecondary
        CheckStatus.ACTION_NEEDED -> p.warningSurface to p.warningText
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.SemiBold)
    }
}
