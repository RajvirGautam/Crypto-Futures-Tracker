package com.rajvir.FuturesTracker

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

class CryptoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(
                context.packageName,
                R.layout.widget_crypto
            )

            // TEMP STATIC DATA (to prove widget loads)
            views.setTextViewText(
                R.id.tvWidgetTitle,
                "BTCUSDT"
            )
            views.setTextViewText(
                R.id.tvWidgetPrice,
                "$67,123"
            )

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}