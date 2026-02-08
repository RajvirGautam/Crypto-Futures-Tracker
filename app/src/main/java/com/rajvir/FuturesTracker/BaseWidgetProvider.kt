package com.rajvir.FuturesTracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews

/**
 * Base class for all size-specific widget providers.
 *
 * Adaptive layout selection based on actual rendered dp size reported by
 * AppWidgetManager.getAppWidgetOptions():
 *
 *   • Tiny  (either dim < 110 dp): symbol + price only         → widget_size_tiny
 *   • Small (either dim < 160 dp): symbol + price + change     → widget_size_small
 *   • Full  (otherwise)          : full sparkline layout       → widget_crypto
 *
 * Also overrides onAppWidgetOptionsChanged so the layout re-selects when the
 * user drags to resize a widget that supports free resize.
 */
abstract class BaseWidgetProvider : AppWidgetProvider() {

    companion object {
        /**
         * Pick the right layout resource based on the widget's min width/height in dp.
         * Android reports the *minimum* guaranteed size via OPTION_APPWIDGET_MIN_WIDTH /
         * OPTION_APPWIDGET_MIN_HEIGHT inside the options Bundle.
         */
        fun layoutForSize(options: Bundle): Int {
            val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            return when {
                minW < 110 || minH < 110 -> R.layout.widget_size_tiny   // 1×1, 2×1, 1×2
                minW < 160 || minH < 160 -> R.layout.widget_size_small  // 1×3, 1×4, 3×1, 4×1, 2×2
                else                     -> R.layout.widget_crypto      // 2×3+ / 3×2+ / 3×3+
            }
        }

        /** Convenience to get options then pick layout. */
        fun layoutForWidget(manager: AppWidgetManager, widgetId: Int): Int =
            layoutForSize(manager.getAppWidgetOptions(widgetId))
    }

    // ── onUpdate ────────────────────────────────────────────────────────────

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        for (widgetId in appWidgetIds) {
            val symbol = prefs.getString("widget_${widgetId}_symbol", "BTCUSDT") ?: "BTCUSDT"
            val baseAsset = symbol.removeSuffix("USDT").removeSuffix("BUSD").removeSuffix("BTC")

            val layoutId = layoutForWidget(appWidgetManager, widgetId)
            val views = RemoteViews(context.packageName, layoutId)
            views.setTextViewText(R.id.tvSymbol, baseAsset)
            views.setTextViewText(R.id.tvPrice, "Loading...")

            // Wire tap-to-refresh using the subclass's concrete class so the right
            // provider broadcast receiver handles the intent.
            val refreshIntent = Intent(context, this::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
            }
            val pi = PendingIntent.getBroadcast(
                context, widgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.rootLayout, pi)
            appWidgetManager.updateAppWidget(widgetId, views)

            // Ensure PriceService is running for live 1-second tick updates
            val serviceIntent = Intent(context, PriceService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                context.startForegroundService(serviceIntent)
            else
                context.startService(serviceIntent)
        }
    }

    // ── onAppWidgetOptionsChanged ────────────────────────────────────────────
    // Called whenever the user resizes a widget. Re-inflate with the correct
    // layout bucket so the view hierarchy matches the new physical size.

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val symbol = prefs.getString("widget_${appWidgetId}_symbol", "BTCUSDT") ?: "BTCUSDT"
        val baseAsset = symbol.removeSuffix("USDT").removeSuffix("BUSD").removeSuffix("BTC")

        val layoutId = layoutForSize(newOptions)
        val views = RemoteViews(context.packageName, layoutId)
        views.setTextViewText(R.id.tvSymbol, baseAsset)
        views.setTextViewText(R.id.tvPrice, "…")

        val refreshIntent = Intent(context, this::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        val pi = PendingIntent.getBroadcast(
            context, appWidgetId, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.rootLayout, pi)
        appWidgetManager.updateAppWidget(appWidgetId, views)

        // Kick PriceService to push a fresh update for this widget
        val serviceIntent = Intent(context, PriceService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
            context.startForegroundService(serviceIntent)
        else
            context.startService(serviceIntent)

        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }
}
