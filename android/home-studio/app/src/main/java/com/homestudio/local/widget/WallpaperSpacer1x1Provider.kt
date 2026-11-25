package com.homestudio.local.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.homestudio.local.R

class WallpaperSpacer1x1Provider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_wallpaper_placeholder)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
