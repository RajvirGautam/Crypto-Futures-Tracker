package com.rajvir.FuturesTracker

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import kotlinx.coroutines.*

class PriceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val symbol = WidgetPrefs.getSymbol(context)
                    val price = ApiClient.api.getFuturesPrice(symbol).markPrice

                    val views = RemoteViews(
                        context.packageName,
                        R.layout.widget_price
                    )

                    views.setTextViewText(R.id.widgetSymbol, symbol)
                    views.setTextViewText(R.id.widgetPrice, price)

                    appWidgetManager.updateAppWidget(widgetId, views)
                } catch (_: Exception) {}
            }
        }
    }
}