package com.rajvir.FuturesTracker

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.rajvir.FuturesTracker.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider // Use Material Slider
import com.rajvir.FuturesTracker.ApiClient
import com.rajvir.FuturesTracker.PriceService
// import com.rajvir.FuturesTracker.PriceService
import kotlinx.coroutines.*
import androidx.work.*

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private var running = false
    private var previewJob: Job? = null
    private var symbolsLoaded = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Edge-to-edge handling
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, i ->
            val bars = i.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, 0) // Only pad top, let bottom flow behind nav bar
            i
        }

        prefs = getSharedPreferences("crypto_prefs", MODE_PRIVATE)

        val etSymbol = findViewById<AutoCompleteTextView>(R.id.etSymbol)
        val slider = findViewById<Slider>(R.id.seekDigits) // Changed to Material Slider
        val tvPrice = findViewById<TextView>(R.id.tvPricePreview)
        val tvDigits = findViewById<TextView>(R.id.tvDigitPreview)
        val btn = findViewById<MaterialButton>(R.id.btnToggle)
        val status = findViewById<TextView>(R.id.tvStatus)

        etSymbol.setText(prefs.getString("symbol", "XRPUSDT"))

        // Material Slider works with floats
        slider.value = prefs.getInt("digit_start_index", 0).toFloat()

        loadSymbols(etSymbol)

        etSymbol.setOnClickListener {
            if (symbolsLoaded) {
                etSymbol.showDropDown()
            }
        }

        findViewById<MaterialButton>(R.id.btnAddWidget).setOnClickListener {
            val intent = Intent(this, WidgetConfigActivity::class.java)
            startActivity(intent)
        }

        etSymbol.setOnItemClickListener { _, _, _, _ ->
            prefs.edit().putString("symbol", etSymbol.text.toString()).apply()
        }

        fun refreshPreview(price: Double) {
            val raw = String.format("%.4f", price)
            val clean = raw.replace(".", "")

            // Slider returns float, cast to int
            val currentIndex = slider.value.toInt()
            val start = currentIndex.coerceAtMost(clean.length - 3)

            val spannable = SpannableString(raw)
            var mapIndex = 0

            for (i in raw.indices) {
                if (raw[i] != '.') {
                    if (mapIndex in start until start + 3) {
                        // Use Binance Yellow for highlight
                        spannable.setSpan(
                            ForegroundColorSpan(Color.parseColor("#F0B90B")),
                            i, i + 1,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    } else {
                        // Dim unselected digits
                        spannable.setSpan(
                            ForegroundColorSpan(Color.parseColor("#44FFFFFF")),
                            i, i + 1,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    mapIndex++
                }
            }

            tvPrice.text = spannable
            tvDigits.text = "STATUS BAR: [ ${clean.substring(start, start + 3)} ]"
        }

        slider.addOnChangeListener { _, value, _ ->
            prefs.edit().putInt("digit_start_index", value.toInt()).apply()
        }

        previewJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val symbol = etSymbol.text.toString().uppercase()
                    if(symbol.isNotEmpty()) {
                        val price = ApiClient.api.getFuturesPrice(symbol).markPrice.toDouble()
                        withContext(Dispatchers.Main) {
                            refreshPreview(price)
                        }
                    }
                } catch (_: Exception) {}
                delay(1000)
            }
        }

        fun updateUIState(isRunning: Boolean) {
            running = isRunning
            if (running) {
                btn.text = "DEACTIVATE MONITOR"
                btn.setBackgroundColor(Color.parseColor("#1E2329")) // Dark
                btn.setTextColor(Color.parseColor("#F6465D")) // Red Text
                btn.strokeColor = ContextCompat.getColorStateList(this, R.color.glass_stroke)
                btn.strokeWidth = 2
                status.text = "• SYSTEM ACTIVE •"
                status.setTextColor(Color.parseColor("#0ECB81"))
            } else {
                btn.text = "ACTIVATE MONITOR"
                btn.setBackgroundColor(Color.parseColor("#F0B90B")) // Yellow
                btn.setTextColor(Color.BLACK)
                status.text = "System Standby"
                status.setTextColor(Color.parseColor("#848E9C"))
            }
        }

        // Check if service is actually running (simple boolean toggle for now)
        // Ideally you check ActivityManager, but for this scope boolean is fine if app stays in memory
        updateUIState(running)

        btn.setOnClickListener {
            if (!running) {
                prefs.edit()
                    .putString("symbol", etSymbol.text.toString().uppercase())
                    .apply()

                requestPermissionAndStart()
                updateUIState(true)
            } else {
                stopService(Intent(this, PriceService::class.java))
                updateUIState(false)
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
                    val adapter = ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_dropdown_item_1line, // Keep simple layout for dropdown
                        symbols
                    )
                    et.setAdapter(adapter)
                    symbolsLoaded = true
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

    override fun onDestroy() {
        previewJob?.cancel()
        super.onDestroy()
    }
}

