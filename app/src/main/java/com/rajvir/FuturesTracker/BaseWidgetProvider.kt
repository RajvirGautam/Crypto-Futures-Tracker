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
 *   • Tiny  (either dim < 80 dp) : BTC + price + %            → widget_size_tiny
 *   • Small (< 150 dp both)      : icon + BTC + price + %     → widget_size_small
 *   • Medium                      : icon + name + chart + price → widget_size_medium
 *   • Wide  (≥ 176 dp wide)      : full card with big chart   → widget_size_wide
 *
 * Also overrides onAppWidgetOptionsChanged so the layout re-selects when the
 * user drags to resize a widget that supports free resize.
 */
abstract class BaseWidgetProvider : AppWidgetProvider() {

    companion object {
        /**
         * Pick layout based on OPTION_APPWIDGET_MIN_WIDTH / MIN_HEIGHT (dp).
         *
     *  Tiny   < 80 wide OR < 80 tall  → 1×1, 2×1, 1×2  → BTC + price + %
     *  Small  ≥ 80 both, < 150 both   → 2×2, 1×3, 1×4  → icon + BTC + price + %
     *  Wide   ≥ 176 wide & ≥ 80 tall  → 4×2+           → full card: header + chart + price + sub-info
     *  Medium everything else          → 3×2, 2×3 etc  → header + chart + price + change
         */
        fun layoutForSize(options: Bundle): Int {
            val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            return when {
                minW < 80 || minH < 80           -> R.layout.widget_size_tiny   // 1×1, 2×1, 1×2
                minW >= 176 && minH >= 80        -> R.layout.widget_size_wide   // 4×2+ single coin + large chart
                minW < 150 && minH < 150         -> R.layout.widget_size_small  // 2×2, narrow/short
                else                             -> R.layout.widget_size_medium // 3×2, 2×3, etc.
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
        // Delegate to WidgetUpdater which handles multi-coin widgets with graphs,
        // decimal formatting, border opacity, and graph timeframe selection
        for (widgetId in appWidgetIds) {
            WidgetUpdater.applyLoadingState(context, appWidgetManager, widgetId)
        }

        // Ensure PriceService is running for live tick updates
        try {
            val serviceIntent = Intent(context, PriceService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                context.startForegroundService(serviceIntent)
            else
                context.startService(serviceIntent)
        } catch (_: Exception) { }
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
        // Reapply loading state with new layout for resized widget
        WidgetUpdater.applyLoadingState(context, appWidgetManager, appWidgetId)

        // Kick PriceService to push a fresh update for this widget
        try {
            val serviceIntent = Intent(context, PriceService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                context.startForegroundService(serviceIntent)
            else
                context.startService(serviceIntent)
        } catch (_: Exception) { }

        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }
}
