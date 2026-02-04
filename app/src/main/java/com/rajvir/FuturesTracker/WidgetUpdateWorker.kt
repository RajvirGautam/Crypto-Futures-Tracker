package com.rajvir.FuturesTracker

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Color
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val widgetId = inputData.getInt("widget_id", -1)
        if (widgetId == -1) return Result.failure()

        val prefs = applicationContext.getSharedPreferences(
            "widget_prefs",
            Context.MODE_PRIVATE
        )

        val symbol = prefs.getString(
            "widget_${widgetId}_symbol",
            "BTCUSDT"
        ) ?: "BTCUSDT"

        return try {
            val priceData = ApiClient.api.getFuturesPrice(symbol)
            val ticker = ApiClient.api.get24hTicker(symbol)

            val price = priceData.markPrice.toDouble()
            val change = ticker.priceChangePercent.toDouble()

            val views = RemoteViews(
                applicationContext.packageName,
                R.layout.widget_crypto
            )

            views.setTextViewText(
                R.id.tvWidgetTitle,
                symbol
            )

            views.setTextViewText(
                R.id.tvWidgetPrice,
                String.format("$%,.4f", price)
            )

            val color = if (change >= 0) {
                Color.parseColor("#0ECB81") // green
            } else {
                Color.parseColor("#F6465D") // red
            }

            views.setTextColor(R.id.tvWidgetPrice, color)

            val manager = AppWidgetManager.getInstance(applicationContext)
            manager.updateAppWidget(widgetId, views)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}