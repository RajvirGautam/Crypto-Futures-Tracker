package com.rajvir.FuturesTracker

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.text.DecimalFormat

class WidgetUpdateWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        try {
            val widgetId = inputData.getInt("widget_id", -1)
            if (widgetId == -1) return Result.failure()

            val prefs = ctx.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val symbol = prefs.getString("widget_${widgetId}_symbol", "BTCUSDT")!!

            val price = ApiClient.api
                .getFuturesPrice(symbol)
                .markPrice
                .toDouble()

            val df = DecimalFormat("#,##0.0000")

            val views = RemoteViews(ctx.packageName, R.layout.widget_crypto)
            views.setTextViewText(R.id.tvWidgetTitle, symbol)
            views.setTextViewText(R.id.tvWidgetPrice, df.format(price))

            AppWidgetManager.getInstance(ctx)
                .updateAppWidget(widgetId, views)

            return Result.success()

        } catch (e: Exception) {
            return Result.retry()
        }
    }
}