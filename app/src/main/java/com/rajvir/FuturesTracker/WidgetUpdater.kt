package com.rajvir.FuturesTracker

import android.appwidget.AppWidgetManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.RemoteViews
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object WidgetUpdater {

    private val providerClasses = listOf(
        AdaptiveWidgetProvider::class.java,
        CryptoWidgetProvider::class.java,
        Widget1x1Provider::class.java,
        Widget1x2Provider::class.java,
        Widget1x3Provider::class.java,
        Widget1x4Provider::class.java,
        Widget2x1Provider::class.java,
        Widget2x2Provider::class.java,
        Widget2x3Provider::class.java,
        Widget2x4Provider::class.java,
        Widget3x1Provider::class.java,
        Widget3x2Provider::class.java,
        Widget3x3Provider::class.java,
        Widget3x4Provider::class.java,
        Widget4x1Provider::class.java,
        Widget4x2Provider::class.java,
        Widget4x3Provider::class.java,
        Widget4x4Provider::class.java
    )

    // ── Cache ────────────────────────────────────────────────────────────────

    private data class SymbolCache(
        var sparkline: Bitmap? = null,
        var lastKlineFetch: Long = 0L,
        var lastSeriesUpdate: Long = 0L,
        var openPrice: Double = 0.0,
        var highPrice: Double = 0.0,
        var lowPrice: Double = 0.0,
        var series: MutableList<Float> = mutableListOf()
    )
    private val cache = mutableMapOf<String, SymbolCache>()
    private val nextWidgetUpdateAt = mutableMapOf<Int, Long>()

    private fun cacheKey(
        widgetId: Int,
        sym: String,
        graphTimeframeLabel: String,
        klineInterval: String,
        graphW: Int,
        graphH: Int
    ): String = "$widgetId@$sym@$graphTimeframeLabel@$klineInterval@${graphW}x${graphH}"

    private fun durationLabelToMillis(label: String): Long {
        val trimmed = label.trim().lowercase(Locale.US)
        if (trimmed.isEmpty()) return 60_000L
        val unit = trimmed.last()
        val value = trimmed.dropLast(1).toLongOrNull() ?: return 60_000L
        return when (unit) {
            's' -> value * 1_000L
            'm' -> value * 60_000L
            'h' -> value * 3_600_000L
            'd' -> value * 86_400_000L
            else -> 60_000L
        }
    }

    // ── Formatters ───────────────────────────────────────────────────────────
    private val US = DecimalFormatSymbols(Locale.US)
    private val pctFmt = DecimalFormat("0.00", US)
    private val fundingFmt = DecimalFormat("0.00000000", US)

    /** Format raw API price string to exactly [decimals] decimal places with thousand separators. */
    fun formatPrice(raw: String, decimals: Int): String {
        return try {
            val value = BigDecimal(raw).toDouble()
            val pattern = "#,##0" + if (decimals > 0) "." + "0".repeat(decimals) else ""
            "$" + DecimalFormat(pattern, US).format(value)
        } catch (_: Exception) { raw }
    }

    // ── Coin metadata ─────────────────────────────────────────────────────────
    private val coinNames = mapOf(
        "BTC" to "Bitcoin",  "ETH" to "Ethereum", "BNB" to "BNB",
        "SOL" to "Solana",   "XRP" to "XRP",       "ADA" to "Cardano",
        "DOGE" to "Dogecoin","DOT" to "Polkadot",  "LINK" to "Chainlink",
        "LTC" to "Litecoin", "AVAX" to "Avalanche","MATIC" to "Polygon",
        "UNI" to "Uniswap",  "ATOM" to "Cosmos",   "TRX" to "TRON",
        "NEAR" to "NEAR",    "APT" to "Aptos",      "ARB" to "Arbitrum",
        "OP" to "Optimism",  "SUI" to "Sui",        "INJ" to "Injective"
    )
    private val quoteNames = mapOf(
        "USDT" to "Tether", "BUSD" to "Binance USD", "BTC" to "Bitcoin"
    )

    private fun splitSymbol(sym: String): Pair<String, String> {
        val q = when { sym.endsWith("USDT") -> "USDT"; sym.endsWith("BUSD") -> "BUSD"
                       sym.endsWith("BTC") -> "BTC"; else -> "" }
        return (if (q.isNotEmpty()) sym.removeSuffix(q) else sym) to q
    }

    private fun baseLabel(sym: String) = splitSymbol(sym).first

    private fun subtitleLabel(sym: String): String {
        val (base, quote) = splitSymbol(sym)
        return "${coinNames[base] ?: base} / ${quoteNames[quote] ?: quote}"
    }

    private fun changeColor(pos: Boolean) =
        if (pos) Color.parseColor("#22C55E") else Color.parseColor("#F43F5E")

    private fun fundingColor(ratePct: Double): Int = when {
        ratePct > 0.0 -> Color.parseColor("#22C55E")
        ratePct < 0.0 -> Color.parseColor("#F43F5E")
        else -> Color.parseColor("#C8CDD4")
    }

    private fun fundingText(ratePct: Double): String {
        val sign = if (ratePct >= 0) "+" else ""
        return "F $sign${fundingFmt.format(ratePct)}%"
    }

    // ── Background bitmap ────────────────────────────────────────────────────

    private fun getBgResForAlpha(borderAlpha: Int): Int {
        return when {
            borderAlpha < 12 -> R.drawable.widget_bg_0
            borderAlpha < 37 -> R.drawable.widget_bg_25
            borderAlpha < 62 -> R.drawable.widget_bg_50
            borderAlpha < 87 -> R.drawable.widget_bg_75
            else -> R.drawable.widget_bg_100
        }
    }

    // ── Data fetching ─────────────────────────────────────────────────────────

    private data class PriceData(
        val raw: String,
        val price: Double,
        val changeAbs: Double,
        val changePct: Double,
        val fundingRatePct: Double,
        val high: Double,
        val low: Double
    )

    private suspend fun fetchAndCache(
        widgetId: Int,
        sym: String,
        shade: Boolean,
        graphTimeframeLabel: String,
        klineInterval: String,
        graphW: Int,
        graphH: Int,
        refreshIntervalMs: Long,
        graphSpanMs: Long
    ): PriceData {
        val premium = ApiClient.api.getFuturesPrice(sym)
        val raw   = premium.markPrice
        val price = raw.toDouble()
        val fundingRatePct = (premium.lastFundingRate.toDoubleOrNull() ?: 0.0) * 100.0
        val key = cacheKey(widgetId, sym, graphTimeframeLabel, klineInterval, graphW, graphH)
        val entry = cache.getOrPut(key) { SymbolCache() }
        val now   = System.currentTimeMillis()
        val maxPoints = (graphW / 24).coerceIn(40, 120)
        val plotStepMs = graphSpanMs.coerceAtLeast(1_000L)

        if (entry.series.isEmpty() || now - entry.lastKlineFetch > maxOf(refreshIntervalMs * 10, 60_000L)) {
            try {
                val klines = ApiClient.api.getKlines(sym, klineInterval, 60)
                entry.openPrice = if (klines.isNotEmpty()) klines.first()[1].toDouble() else price
                val closes = if (klines.isNotEmpty()) klines.map { it[4].toFloat() } else listOf(price.toFloat())
                // Keep H/L consistent with what is visually plotted (close series on sparkline).
                entry.highPrice = closes.maxOrNull()?.toDouble() ?: price
                entry.lowPrice = closes.minOrNull()?.toDouble() ?: price
                entry.series = closes.takeLast(maxPoints).toMutableList()
                while (entry.series.size < maxPoints) {
                    entry.series.add(0, entry.series.firstOrNull() ?: price.toFloat())
                }
                entry.lastKlineFetch = now
                entry.lastSeriesUpdate = now
            } catch (_: Exception) {}
        }

        if (entry.series.isEmpty()) {
            repeat(maxPoints) { entry.series.add(price.toFloat()) }
            entry.openPrice = price
            entry.highPrice = price
            entry.lowPrice = price
            entry.lastSeriesUpdate = now
        }

        val elapsedSinceStep = now - entry.lastSeriesUpdate
        if (elapsedSinceStep >= plotStepMs) {
            val advanceSlots = (elapsedSinceStep / plotStepMs)
                .toInt()
                .coerceAtLeast(1)
                .coerceAtMost(maxPoints)
            repeat(advanceSlots) {
                entry.series.add(price.toFloat())
            }
            while (entry.series.size > maxPoints) {
                entry.series.removeAt(0)
            }
            entry.lastSeriesUpdate += advanceSlots * plotStepMs
        } else {
            entry.series[entry.series.lastIndex] = price.toFloat()
        }

        entry.highPrice = maxOf(entry.highPrice, entry.series.maxOrNull()?.toDouble() ?: price)
        entry.lowPrice = minOf(entry.lowPrice, entry.series.minOrNull()?.toDouble() ?: price)
        entry.sparkline = SparklineRenderer.render(
            data = entry.series,
            positive = price >= entry.openPrice,
            width = graphW,
            height = graphH,
            plotStepMs = plotStepMs
        )

        val abs = price - entry.openPrice
        val pct = if (entry.openPrice != 0.0) (abs / entry.openPrice) * 100 else 0.0
        val high = if (entry.highPrice == 0.0) price else entry.highPrice
        val low = if (entry.lowPrice == 0.0) price else entry.lowPrice
        return PriceData(raw, price, abs, pct, fundingRatePct, high, low)
    }

    // ── Change text builder ──────────────────────────────────────────────────

    /**
     * Single-coin (1coin layout): "USDT +1,510.99 · 1.85%"
     * Multi-coin columns: compact "+1.85%"
     */
    private fun appendColoredText(sb: SpannableStringBuilder, text: String, color: Int) {
        val start = sb.length
        sb.append(text)
        sb.setSpan(ForegroundColorSpan(color), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun changeText(sym: String, abs: Double, pct: Double, compact: Boolean, decimals: Int, high: Double, low: Double): CharSequence {
        val pctStr = (if (pct >= 0) "+" else "") + pctFmt.format(pct) + "%"
        val pattern = "#,##0" + if (decimals > 0) "." + "0".repeat(decimals) else ""
        val fmt = DecimalFormat(pattern, US)
        val white = Color.WHITE
        val green = Color.parseColor("#22C55E")
        val red = Color.parseColor("#F43F5E")
        val pctColor = if (pct >= 0) green else red

        val sb = SpannableStringBuilder()
        appendColoredText(sb, pctStr, pctColor)
        appendColoredText(sb, " | ", white)
        appendColoredText(sb, "H : ", white)
        appendColoredText(sb, fmt.format(high), green)
        appendColoredText(sb, " | ", white)
        appendColoredText(sb, "L : ", white)
        appendColoredText(sb, fmt.format(low), red)

        if (compact) return sb
        val (_, quote) = splitSymbol(sym)
        val absStr = (if (abs >= 0) "+" else "") + fmt.format(kotlin.math.abs(abs))
        return SpannableStringBuilder().apply {
            appendColoredText(this, "$quote $absStr · ", white)
            appendColoredText(this, pctStr, pctColor)
            appendColoredText(this, " | ", white)
            appendColoredText(this, "H : ", white)
            appendColoredText(this, fmt.format(high), green)
            appendColoredText(this, " | ", white)
            appendColoredText(this, "L : ", white)
            appendColoredText(this, fmt.format(low), red)
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Immediate placeholder shown right after config saves or on first onUpdate. */
    fun applyLoadingState(ctx: Context, mgr: AppWidgetManager, widgetId: Int) {
        val coinCount   = WidgetPrefs.getCoinCount(ctx, widgetId)
        val coins       = WidgetPrefs.getCoins(ctx, widgetId)
        val borderAlpha = WidgetPrefs.getBorderAlpha(ctx, widgetId)
        val layoutId    = WidgetPrefs.layoutResId(coinCount)
        val views       = RemoteViews(ctx.packageName, layoutId)

        views.setInt(R.id.rootLayout, "setBackgroundResource", getBgResForAlpha(borderAlpha))
        
        // Slot 1
        views.setTextViewText(R.id.tvSymbol,   baseLabel(coins.getOrElse(0) { "BTCUSDT" }))
        views.setTextViewText(R.id.tvSubtitle, subtitleLabel(coins.getOrElse(0) { "BTCUSDT" }))
        views.setTextViewText(R.id.tvPrice, "Loading…")
        views.setTextViewText(R.id.tvFunding, "F --")
        
        // Slot 2
        if (coinCount >= 2) {
            views.setTextViewText(R.id.tvSymbol2, baseLabel(coins.getOrElse(1) { "ETHUSDT" }))
            views.setTextViewText(R.id.tvPrice2, "Loading…")
            views.setTextViewText(R.id.tvFunding2, "F --")
        }
        // Slot 3
        if (coinCount >= 3) {
            views.setTextViewText(R.id.tvSymbol3, baseLabel(coins.getOrElse(2) { "SOLUSDT" }))
            views.setTextViewText(R.id.tvPrice3, "Loading…")
            views.setTextViewText(R.id.tvFunding3, "F --")
        }
        // Slot 4
        if (coinCount >= 4) {
            views.setTextViewText(R.id.tvSymbol4, baseLabel(coins.getOrElse(3) { "BNBUSDT" }))
            views.setTextViewText(R.id.tvPrice4, "Loading…")
            views.setTextViewText(R.id.tvFunding4, "F --")
        }

        views.setOnClickPendingIntent(R.id.rootLayout, configPendingIntent(ctx, widgetId))
        mgr.updateAppWidget(widgetId, views)
    }

    private fun graphBitmapSizeForWidget(
        ctx: Context,
        mgr: AppWidgetManager,
        widgetId: Int,
        coinCount: Int
    ): Pair<Int, Int> {
        val opts = mgr.getAppWidgetOptions(widgetId)
        val density = ctx.resources.displayMetrics.density
        val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
        val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 120)
        val wPx = (wDp * density).toInt().coerceAtLeast(300)
        val hPx = (hDp * density).toInt().coerceAtLeast(180)

        val largeWidget = wDp >= 240 || hDp >= 240
        val graphHeightRatio = when (coinCount) {
            1 -> if (largeWidget) 0.54f else 0.48f
            2 -> if (largeWidget) 0.60f else 0.46f
            3 -> if (largeWidget) 0.56f else 0.40f
            else -> if (largeWidget) 0.52f else 0.36f
        }

        val columns = if (coinCount == 1) 1 else 2
        val rows = when (coinCount) {
            1, 2 -> 1
            else -> 2
        }

        val horizontalPaddingPx = (16f * density).toInt()
        val verticalPaddingPx = (16f * density).toInt()
        val usableW = (wPx - horizontalPaddingPx).coerceAtLeast(200)
        val usableH = (hPx - verticalPaddingPx).coerceAtLeast(120)
        val slotW = (usableW / columns.toFloat()).toInt()
        val slotH = ((usableH / rows.toFloat()) * graphHeightRatio).toInt()

        val superSample = 2
        val graphW = (slotW * superSample).coerceIn(500, 2600)
        val graphH = (slotH * superSample).coerceIn(240, 1400)
        return graphW to graphH
    }

    suspend fun updateAllWidgets(ctx: Context, forceRefresh: Boolean = false) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val widgetIds = getAllWidgetIds(ctx)
        if (widgetIds.isEmpty()) return

        val now = System.currentTimeMillis()
        val activeIds = widgetIds.toSet()
        nextWidgetUpdateAt.keys.retainAll(activeIds)

        val timeStr = SimpleDateFormat("H:mm", Locale.getDefault()).format(Date())

        for (widgetId in widgetIds) {
            val coinCount   = WidgetPrefs.getCoinCount(ctx, widgetId)
            val coins       = WidgetPrefs.getCoins(ctx, widgetId)
            val decimals    = WidgetPrefs.getDecimals(ctx, widgetId)
            val borderAlpha = WidgetPrefs.getBorderAlpha(ctx, widgetId)
            val shade       = WidgetPrefs.getShadeGraph(ctx, widgetId)
            val graphTimeframeLabel = WidgetPrefs.getGraphTimeframeLabel(ctx, widgetId)
            val graphTf     = GraphTimeframes.byLabel(graphTimeframeLabel)
            val graphSpanMs = durationLabelToMillis(graphTimeframeLabel)
            val widgetInterval = UpdateIntervals.byLabel(WidgetPrefs.getWidgetUpdateIntervalLabel(ctx, widgetId))
            val widgetRefreshIntervalMs = widgetInterval.millis.coerceAtLeast(1_000L)

            if (!forceRefresh && now < (nextWidgetUpdateAt[widgetId] ?: 0L)) continue

            val layoutId    = WidgetPrefs.layoutResId(coinCount)
            val (graphW, graphH) = graphBitmapSizeForWidget(ctx, mgr, widgetId, coinCount)

            try {
                // Fetch all required coins
                val dataList = coins.map { sym ->
                    try {
                        fetchAndCache(
                            widgetId,
                            sym,
                            shade,
                            graphTimeframeLabel,
                            graphTf.klineInterval,
                            graphW,
                            graphH,
                            widgetRefreshIntervalMs,
                            graphSpanMs
                        )
                    }
                    catch (_: Exception) { PriceData("0", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0) }
                }

                val views = RemoteViews(ctx.packageName, layoutId)
                views.setInt(R.id.rootLayout, "setBackgroundResource", getBgResForAlpha(borderAlpha))
                views.setOnClickPendingIntent(R.id.rootLayout, configPendingIntent(ctx, widgetId))

                // Compact change text for multi-coin cols, full for single
                val compact = coinCount > 1

                // Slot 1 — present in all layouts
                val d1 = dataList.getOrElse(0) { PriceData("0", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0) }
                val s1 = coins.getOrElse(0) { "BTCUSDT" }
                views.setTextViewText(R.id.tvSymbol,   baseLabel(s1))
                views.setTextViewText(R.id.tvSubtitle, subtitleLabel(s1))
                views.setTextViewText(R.id.tvTime,     timeStr)
                views.setTextViewText(R.id.tvPrice,    formatPrice(d1.raw, decimals))
                views.setTextViewText(R.id.tvFunding, fundingText(d1.fundingRatePct))
                views.setTextColor(R.id.tvFunding, fundingColor(d1.fundingRatePct))
                views.setTextViewText(R.id.tvChange,   changeText(s1, d1.changeAbs, d1.changePct, compact, decimals, d1.high, d1.low))
                cache[cacheKey(widgetId, s1, graphTimeframeLabel, graphTf.klineInterval, graphW, graphH)]?.sparkline
                    ?.let { views.setImageViewBitmap(R.id.imgGraph, it) }

                // Slot 2
                if (coinCount >= 2) {
                    val d2 = dataList.getOrElse(1) { PriceData("0", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0) }
                    val s2 = coins.getOrElse(1) { "ETHUSDT" }
                    views.setTextViewText(R.id.tvSymbol2, baseLabel(s2))
                    views.setTextViewText(R.id.tvPrice2,  formatPrice(d2.raw, decimals))
                    views.setTextViewText(R.id.tvFunding2, fundingText(d2.fundingRatePct))
                    views.setTextColor(R.id.tvFunding2, fundingColor(d2.fundingRatePct))
                    views.setTextViewText(R.id.tvChange2, changeText(s2, d2.changeAbs, d2.changePct, true, decimals, d2.high, d2.low))
                    cache[cacheKey(widgetId, s2, graphTimeframeLabel, graphTf.klineInterval, graphW, graphH)]?.sparkline
                        ?.let { views.setImageViewBitmap(R.id.imgGraph2, it) }
                }

                // Slot 3
                if (coinCount >= 3) {
                    val d3 = dataList.getOrElse(2) { PriceData("0", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0) }
                    val s3 = coins.getOrElse(2) { "SOLUSDT" }
                    views.setTextViewText(R.id.tvSymbol3, baseLabel(s3))
                    views.setTextViewText(R.id.tvPrice3,  formatPrice(d3.raw, decimals))
                    views.setTextViewText(R.id.tvFunding3, fundingText(d3.fundingRatePct))
                    views.setTextColor(R.id.tvFunding3, fundingColor(d3.fundingRatePct))
                    views.setTextViewText(R.id.tvChange3, changeText(s3, d3.changeAbs, d3.changePct, true, decimals, d3.high, d3.low))
                    cache[cacheKey(widgetId, s3, graphTimeframeLabel, graphTf.klineInterval, graphW, graphH)]?.sparkline
                        ?.let { views.setImageViewBitmap(R.id.imgGraph3, it) }
                }

                // Slot 4
                if (coinCount >= 4) {
                    val d4 = dataList.getOrElse(3) { PriceData("0", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0) }
                    val s4 = coins.getOrElse(3) { "BNBUSDT" }
                    views.setTextViewText(R.id.tvSymbol4, baseLabel(s4))
                    views.setTextViewText(R.id.tvPrice4,  formatPrice(d4.raw, decimals))
                    views.setTextViewText(R.id.tvFunding4, fundingText(d4.fundingRatePct))
                    views.setTextColor(R.id.tvFunding4, fundingColor(d4.fundingRatePct))
                    views.setTextViewText(R.id.tvChange4, changeText(s4, d4.changeAbs, d4.changePct, true, decimals, d4.high, d4.low))
                    cache[cacheKey(widgetId, s4, graphTimeframeLabel, graphTf.klineInterval, graphW, graphH)]?.sparkline
                        ?.let { views.setImageViewBitmap(R.id.imgGraph4, it) }
                }

                mgr.updateAppWidget(widgetId, views)
                nextWidgetUpdateAt[widgetId] = now + widgetRefreshIntervalMs

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getAllWidgetIds(ctx: Context): IntArray {
        val mgr = AppWidgetManager.getInstance(ctx)
        return providerClasses
            .flatMap { mgr.getAppWidgetIds(ComponentName(ctx, it)).toList() }
            .toIntArray()
    }

    fun hasAnyWidgets(ctx: Context): Boolean = getAllWidgetIds(ctx).isNotEmpty()

    private fun configPendingIntent(ctx: Context, widgetId: Int): PendingIntent {
        val configIntent = Intent(ctx, WidgetConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        return PendingIntent.getActivity(
            ctx,
            widgetId,
            configIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
