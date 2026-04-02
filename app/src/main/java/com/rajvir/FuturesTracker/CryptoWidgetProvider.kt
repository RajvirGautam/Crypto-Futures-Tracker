package com.rajvir.FuturesTracker

import android.app.PendingIntent
import android.content.Intent
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
        // Delegate to WidgetUpdater which handles multi-coin widgets with graphs,
        // decimal formatting, border opacity, and graph timeframe selection
        for (widgetId in appWidgetIds) {
            WidgetUpdater.applyLoadingState(context, appWidgetManager, widgetId)
        }

        // Ensure PriceService is running for live tick updates
        try {
            val serviceIntent = Intent(context, PriceService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                context.startForegroundService(serviceIntent)
            else
                context.startService(serviceIntent)
        } catch (_: Exception) { }
    }
}