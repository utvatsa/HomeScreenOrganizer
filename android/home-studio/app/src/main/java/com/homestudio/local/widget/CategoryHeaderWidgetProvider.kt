package com.homestudio.local.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews
import com.homestudio.local.R
import com.homestudio.local.data.HomeStudioStore

class CategoryHeaderWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        HomeStudioStore(context).removeWidgets(appWidgetIds)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        HomeStudioStore(context).remapWidgetIds(oldWidgetIds, newWidgetIds)
    }

    companion object {
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val store = HomeStudioStore(context)
            val fullCfg = store.load()
            val widgetCfg = fullCfg.widgets[appWidgetId]
                ?: fullCfg.categories.firstOrNull()?.let { first ->
                    com.homestudio.local.data.WidgetInstanceConfig(
                        appWidgetId = appWidgetId,
                        categoryId = first.id,
                        title = first.title,
                        subtitle = first.subtitle,
                    )
                }
                ?: com.homestudio.local.data.WidgetInstanceConfig(appWidgetId = appWidgetId, title = "Category")

            val opts = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val widthDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320).coerceAtLeast(160)
            val heightDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 64).coerceAtLeast(48)

            val bitmap = HeaderRenderer.render(
                context = context,
                widgetConfig = widgetCfg,
                globalTheme = fullCfg.globalTheme,
                widthDp = widthDp,
                heightDp = heightDp,
            )

            val views = RemoteViews(context.packageName, R.layout.widget_category_header)
            views.setImageViewBitmap(R.id.header_image, bitmap)
            val desc = buildString {
                append(widgetCfg.title)
                if (!widgetCfg.subtitle.isNullOrBlank()) {
                    append(" - ")
                    append(widgetCfg.subtitle)
                }
            }
            views.setContentDescription(R.id.header_image, desc)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, CategoryHeaderWidgetProvider::class.java)
            )
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }
    }
}
