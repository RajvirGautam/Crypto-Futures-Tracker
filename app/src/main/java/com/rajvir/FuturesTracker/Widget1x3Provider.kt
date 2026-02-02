package com.rajvir.FuturesTracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class Widget1x3Provider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        for (widgetId in appWidgetIds) {
            val symbol = prefs.getString("widget_${widgetId}_symbol", "BTCUSDT") ?: "BTCUSDT"
            val views = RemoteViews(context.packageName, R.layout.widget_crypto)
            val baseAsset = symbol.removeSuffix("USDT").removeSuffix("BUSD").removeSuffix("BTC")
            views.setTextViewText(R.id.tvSymbol, baseAsset)
            views.setTextViewText(R.id.tvPrice, "Loading...")
            appWidgetManager.updateAppWidget(widgetId, views)
            val refreshIntent = Intent(context, Widget1x3Provider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
            }
            val pi = PendingIntent.getBroadcast(context, widgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.rootLayout, pi)
            appWidgetManager.updateAppWidget(widgetId, views)
            val serviceIntent = Intent(context, PriceService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                context.startForegroundService(serviceIntent)
            else context.startService(serviceIntent)
        }
    }
}
