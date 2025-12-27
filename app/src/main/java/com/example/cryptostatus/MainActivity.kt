package com.example.cryptostatus

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, i ->
            val bars = i.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, v.paddingBottom)
            i
        }

        prefs = getSharedPreferences("crypto_prefs", MODE_PRIVATE)

        val etSymbol = findViewById<AutoCompleteTextView>(R.id.etSymbol)
        val seek = findViewById<SeekBar>(R.id.seekDigits)
        val preview = findViewById<TextView>(R.id.tvDigitPreview)
        val btn = findViewById<Button>(R.id.btnToggle)
        val status = findViewById<TextView>(R.id.tvStatus)

        etSymbol.setText(prefs.getString("symbol", "XRPUSDT"))
        seek.progress = prefs.getInt("digit_start_index", 0)
        preview.text = "Showing digits starting at index ${seek.progress}"

        loadSymbols(etSymbol)

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                prefs.edit().putInt("digit_start_index", p).apply()
                preview.text = "Showing digits starting at index $p"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        btn.setOnClickListener {
            if (!running) {
                prefs.edit()
                    .putString("symbol", etSymbol.text.toString().uppercase())
                    .apply()

                requestPermissionAndStart()

                running = true
                btn.text = "STOP SERVICE"
                btn.setBackgroundColor(Color.RED)
                status.text = "Service running"
                status.setTextColor(Color.GREEN)
            } else {
                stopService(Intent(this, PriceService::class.java))
                running = false
                btn.text = "START SERVICE"
                btn.setBackgroundColor(Color.GREEN)
                status.text = "Service stopped"
                status.setTextColor(Color.RED)
            }
        }
    }

    private fun loadSymbols(et: AutoCompleteTextView) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val info = ApiClient.api.getExchangeInfo()
                val symbols = info.symbols
                    .filter { it.contractType == "PERPETUAL" && it.status == "TRADING" }
                    .map { it.symbol }
                    .sorted()

                withContext(Dispatchers.Main) {
                    et.setAdapter(
                        ArrayAdapter(
                            this@MainActivity,
                            android.R.layout.simple_dropdown_item_1line,
                            symbols
                        )
                    )
                }
            } catch (_: Exception) {}
        }
    }

    private fun requestPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        } else {
            ContextCompat.startForegroundService(this, Intent(this, PriceService::class.java))
        }
    }
}