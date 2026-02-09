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

    private data class SymbolCache(
        var sparkline: Bitmap? = null,
        var lastKlineFetchTime: Long = 0L,
        var openPrice1hAgo: Float = 0f
    )

    private val symbolCacheMap = mutableMapOf<String, SymbolCache>()

    // Fixed secondary coins shown in the wide multi-coin layout
    private const val COIN2 = "ETHUSDT"
    private const val COIN3 = "BNBUSDT"

    private val decFmt = DecimalFormat("#,##0.00")
    private val pctFmt = DecimalFormat("0.00")

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun displayLabel(symbol: String): String {
        val quote = when {
            symbol.endsWith("USDT") -> "USDT"
            symbol.endsWith("BUSD") -> "BUSD"
            symbol.endsWith("BTC")  -> "BTC"
            else                    -> ""
        }
        val base = if (quote.isNotEmpty()) symbol.removeSuffix(quote) else symbol
        val displayQuote = when (quote) {
            "USDT", "BUSD" -> "USD"
            "BTC"          -> "BTC"
            else           -> "USD"
        }
        return "$base/$displayQuote"
    }

    private fun changeColor(isPositive: Boolean) =
        if (isPositive) Color.parseColor("#10B981") else Color.parseColor("#EF4444")

    private suspend fun fetchAndCache(symbol: String): Triple<Float, Float, Float> {
        val currentPrice = ApiClient.api.getFuturesPrice(symbol).markPrice.toFloat()
        val cache = symbolCacheMap.getOrPut(symbol) { SymbolCache() }
        val now = System.currentTimeMillis()
        if (now - cache.lastKlineFetchTime > 60_000 || cache.sparkline == null) {
            try {
                val klines = ApiClient.api.getKlines(symbol, "1m", 60)
                cache.openPrice1hAgo = if (klines.isNotEmpty()) klines.first()[1].toFloat() else currentPrice
                val sparklineData = if (klines.isNotEmpty()) klines.map { it[4].toFloat() } else listOf(currentPrice)
                val chg = currentPrice - cache.openPrice1hAgo
                cache.sparkline = SparklineRenderer.render(sparklineData, chg >= 0)
                cache.lastKlineFetchTime = now
            } catch (_: Exception) { }
        }
        val changeAbs = currentPrice - cache.openPrice1hAgo
        val changePct = if (cache.openPrice1hAgo != 0f) (changeAbs / cache.openPrice1hAgo) * 100 else 0f
        return Triple(currentPrice, changeAbs, changePct)
    }

    private fun formatChange(pct: Float): String {
        val sign = if (pct >= 0) "+" else ""
        return "$sign${pctFmt.format(pct)}%"
    }

    // ── main update entry point ──────────────────────────────────────────────

    suspend fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        val providerClasses = listOf(
            CryptoWidgetProvider::class.java,
            Widget1x1Provider::class.java, Widget1x2Provider::class.java,
            Widget1x3Provider::class.java, Widget1x4Provider::class.java,
            Widget2x1Provider::class.java, Widget2x2Provider::class.java,
            Widget2x3Provider::class.java, Widget2x4Provider::class.java,
            Widget3x1Provider::class.java, Widget3x2Provider::class.java,
            Widget3x3Provider::class.java, Widget3x4Provider::class.java,
            Widget4x1Provider::class.java, Widget4x2Provider::class.java,
            Widget4x3Provider::class.java, Widget4x4Provider::class.java
        )
        val widgetIds = providerClasses
            .flatMap { appWidgetManager.getAppWidgetIds(ComponentName(context, it)).toList() }
            .toIntArray()
        if (widgetIds.isEmpty()) return

        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()).lowercase()

        // Pre-fetch BTC + ETH once for all wide widgets this tick
        var btcData: Triple<Float, Float, Float>? = null
        var ethData: Triple<Float, Float, Float>? = null
        val needsWide = widgetIds.any { BaseWidgetProvider.isWideWidget(appWidgetManager, it) }
        if (needsWide) {
            try { btcData = fetchAndCache("BTCUSDT") } catch (_: Exception) { }
            try { ethData = fetchAndCache(COIN2) } catch (_: Exception) { }
        }

        for (widgetId in widgetIds) {
            val symbol = prefs.getString("widget_${widgetId}_symbol", "BTCUSDT") ?: "BTCUSDT"
            val layoutId = BaseWidgetProvider.layoutForWidget(appWidgetManager, widgetId)
            val isWide = layoutId == R.layout.widget_size_wide

            try {
                val (currentPrice, changeAbs, changePct) = fetchAndCache(symbol)
                val isPositive = changeAbs >= 0
                val priceStr = decFmt.format(currentPrice)
                val changePctStr = formatChange(changePct)

                val views = RemoteViews(context.packageName, layoutId)

                // ── Column 1 / primary coin ──
                views.setTextViewText(R.id.tvSymbol, displayLabel(symbol))
                views.setTextViewText(R.id.tvPrice, priceStr)
                views.setTextViewText(R.id.tvChange, changePctStr)
                views.setTextColor(R.id.tvChange, changeColor(isPositive))
                views.setTextViewText(R.id.tvTime, timeStr)

                // Sparkline — medium layout only
                if (layoutId == R.layout.widget_size_medium) {
                    symbolCacheMap[symbol]?.sparkline?.let {
                        views.setImageViewBitmap(R.id.imgGraph, it)
                    }
                }

                // ── Wide multi-coin: override col1 → BTC, and fill col2 + col3 ──
                if (isWide) {
                    // Col 1: always BTC
                    val c1Symbol = "BTCUSDT"
                    val c1 = if (symbol == c1Symbol) Triple(currentPrice, changeAbs, changePct)
                              else (btcData ?: Triple(0f, 0f, 0f))
                    views.setTextViewText(R.id.tvSymbol, displayLabel(c1Symbol))
                    views.setTextViewText(R.id.tvPrice, decFmt.format(c1.first))
                    views.setTextViewText(R.id.tvChange, formatChange(c1.third))
                    views.setTextColor(R.id.tvChange, changeColor(c1.second >= 0))

                    // Col 2: always ETH
                    val c2 = ethData ?: Triple(0f, 0f, 0f)
                    views.setTextViewText(R.id.tvSymbol2, displayLabel(COIN2))
                    views.setTextViewText(R.id.tvPrice2, decFmt.format(c2.first))
                    views.setTextViewText(R.id.tvChange2, formatChange(c2.third))
                    views.setTextColor(R.id.tvChange2, changeColor(c2.second >= 0))

                    // Col 3: configured coin, or BNB if same as BTC/ETH
                    val c3Symbol = when (symbol) {
                        "BTCUSDT", "ETHUSDT" -> COIN3
                        else                  -> symbol
                    }
                    val c3 = if (c3Symbol == symbol) Triple(currentPrice, changeAbs, changePct)
                              else try { fetchAndCache(c3Symbol) } catch (_: Exception) { Triple(0f, 0f, 0f) }
                    views.setTextViewText(R.id.tvSymbol3, displayLabel(c3Symbol))
                    views.setTextViewText(R.id.tvPrice3, decFmt.format(c3.first))
                    views.setTextViewText(R.id.tvChange3, formatChange(c3.third))
                    views.setTextColor(R.id.tvChange3, changeColor(c3.second >= 0))
                }

                appWidgetManager.updateAppWidget(widgetId, views)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
