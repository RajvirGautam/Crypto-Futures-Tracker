package com.rajvir.FuturesTracker

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Color
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WidgetUpdateWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val widgetId = inputData.getInt("widget_id", -1)
        if (widgetId == -1) return Result.failure()

        val views = RemoteViews(applicationContext.packageName, R.layout.widget_crypto)

        try {
            val symbol = "BTCUSDT"
            
            val currentPriceData = ApiClient.api.getFuturesPrice(symbol)
            val currentPrice = currentPriceData.markPrice.toFloat()

            // Fetch 60 minutes of 1m klines
            val klines = ApiClient.api.getKlines(symbol, "1m", 60)
            
            // The open price of the 60m ago kline is index 1
            // Sometimes if list is empty or small, fallback to current price
            val openPrice1hAgo = if (klines.isNotEmpty()) klines.first()[1].toFloat() else currentPrice
            val changeAbs = currentPrice - openPrice1hAgo
            val changePct = if (openPrice1hAgo != 0f) (changeAbs / openPrice1hAgo) * 100 else 0f
            
            // Extract close prices for sparkline
            val sparklineData = if (klines.isNotEmpty()) klines.map { it[4].toFloat() } else listOf(currentPrice)
            val isPositive = changeAbs >= 0

            val spark = SparklineRenderer.render(
                sparklineData,
                isPositive
            )

            val decimalFormat = java.text.DecimalFormat("#,##0.00")
            val priceStr = decimalFormat.format(currentPrice)
            val sign = if (isPositive) "+" else ""
            val changeAbsStr = decimalFormat.format(changeAbs)
            val changePctStr = java.text.DecimalFormat("0.00").format(changePct)
            
            val changeText = "USDT $sign$changeAbsStr • $sign$changePctStr%"
            val changeColor = if (isPositive) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
            
            // Formate time
            val timeFormat = java.text.SimpleDateFormat("H:mm", java.util.Locale.getDefault())
            val timeStr = timeFormat.format(java.util.Date())

            views.setTextViewText(R.id.tvSymbol, "BTC")
            views.setTextViewText(R.id.tvName, "Bitcoin / Tether")
            views.setTextViewText(R.id.tvPrice, priceStr)
            views.setTextViewText(R.id.tvChange, changeText)
            views.setTextColor(R.id.tvChange, changeColor)
            views.setTextViewText(R.id.tvTime, timeStr)
            views.setImageViewBitmap(R.id.imgGraph, spark)

        } catch (e: Exception) {
            e.printStackTrace()
            // In case of network error, just refresh the time maybe or show error state
            val timeFormat = java.text.SimpleDateFormat("H:mm", java.util.Locale.getDefault())
            val timeStr = timeFormat.format(java.util.Date())
            views.setTextViewText(R.id.tvTime, "$timeStr (Error)")
        }

        AppWidgetManager.getInstance(applicationContext)
            .updateAppWidget(widgetId, views)

        return Result.success()
    }
}