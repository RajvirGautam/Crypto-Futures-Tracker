package com.rajvir.FuturesTracker

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChartsFragment : Fragment() {

    private lateinit var chartWebView: WebView
    private lateinit var chipsContainer: LinearLayout
    private var quickSymbols = mutableListOf("BTCUSDT", "XRPUSDT")
    private val prefsName = "charts_prefs"
    private val quickSymbolsKey = "quick_access_symbols"
    private var selectedIndex = 0
    private var symbolSuggestions: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_charts, container, false)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chartWebView = view.findViewById(R.id.chartWebView)
        chartWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        chartWebView.webChromeClient = WebChromeClient()
        chipsContainer = view.findViewById(R.id.symbolChipsContainer)
        loadSymbolSuggestions()

        quickSymbols = loadQuickSymbols()
        if (selectedIndex >= quickSymbols.size) selectedIndex = 0

        renderQuickAccessChips()
        quickSymbols.getOrNull(selectedIndex)?.let { loadChart(it) }
    }

    private fun loadQuickSymbols(): MutableList<String> {
        val prefs = requireContext().getSharedPreferences(prefsName, 0)
        val raw = prefs.getString(quickSymbolsKey, null).orEmpty()
        val saved = raw.split(',')
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .map { normalizeSymbol(it) }
            .distinct()
            .toMutableList()

        if (saved.isEmpty()) {
            return mutableListOf("BTCUSDT", "XRPUSDT")
        }

        // Ensure requested defaults remain easily accessible.
        if (!saved.contains("BTCUSDT")) saved.add(0, "BTCUSDT")
        if (!saved.contains("XRPUSDT")) saved.add(if (saved.isEmpty()) 0 else 1, "XRPUSDT")
        return saved
    }

    private fun saveQuickSymbols() {
        val prefs = requireContext().getSharedPreferences(prefsName, 0)
        prefs.edit().putString(quickSymbolsKey, quickSymbols.joinToString(",")).apply()
    }

    private fun renderQuickAccessChips() {
        chipsContainer.removeAllViews()
        quickSymbols.forEachIndexed { i, symbol ->
            val chip = createChip(toBaseLabel(symbol), i == selectedIndex)
            chip.setOnClickListener {
                selectedIndex = i
                updateChips(chipsContainer)
                loadChart(symbol)
            }
            chip.setOnLongClickListener {
                if (quickSymbols.size <= 1) {
                    true
                } else {
                    quickSymbols.removeAt(i)
                    selectedIndex = selectedIndex.coerceAtMost(quickSymbols.lastIndex)
                    saveQuickSymbols()
                    renderQuickAccessChips()
                    quickSymbols.getOrNull(selectedIndex)?.let { loadChart(it) }
                    true
                }
            }
            chipsContainer.addView(chip)
        }

        val addChip = createChip("+ Add", false)
        addChip.setOnClickListener { showAddQuickCoinDialog() }
        chipsContainer.addView(addChip)

        updateChips(chipsContainer)
    }

    private fun showAddQuickCoinDialog() {
        val input = AutoCompleteTextView(requireContext()).apply {
            hint = "e.g. ADA, DOGE, ETH"
            setSingleLine(true)
            threshold = 1
            val suggestions = if (symbolSuggestions.isNotEmpty()) symbolSuggestions else listOf(
                "BTC", "ETH", "XRP", "SOL", "BNB", "ADA", "DOGE", "AVAX", "LINK"
            )
            setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, suggestions))
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) showDropDown()
            }
            post { showDropDown() }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Add quick coin")
            .setMessage("Enter a coin ticker or pair (e.g. ADA or ADAUSDT).")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val normalized = normalizeSymbol(input.text?.toString().orEmpty())
                if (normalized.isNotEmpty() && !quickSymbols.contains(normalized)) {
                    quickSymbols.add(normalized)
                    selectedIndex = quickSymbols.lastIndex
                    saveQuickSymbols()
                    renderQuickAccessChips()
                    loadChart(normalized)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun normalizeSymbol(raw: String): String {
        val cleaned = raw.uppercase().replace(Regex("[^A-Z0-9]"), "")
        if (cleaned.isEmpty()) return ""
        return if (cleaned.endsWith("USDT") || cleaned.endsWith("USD") || cleaned.endsWith("BTC") || cleaned.endsWith("ETH") || cleaned.endsWith("BNB")) {
            cleaned
        } else {
            "${cleaned}USDT"
        }
    }

    private fun toBaseLabel(symbol: String): String {
        return when {
            symbol.endsWith("USDT") -> symbol.removeSuffix("USDT")
            symbol.endsWith("USD") -> symbol.removeSuffix("USD")
            symbol.endsWith("BTC") -> symbol.removeSuffix("BTC")
            symbol.endsWith("ETH") -> symbol.removeSuffix("ETH")
            symbol.endsWith("BNB") -> symbol.removeSuffix("BNB")
            else -> symbol
        }
    }

    private fun loadChart(binanceSymbol: String) {
        val tvSymbol = "BINANCE:${binanceSymbol}"
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
              html, body { width: 100%; height: 100%; margin: 0; padding: 0; background: #0A0D08; overflow: hidden; }
              .tradingview-widget-container { width: 100%; height: 100%; }
              #tv_chart { width: 100%; height: 100%; }
            </style>
            </head>
            <body>
            <div class="tradingview-widget-container">
              <div id="tv_chart"></div>
              <script type="text/javascript" src="https://s3.tradingview.com/tv.js"></script>
              <script type="text/javascript">
              new TradingView.widget({
                "width": "100%",
                "height": "100%",
                "symbol": "$tvSymbol",
                                "interval": "30",
                "timezone": "Etc/UTC",
                "theme": "dark",
                "style": "1",
                "locale": "en",
                "toolbar_bg": "#0A0D08",
                "enable_publishing": false,
                "hide_side_toolbar": false,
                "allow_symbol_change": true,
                "container_id": "tv_chart",
                "hide_top_toolbar": false,
                "save_image": false
              });
              </script>
            </div>
            </body>
            </html>
        """.trimIndent()
        chartWebView.loadDataWithBaseURL("https://s3.tradingview.com", html, "text/html", "utf-8", null)
    }

    private fun loadSymbolSuggestions() {
        CoroutineScope(Dispatchers.IO).launch {
            val suggestions = try {
                ApiClient.api.getExchangeInfo().symbols
                    .asSequence()
                    .filter { it.contractType == "PERPETUAL" && it.status == "TRADING" }
                    .map { it.symbol }
                    .flatMap { sym ->
                        val base = toBaseLabel(sym)
                        sequenceOf(base, sym)
                    }
                    .distinct()
                    .sorted()
                    .toList()
            } catch (_: Exception) {
                emptyList()
            }

            withContext(Dispatchers.Main) {
                if (isAdded) symbolSuggestions = suggestions
            }
        }
    }

    private fun createChip(label: String, active: Boolean): TextView {
        return TextView(requireContext()).apply {
            text = label
            textSize = 13f
            setTextColor(if (active) Color.WHITE else Color.parseColor("#8A9E7A"))
            setBackgroundResource(if (active) R.drawable.bg_pill_green else R.drawable.bg_glass)
            setPadding(36, 16, 36, 16)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 10, 0)
            layoutParams = lp
        }
    }

    private fun updateChips(container: LinearLayout) {
        val symbolChipCount = quickSymbols.size
        for (i in 0 until symbolChipCount) {
            val chip = container.getChildAt(i) as? TextView ?: continue
            val active = (i == selectedIndex)
            chip.setTextColor(if (active) Color.WHITE else Color.parseColor("#8A9E7A"))
            chip.setBackgroundResource(if (active) R.drawable.bg_pill_green else R.drawable.bg_glass)
        }
    }

    override fun onDestroyView() {
        chartWebView.destroy()
        super.onDestroyView()
    }
}
