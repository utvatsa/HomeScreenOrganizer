package com.homestudio.local.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.homestudio.local.data.HomeStudioStore
import com.homestudio.local.data.WidgetInstanceConfig

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val store = HomeStudioStore(this)
        val fullConfig = store.load()
        val existing = fullConfig.widgets[appWidgetId]
        val initialTitle = existing?.title ?: fullConfig.categories.firstOrNull()?.title ?: "Category"

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val context = LocalContext.current
                var selectedCatId by remember { mutableStateOf(existing?.categoryId) }
                var title by remember { mutableStateOf(initialTitle) }
                var subtitle by remember { mutableStateOf(existing?.subtitle ?: "") }
                var inheritTheme by remember { mutableStateOf(existing?.inheritTheme ?: true) }
                var fontSizeSp by remember { mutableFloatStateOf(existing?.fontSizeSp ?: fullConfig.globalTheme.fontSizeSp) }
                var opacity by remember { mutableFloatStateOf(existing?.opacity ?: fullConfig.globalTheme.opacity) }
                var fontWeight by remember { mutableIntStateOf(existing?.fontWeight ?: fullConfig.globalTheme.fontWeight) }
                var alignment by remember { mutableStateOf(existing?.alignment ?: fullConfig.globalTheme.alignment) }
                var showSeparator by remember { mutableStateOf(existing?.showSeparator ?: fullConfig.globalTheme.showSeparator) }

                val previewCfg = WidgetInstanceConfig(
                    appWidgetId = appWidgetId,
                    categoryId = selectedCatId,
                    title = title.ifBlank { "Category" },
                    subtitle = subtitle.takeIf { it.isNotBlank() },
                    inheritTheme = inheritTheme,
                    fontSizeSp = if (inheritTheme) null else fontSizeSp,
                    opacity = if (inheritTheme) null else opacity,
                    fontWeight = if (inheritTheme) null else fontWeight,
                    alignment = if (inheritTheme) null else alignment,
                    showSeparator = if (inheritTheme) null else showSeparator,
                )

                val previewBitmap = remember(previewCfg, fullConfig.globalTheme) {
                    HeaderRenderer.render(context, previewCfg, fullConfig.globalTheme, 340, 72)
                }

                Scaffold { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Configure Category Header (#$appWidgetId)", style = MaterialTheme.typography.titleLarge)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                                .background(Color(0xFF0E0E10), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF2C2C32), RoundedCornerShape(12.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = title,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        Text("Quick Select Category", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            fullConfig.categories.forEach { cat ->
                                FilterChip(
                                    selected = selectedCatId == cat.id,
                                    onClick = {
                                        selectedCatId = cat.id
                                        title = cat.title
                                        subtitle = cat.subtitle ?: ""
                                    },
                                    label = { Text(cat.title) },
                                )
                            }
                        }

                        OutlinedTextField(
                            value = title,
                            onValueChange = {
                                title = it
                                selectedCatId = null
                            },
                            label = { Text("Header Text") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )

                        OutlinedTextField(
                            value = subtitle,
                            onValueChange = { subtitle = it },
                            label = { Text("Optional Subtitle") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { inheritTheme = !inheritTheme },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Inherit Global Theme")
                            Switch(checked = inheritTheme, onCheckedChange = { inheritTheme = it })
                        }

                        if (!inheritTheme) {
                            Text("Font Size: ${fontSizeSp.toInt()} sp")
                            @Suppress("DEPRECATION")
                            Slider(value = fontSizeSp, onValueChange = { fontSizeSp = it }, valueRange = 16f..36f)

                            Text("Opacity: ${(opacity * 100).toInt()}%")
                            @Suppress("DEPRECATION")
                            Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.3f..1.0f)

                            Text("Font Weight: $fontWeight")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(200, 300, 400, 500, 700).forEach { w ->
                                    FilterChip(
                                        selected = fontWeight == w,
                                        onClick = { fontWeight = w },
                                        label = { Text("$w") },
                                    )
                                }
                            }

                            Text("Alignment")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("start", "center", "end").forEach { align ->
                                    FilterChip(
                                        selected = alignment == align,
                                        onClick = { alignment = align },
                                        label = { Text(align) },
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Show Subtle Separator")
                                Switch(checked = showSeparator, onCheckedChange = { showSeparator = it })
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                store.putWidgetConfig(previewCfg)
                                val mgr = AppWidgetManager.getInstance(context)
                                CategoryHeaderWidgetProvider.updateWidget(context, mgr, appWidgetId)
                                val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                setResult(RESULT_OK, resultValue)
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Save Header Widget")
                        }
                    }
                }
            }
        }
    }
}
