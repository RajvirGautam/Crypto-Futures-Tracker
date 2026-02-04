package com.rajvir.FuturesTracker

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import androidx.work.*

class CryptoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

        for (widgetId in appWidgetIds) {

            val symbol = prefs.getString(
                "widget_${widgetId}_symbol",
                "BTCUSDT"
            ) ?: "BTCUSDT"

            val views = RemoteViews(
                context.packageName,
                R.layout.widget_crypto
            )

            views.setTextViewText(R.id.tvWidgetTitle, symbol)
            views.setTextViewText(R.id.tvWidgetPrice, "Loading...")

            appWidgetManager.updateAppWidget(widgetId, views)

            // 🔹 ONE-TIME IMMEDIATE UPDATE
            val oneTimeWork = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setInputData(workDataOf("widget_id" to widgetId))
                .build()

            WorkManager.getInstance(context).enqueue(oneTimeWork)

            // 🔹 PERIODIC UPDATE (15 min – Android minimum)
            val periodicWork = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES
            )
                .setInputData(workDataOf("widget_id" to widgetId))
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "widget_update_$widgetId",
                ExistingPeriodicWorkPolicy.REPLACE,
                periodicWork
            )
        }
    }
}