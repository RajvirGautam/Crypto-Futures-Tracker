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

            val baseAsset = when {
                symbol.endsWith("USDT") -> symbol.removeSuffix("USDT")
                symbol.endsWith("BUSD") -> symbol.removeSuffix("BUSD")
                symbol.endsWith("BTC")  -> symbol.removeSuffix("BTC")
                else -> symbol
            }
            val quoteAsset = when {
                symbol.endsWith("USDT") -> "Tether"
                symbol.endsWith("BUSD") -> "BUSD"
                symbol.endsWith("BTC") -> "Bitcoin"
                else -> "USDT"
            }

            views.setTextViewText(R.id.tvSymbol, baseAsset)
            views.setTextViewText(R.id.tvName, "$baseAsset / $quoteAsset")
            views.setTextViewText(R.id.tvPrice, "Loading...")

            appWidgetManager.updateAppWidget(widgetId, views)

            appWidgetManager.updateAppWidget(widgetId, views)

            val refreshIntent = Intent(context, CryptoWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
            }

            val pi = PendingIntent.getBroadcast(
                context, widgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.rootLayout, pi)
            appWidgetManager.updateAppWidget(widgetId, views)
            
            // 🔹 Make sure PriceService is running to feed 1-second updates!
            // Wrapped in try/catch so a background-restriction IllegalStateException
            // doesn't crash onUpdate and trigger "Can't load widget".
            try {
                val serviceIntent = Intent(context, PriceService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (_: Exception) { }
        }
    }
}