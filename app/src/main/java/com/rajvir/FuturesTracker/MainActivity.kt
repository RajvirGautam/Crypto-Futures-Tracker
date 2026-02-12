package com.rajvir.FuturesTracker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var webViewChart: WebView
    private lateinit var rvCoins: RecyclerView
    private lateinit var coinAdapter: CoinAdapter
    private var pricesJob: Job? = null
    private var currentSymbol = "BINANCE:BTCUSDT"
    private var currentInterval = "D"
    private val periodButtons = mutableListOf<TextView>()
    
    // Mock user coins. First one is dynamic based on selected.
    private val portfolioCoins = mutableListOf(
        CoinItem("BTCUSDT", "Bitcoin", "0.2157 BTC", 0.0, 0.0, 0.0),
        CoinItem("SOLUSDT", "Solana", "258.1901 SOL", 0.0, 0.0, 0.0)
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Edge-to-edge handling
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, i ->
            val bars = i.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, 0)
            i
        }

        prefs = getSharedPreferences("crypto_prefs", MODE_PRIVATE)

        // Setup WebView
        webViewChart = findViewById(R.id.webViewChart)
        webViewChart.settings.javaScriptEnabled = true
        webViewChart.settings.domStorageEnabled = true
        webViewChart.settings.loadWithOverviewMode = true
        webViewChart.settings.useWideViewPort = true
        webViewChart.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webViewChart.settings.mediaPlaybackRequiresUserGesture = false
        webViewChart.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        webViewChart.webViewClient = WebViewClient()
        webViewChart.webChromeClient = WebChromeClient()
        
        loadChart("BINANCE:BTCUSDT", "D")
        
        // Setup period selector buttons
        setupPeriodSelectors()

        // Setup RecyclerView
        rvCoins = findViewById(R.id.rvCoins)
        rvCoins.layoutManager = LinearLayoutManager(this)
        coinAdapter = CoinAdapter { symbol ->
            // On coin click, switch chart
            currentSymbol = "BINANCE:${symbol}"
            loadChart(currentSymbol, currentInterval)
        }
        rvCoins.adapter = coinAdapter
        coinAdapter.submitList(portfolioCoins.toList())
        
        // Setup bottom nav actions
        findViewById<android.view.View>(R.id.fabAddTracker).setOnClickListener {
            val bottomSheet = AddTrackerBottomSheet {
                requestPermissionAndStart()
            }
            bottomSheet.show(supportFragmentManager, "AddTrackerBottomSheet")
        }
        
        startPricesPolling()
    }
    
    private fun loadChart(symbol: String, interval: String) {
        currentSymbol = symbol
        currentInterval = interval
        val encodedSymbol = android.net.Uri.encode(symbol)
        val url = "https://s.tradingview.com/widgetembed/?frameElementId=tradingview_chart" +
            "&symbol=$encodedSymbol" +
            "&interval=$interval" +
            "&hidetoptoolbar=0" +
            "&hidelegend=0" +
            "&saveimage=0" +
            "&toolbarbg=F5F5F5" +
            "&theme=light" +
            "&style=1" +
            "&timezone=Etc%2FUTC" +
            "&withdateranges=1" +
            "&showpopupbutton=0" +
            "&studies=MAExp%407%7CMAExp%4025%7CMAExp%4099" +
            "&locale=en" +
            "&utm_source=cryptostatus" +
            "&utm_medium=widget_new" +
            "&utm_campaign=chart" +
            "&allow_symbol_change=0"
        webViewChart.loadUrl(url)
    }
    
    private fun setupPeriodSelectors() {
        val btn1W = findViewById<TextView>(R.id.btnPeriod1W)
        val btn1M = findViewById<TextView>(R.id.btnPeriod1M)
        val btn3M = findViewById<TextView>(R.id.btnPeriod3M)
        val btn6M = findViewById<TextView>(R.id.btnPeriod6M)
        val btn1Y = findViewById<TextView>(R.id.btnPeriod1Y)
        val btnAll = findViewById<TextView>(R.id.btnPeriodAll)
        
        periodButtons.addAll(listOf(btn1W, btn1M, btn3M, btn6M, btn1Y, btnAll))
        
        // Map buttons to TradingView intervals
        val intervalMap = mapOf(
            btn1W to "60",    // 1 hour candles for 1W view
            btn1M to "D",     // Daily candles for 1M view
            btn3M to "D",     // Daily candles for 3M view
            btn6M to "W",     // Weekly candles for 6M view
            btn1Y to "W",     // Weekly candles for 1Y view
            btnAll to "M"     // Monthly candles for ALL view
        )
        
        // Default selection: 1M
        highlightPeriodButton(btn1M)
        
        for ((button, interval) in intervalMap) {
            button.setOnClickListener {
                highlightPeriodButton(button)
                loadChart(currentSymbol, interval)
            }
        }
    }
    
    private fun highlightPeriodButton(selected: TextView) {
        for (btn in periodButtons) {
            if (btn == selected) {
                btn.setTextColor(ContextCompat.getColor(this, R.color.home_text_primary))
                btn.setTypeface(null, Typeface.BOLD)
            } else {
                btn.setTextColor(ContextCompat.getColor(this, R.color.home_text_secondary))
                btn.setTypeface(null, Typeface.NORMAL)
            }
        }
    }

    private fun startPricesPolling() {
        pricesJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val updatedCoins = portfolioCoins.map { coin ->
                        try {
                            val ticker = ApiClient.api.get24hTicker(coin.symbol)
                            val price = ticker.lastPrice?.toDoubleOrNull() ?: 0.0
                            val changeRaw = ticker.priceChange?.toDoubleOrNull() ?: 0.0
                            val changePct = ticker.priceChangePercent?.toDoubleOrNull() ?: 0.0
                            coin.copy(price = price, priceChangeRaw = changeRaw, priceChangePercent = changePct)
                        } catch(e:Exception){
                            coin
                        }
                    }
                    withContext(Dispatchers.Main) {
                        coinAdapter.submitList(updatedCoins)
                    }
                } catch (e: Exception) {}
                delay(2000)
            }
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
        pricesJob?.cancel()
        super.onDestroy()
    }
}
