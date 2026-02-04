package com.rajvir.FuturesTracker

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*

class WidgetConfigActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

            val result = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }

            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }
}