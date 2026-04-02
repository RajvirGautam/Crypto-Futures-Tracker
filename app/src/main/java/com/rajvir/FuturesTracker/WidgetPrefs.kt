package com.rajvir.FuturesTracker

import android.content.Context
import android.content.SharedPreferences

/**
 * Per-widget SharedPreferences.  All keys are "widget_<id>_<field>".
 */
object WidgetPrefs {

    private const val PREFS = "widget_prefs"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── coin count (1–4) ─────────────────────────────────────────────────────
    fun getCoinCount(ctx: Context, id: Int): Int =
        prefs(ctx).getInt("widget_${id}_coinCount", 1).coerceIn(1, 4)

    fun saveCoinCount(ctx: Context, id: Int, n: Int) =
        prefs(ctx).edit().putInt("widget_${id}_coinCount", n.coerceIn(1, 4)).apply()

    // ── per-slot symbols (1-indexed) ─────────────────────────────────────────
    fun getCoin(ctx: Context, id: Int, slot: Int): String =
        prefs(ctx).getString("widget_${id}_coin$slot", defaultCoin()) ?: defaultCoin()

    fun saveCoin(ctx: Context, id: Int, slot: Int, symbol: String) =
        prefs(ctx).edit().putString("widget_${id}_coin$slot", symbol).apply()

    private fun defaultCoin() = ""

    /** Saves the full ordered list of coins for a widget. */
    fun saveCoins(ctx: Context, id: Int, coins: List<String>) {
        val ed = prefs(ctx).edit()
        coins.forEachIndexed { i, sym -> ed.putString("widget_${id}_coin${i + 1}", sym) }
        ed.apply()
    }

    /** Returns the ordered coin list up to coinCount. */
    fun getCoins(ctx: Context, id: Int): List<String> {
        val n = getCoinCount(ctx, id)
        return (1..n).map { getCoin(ctx, id, it) }
    }

    // ── display options ──────────────────────────────────────────────────────
    /** Decimal places 0–8.  Default 2. */
    fun getDecimals(ctx: Context, id: Int): Int =
        prefs(ctx).getInt("widget_${id}_decimals", 2).coerceIn(0, 8)

    fun saveDecimals(ctx: Context, id: Int, d: Int) =
        prefs(ctx).edit().putInt("widget_${id}_decimals", d.coerceIn(0, 8)).apply()

    /** Border opacity 0–100.  Default 25. */
    fun getBorderAlpha(ctx: Context, id: Int): Int =
        prefs(ctx).getInt("widget_${id}_borderAlpha", 25)

    fun saveBorderAlpha(ctx: Context, id: Int, a: Int) =
        prefs(ctx).edit().putInt("widget_${id}_borderAlpha", a.coerceIn(0, 100)).apply()

    /** Shade area under sparkline.  Default true. */
    fun getShadeGraph(ctx: Context, id: Int): Boolean =
        prefs(ctx).getBoolean("widget_${id}_shadeGraph", true)

    fun saveShadeGraph(ctx: Context, id: Int, v: Boolean) =
        prefs(ctx).edit().putBoolean("widget_${id}_shadeGraph", v).apply()

    /** Graph timeframe label (e.g. 30s, 1m, 15m). Default 1m. */
    fun getGraphTimeframeLabel(ctx: Context, id: Int): String =
        prefs(ctx).getString("widget_${id}_graphTimeframe", "1m") ?: "1m"

    fun saveGraphTimeframeLabel(ctx: Context, id: Int, label: String) =
        prefs(ctx).edit().putString("widget_${id}_graphTimeframe", label).apply()

    /** Widget update interval label (e.g. 1s, 1m, 5m). Default 1m. */
    fun getWidgetUpdateIntervalLabel(ctx: Context, id: Int): String =
        prefs(ctx).getString("widget_${id}_updateInterval", "1m") ?: "1m"

    fun saveWidgetUpdateIntervalLabel(ctx: Context, id: Int, label: String) =
        prefs(ctx).edit().putString("widget_${id}_updateInterval", label).apply()

    // ── cleanup ──────────────────────────────────────────────────────────────
    fun deleteAll(ctx: Context, id: Int) {
        prefs(ctx).edit().apply {
            remove("widget_${id}_coinCount")
            for (s in 1..4) remove("widget_${id}_coin$s")
            remove("widget_${id}_decimals")
            remove("widget_${id}_borderAlpha")
            remove("widget_${id}_shadeGraph")
            remove("widget_${id}_graphTimeframe")
            remove("widget_${id}_updateInterval")
        }.apply()
    }

    // ── layout selection ─────────────────────────────────────────────────────
    fun layoutResId(coinCount: Int): Int = when (coinCount) {
        1    -> R.layout.widget_1coin
        2    -> R.layout.widget_2coin
        3    -> R.layout.widget_3coin
        else -> R.layout.widget_4coin
    }

    // ── legacy global symbol (used by PriceWidgetProvider only) ─────────────
    fun getSymbol(ctx: Context): String =
        prefs(ctx).getString("widget_symbol", "BTCUSDT") ?: "BTCUSDT"

    // ── compat: single-coin helpers used by config/loading state ─────────────
    fun getSymbol(ctx: Context, id: Int): String = getCoin(ctx, id, 1)
    fun saveSymbol(ctx: Context, id: Int, sym: String) = saveCoin(ctx, id, 1, sym)
}
