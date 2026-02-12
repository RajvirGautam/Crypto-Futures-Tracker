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

    private val decFmt = DecimalFormat("#,##0.00")
    private val pctFmt = DecimalFormat("0.00")

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun baseAsset(symbol: String): String = when {
        symbol.endsWith("USDT") -> symbol.removeSuffix("USDT")
        symbol.endsWith("BUSD") -> symbol.removeSuffix("BUSD")
        symbol.endsWith("BTC")  -> symbol.removeSuffix("BTC")
        else -> symbol
    }

    private fun quoteAsset(symbol: String): String = when {
        symbol.endsWith("USDT") -> "USDT"
        symbol.endsWith("BUSD") -> "BUSD"
        symbol.endsWith("BTC")  -> "BTC"
        else -> "USDT"
    }

    private val coinNames = mapOf(
        "BTC" to "Bitcoin", "ETH" to "Ethereum", "BNB" to "BNB",
        "SOL" to "Solana", "XRP" to "XRP", "DOGE" to "Dogecoin",
        "ADA" to "Cardano", "AVAX" to "Avalanche", "DOT" to "Polkadot",
        "LINK" to "Chainlink", "MATIC" to "Polygon", "POL" to "Polygon",
        "UNI" to "Uniswap", "LTC" to "Litecoin", "ATOM" to "Cosmos",
        "FIL" to "Filecoin", "APT" to "Aptos", "ARB" to "Arbitrum",
        "OP" to "Optimism", "NEAR" to "NEAR", "SUI" to "Sui",
        "PEPE" to "Pepe", "WIF" to "dogwifhat", "SHIB" to "Shiba Inu",
        "TRX" to "TRON", "TON" to "Toncoin", "SEI" to "Sei",
        "USDT" to "Tether", "BUSD" to "BUSD"
    )

    private fun fullPairName(symbol: String): String {
        val base = baseAsset(symbol)
        val quote = quoteAsset(symbol)
        return "${coinNames[base] ?: base} / ${coinNames[quote] ?: quote}"
    }

    private fun changeColor(isPositive: Boolean) =
        if (isPositive) Color.parseColor("#10B981") else Color.parseColor("#EF4444")

    private suspend fun fetchAndCache(symbol: String): Triple<Float, Float, Float> {
        val currentPrice = ApiClient.api.getFuturesPrice(symbol).markPrice.toFloat()
        val cache = symbolCacheMap.getOrPut(symbol) { SymbolCache() }
        val now = System.currentTimeMillis()
        if (now - cache.lastKlineFetchTime > 300_000 || cache.sparkline == null) {
            try {
                val klines = ApiClient.api.getKlines(symbol, "5m", 60)
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

    private fun formatAbsAndPct(absChange: Float, pctChange: Float): String {
        val sign = if (absChange >= 0) "+" else ""
        return "${sign}${decFmt.format(absChange)}  ·  ${pctFmt.format(kotlin.math.abs(pctChange))}%"
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

        for (widgetId in widgetIds) {
            val symbol = prefs.getString("widget_${widgetId}_symbol", "BTCUSDT") ?: "BTCUSDT"
            val layoutId = BaseWidgetProvider.layoutForWidget(appWidgetManager, widgetId)

            try {
                val (currentPrice, changeAbs, changePct) = fetchAndCache(symbol)
                val isPositive = changeAbs >= 0
                val priceStr = decFmt.format(currentPrice)

                val views = RemoteViews(context.packageName, layoutId)

                // ── Populate all fields ──
                views.setTextViewText(R.id.tvSymbol, baseAsset(symbol))
                views.setTextViewText(R.id.tvName, fullPairName(symbol))
                views.setTextViewText(R.id.tvPrice, priceStr)
                views.setTextViewText(R.id.tvQuote, quoteAsset(symbol))
                views.setTextViewText(R.id.tvTime, timeStr)

                // Change text: abs+pct for large layouts, just pct for compact
                if (layoutId == R.layout.widget_size_wide || layoutId == R.layout.widget_size_medium) {
                    views.setTextViewText(R.id.tvChange, formatAbsAndPct(changeAbs, changePct))
                } else {
                    views.setTextViewText(R.id.tvChange, formatChange(changePct))
                }
                views.setTextColor(R.id.tvChange, changeColor(isPositive))

                // Sparkline — shown on medium and wide layouts
                if (layoutId == R.layout.widget_size_medium || layoutId == R.layout.widget_size_wide) {
                    symbolCacheMap[symbol]?.sparkline?.let {
                        views.setImageViewBitmap(R.id.imgGraph, it)
                    }
                }

                appWidgetManager.updateAppWidget(widgetId, views)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
