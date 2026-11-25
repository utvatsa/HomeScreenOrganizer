package com.homestudio.local.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.homestudio.local.catalog.InstalledAppInfo
import com.homestudio.local.data.CategoryDefinition
import com.homestudio.local.data.CustomAppDefinition

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomCatalogTab(
    installedApps: List<InstalledAppInfo>,
    categories: List<CategoryDefinition>,
    onSaveCustomApp: (CustomAppDefinition, CategoryDefinition?) -> Unit,
) {
    val p = LocalStudioPalette.current
    var query by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("all") }
    var editingApp by remember { mutableStateOf<InstalledAppInfo?>(null) }

    Text("Installed App Catalog (${installedApps.size})", style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
    Text(
        text = "Only apps installed on your device are shown. Tag any custom or niche app with categories & a suite folder so suggestions and presets recognize it automatically.",
        style = MaterialTheme.typography.bodySmall,
        color = p.textSecondary,
    )

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Search installed apps or packages") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "all" to "All (${installedApps.size})",
            "user" to "User (${installedApps.count { !it.isSystemApp }})",
            "custom" to "Custom (${installedApps.count { it.isCustomTagged }})",
            "other" to "Other/Unmapped (${installedApps.count { "utilities" in it.categories }})",
        ).forEach { (id, label) ->
            FilterChip(
                selected = filterMode == id,
                onClick = { filterMode = id },
                label = { Text(label) },
            )
        }
    }

    val filtered = remember(installedApps, query, filterMode) {
        val q = query.trim().lowercase()
        installedApps.filter { app ->
            val modeOk = when (filterMode) {
                "user" -> !app.isSystemApp
                "custom" -> app.isCustomTagged
                "other" -> "utilities" in app.categories
                else -> true
            }
            val queryOk = q.isEmpty() ||
                app.displayName.lowercase().contains(q) ||
                app.packageName.lowercase().contains(q) ||
                app.categories.any { it.contains(q) }
            modeOk && queryOk
        }
    }

    filtered.take(80).forEach { app ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 400f)),
            colors = CardDefaults.cardColors(containerColor = p.card),
            border = BorderStroke(1.dp, p.border),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InstalledAppIcon(packageName = app.packageName, fallbackLabel = app.displayName, size = 38.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(app.displayName, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                        if (app.isCustomTagged) {
                            Box(
                                modifier = Modifier
                                    .background(p.positiveSurface, RoundedCornerShape(999.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            ) {
                                Text("Custom", style = MaterialTheme.typography.labelSmall, color = p.positiveText)
                            }
                        }
                    }
                    Text(
                        text = "Categories: ${app.categories.joinToString(", ")}" + (app.suiteFolder?.let { " | Folder: $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = p.secondaryAccent,
                    )
                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = p.textMuted)
                }
                OutlinedButton(onClick = { editingApp = app }) {
                    Text("Tag")
                }
            }
        }
    }

    if (filtered.size > 80) {
        Text(
            text = "Showing top 80 of ${filtered.size} matches — refine search above.",
            style = MaterialTheme.typography.bodySmall,
            color = p.textMuted,
        )
    }

    editingApp?.let { target ->
        TagCustomAppDialog(
            app = target,
            categories = categories,
            onDismiss = { editingApp = null },
            onSave = { def, newCat ->
                onSaveCustomApp(def, newCat)
                editingApp = null
            },
        )
    }
}

@Composable
private fun TagCustomAppDialog(
    app: InstalledAppInfo,
    categories: List<CategoryDefinition>,
    onDismiss: () -> Unit,
    onSave: (CustomAppDefinition, CategoryDefinition?) -> Unit,
) {
    val p = LocalStudioPalette.current
    var selectedCats by remember(app.packageName) { mutableStateOf(app.categories.toSet()) }
    var suiteFolder by remember(app.packageName) { mutableStateOf(app.suiteFolder ?: "") }
    var customCatTitle by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categorize ${app.displayName}") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = p.textMuted)
                OutlinedTextField(
                    value = suiteFolder,
                    onValueChange = { suiteFolder = it },
                    label = { Text("Suite / Folder Name (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = customCatTitle,
                    onValueChange = { customCatTitle = it },
                    label = { Text("Or add new category name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text("Select categories:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = p.textPrimary)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    categories.take(14).forEach { cat ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = cat.id in selectedCats,
                                onCheckedChange = { checked ->
                                    selectedCats = if (checked) selectedCats + cat.id else selectedCats - cat.id
                                },
                            )
                            Text(cat.title, style = MaterialTheme.typography.bodySmall, color = p.textPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val trimmedCustom = customCatTitle.trim()
                var addedCat: CategoryDefinition? = null
                val finalCats = selectedCats.toMutableSet()
                if (trimmedCustom.isNotEmpty()) {
                    val slug = trimmedCustom.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                    if (slug.isNotEmpty()) {
                        addedCat = CategoryDefinition(id = slug, title = trimmedCustom)
                        finalCats.add(slug)
                    }
                }
                onSave(
                    CustomAppDefinition(
                        packageName = app.packageName,
                        displayName = app.displayName,
                        categories = if (finalCats.isEmpty()) listOf("daily-essentials") else finalCats.toList(),
                        suiteFolder = suiteFolder.trim().ifBlank { null },
                    ),
                    addedCat,
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
