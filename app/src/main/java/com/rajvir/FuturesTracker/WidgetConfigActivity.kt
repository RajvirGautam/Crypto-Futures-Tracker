package com.rajvir.FuturesTracker

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.RemoteViews
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*

class WidgetConfigActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Default result to CANCELED so backing out removes the widget correctly
        setResult(Activity.RESULT_CANCELED)

        setContentView(R.layout.activity_widget_config)

        prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val etSymbol = findViewById<AutoCompleteTextView>(R.id.etWidgetSymbol)
        val btnDone = findViewById<MaterialButton>(R.id.btnWidgetDone)

        // Load symbols
        CoroutineScope(Dispatchers.IO).launch {
            val info = ApiClient.api.getExchangeInfo()
            val symbols = info.symbols
                .filter { it.contractType == "PERPETUAL" && it.status == "TRADING" }
                .map { it.symbol }
                .sorted()

            withContext(Dispatchers.Main) {
                etSymbol.setAdapter(
                    ArrayAdapter(
                        this@WidgetConfigActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        symbols
                    )
                )
            }
        }

        btnDone.setOnClickListener {
            val symbol = etSymbol.text.toString().uppercase()
            if (symbol.isEmpty()) return@setOnClickListener

            // SAVE CONFIG FOR THIS WIDGET ID
            prefs.edit()
                .putString("widget_${appWidgetId}_symbol", symbol)
                .apply()

            // ── Perform the initial widget update so it renders immediately ──
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val layoutId = BaseWidgetProvider.layoutForWidget(appWidgetManager, appWidgetId)
            val baseAsset = when {
                symbol.endsWith("USDT") -> symbol.removeSuffix("USDT")
                symbol.endsWith("BUSD") -> symbol.removeSuffix("BUSD")
                symbol.endsWith("BTC")  -> symbol.removeSuffix("BTC")
                else -> symbol
            }

            val views = RemoteViews(packageName, layoutId)
            views.setTextViewText(R.id.tvSymbol, baseAsset)
            views.setTextViewText(R.id.tvPrice, "Loading...")

            // Wire tap-to-refresh
            val refreshIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            val pi = PendingIntent.getBroadcast(
                this, appWidgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.rootLayout, pi)
            appWidgetManager.updateAppWidget(appWidgetId, views)

            // Kick PriceService to push live data into all widgets
            try {
                val serviceIntent = Intent(this, PriceService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                    startForegroundService(serviceIntent)
                else
                    startService(serviceIntent)
            } catch (_: Exception) { }

            val result = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }

            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }
}