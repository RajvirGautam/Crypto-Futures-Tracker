package com.rajvir.FuturesTracker

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.RemoteViews
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WidgetUpdater {

    // Per-symbol cache so different widgets don't share stale data
    private data class SymbolCache(
        var sparkline: Bitmap? = null,
        var lastKlineFetchTime: Long = 0L,
        var openPrice1hAgo: Float = 0f
    )

    private val symbolCacheMap = mutableMapOf<String, SymbolCache>()

    suspend fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, CryptoWidgetProvider::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (widgetIds.isEmpty()) return

        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

        for (widgetId in widgetIds) {
            val symbol = prefs.getString("widget_${widgetId}_symbol", "BTCUSDT") ?: "BTCUSDT"

            try {
                // Always fetch live price for this widget's symbol
                val currentPrice = ApiClient.api.getFuturesPrice(symbol).markPrice.toFloat()

                val cache = symbolCacheMap.getOrPut(symbol) { SymbolCache() }
                val currentTime = System.currentTimeMillis()

                // Refresh kline chart at most once per 60s per symbol
                if (currentTime - cache.lastKlineFetchTime > 60_000 || cache.sparkline == null) {
                    try {
                        val klines = ApiClient.api.getKlines(symbol, "1m", 60)
                        cache.openPrice1hAgo = if (klines.isNotEmpty()) klines.first()[1].toFloat() else currentPrice
                        val sparklineData = if (klines.isNotEmpty()) klines.map { it[4].toFloat() } else listOf(currentPrice)
                        val changeAbs = currentPrice - cache.openPrice1hAgo
                        cache.sparkline = SparklineRenderer.render(sparklineData, changeAbs >= 0)
                        cache.lastKlineFetchTime = currentTime
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val changeAbs = currentPrice - cache.openPrice1hAgo
                val changePct = if (cache.openPrice1hAgo != 0f) (changeAbs / cache.openPrice1hAgo) * 100 else 0f
                val isPositive = changeAbs >= 0

                val decimalFormat = DecimalFormat("#,##0.00")
                val priceStr = decimalFormat.format(currentPrice)
                val sign = if (isPositive) "+" else ""
                val changeAbsStr = decimalFormat.format(changeAbs)
                val changePctStr = DecimalFormat("0.00").format(changePct)

                val changeText = "USDT $sign$changeAbsStr • $sign$changePctStr%"
                val changeColor = if (isPositive) Color.parseColor("#10B981") else Color.parseColor("#EF4444")

                val timeStr = SimpleDateFormat("H:mm:ss", Locale.getDefault()).format(Date())

                // Format display name: "BTCUSDT" → symbol="BTC", pair="Bitcoin / Tether"
                val baseAsset = symbol.removeSuffix("USDT").removeSuffix("BUSD").removeSuffix("BTC")
                val quoteAsset = when {
                    symbol.endsWith("USDT") -> "Tether"
                    symbol.endsWith("BUSD") -> "BUSD"
                    symbol.endsWith("BTC") -> "Bitcoin"
                    else -> "USDT"
                }
                val coinName = "$baseAsset / $quoteAsset"

                val views = RemoteViews(context.packageName, R.layout.widget_crypto)
                views.setTextViewText(R.id.tvSymbol, baseAsset)
                views.setTextViewText(R.id.tvName, coinName)
                views.setTextViewText(R.id.tvPrice, priceStr)
                views.setTextViewText(R.id.tvChange, changeText)
                views.setTextColor(R.id.tvChange, changeColor)
                views.setTextViewText(R.id.tvTime, timeStr)
                cache.sparkline?.let { views.setImageViewBitmap(R.id.imgGraph, it) }

                appWidgetManager.updateAppWidget(widgetId, views)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
