package com.rajvir.FuturesTracker

import android.content.Context

object WidgetPrefs {
    private const val PREF = "widget_prefs"
    private const val KEY_SYMBOL = "widget_symbol"

    fun saveSymbol(context: Context, symbol: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SYMBOL, symbol)
            .apply()
    }

    fun getSymbol(context: Context): String {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_SYMBOL, "BTCUSDT")!!
    }
}