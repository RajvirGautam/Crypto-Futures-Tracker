package com.rajvir.FuturesTracker

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import android.Manifest
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class HomeFragment : Fragment() {

    private enum class AlarmMode { PRICE, PERCENT }

    private lateinit var prefs: SharedPreferences
    private lateinit var rvCoins: RecyclerView
    private lateinit var coinAdapter: CoinAdapter
    private lateinit var etSearch: AutoCompleteTextView
    private lateinit var searchDropdownAdapter: SearchDropdownAdapter
    private lateinit var tvSearchedCoinName: TextView
    private lateinit var tvSearchedPrice: TextView
    private lateinit var tvSearchedChange: TextView
    private lateinit var tvNotifIntervalHint: TextView
    private lateinit var tvNotifButtonText: TextView
    private lateinit var ivNotifBell: ImageView
    private lateinit var topSection: LinearLayout
    private lateinit var btnNotifications: LinearLayout
    private lateinit var tabAlarmMode: TabLayout
    private lateinit var tvAlarmCurrentPrice: TextView
    private lateinit var seekAlarmTarget: SeekBar
    private lateinit var etAlarmTarget: EditText
    private lateinit var swAlarmEnabled: SwitchCompat
    private lateinit var tvAlarmSummary: TextView

    private var pricesJob: Job? = null
    private var searchJob: Job? = null
    private var currentSearchSymbol = "BTCUSDT"
    private var isNotificationActive = false
    private var suppressSearchRequest = false
    private var suppressAlarmInputUpdate = false
    private var lastSearchedPrice = 0.0
    private var alarmMode = AlarmMode.PRICE
    private var alarmTargetPrice = 0.0
    private var alarmPercent = 1.0
    private var alarmBasePrice = 0.0
    private var sliderMinPrice = 0.0
    private var sliderMaxPrice = 0.0

    private val US = DecimalFormatSymbols(Locale.US)
    private val priceFmt = DecimalFormat("#,##0.00", US)
    private val pctFmt = DecimalFormat("0.00", US)

    private val coinNameMap = mapOf(
        "BTC" to "Bitcoin", "ETH" to "Ethereum", "BNB" to "BNB",
        "SOL" to "Solana", "XRP" to "XRP", "ADA" to "Cardano",
        "DOGE" to "Dogecoin", "AVAX" to "Avalanche", "DOT" to "Polkadot",
        "MATIC" to "Polygon", "POL" to "Polygon", "LINK" to "Chainlink",
        "LTC" to "Litecoin", "UNI" to "Uniswap", "ATOM" to "Cosmos",
        "SUI" to "Sui", "ARB" to "Arbitrum", "OP" to "Optimism",
        "NEAR" to "NEAR Protocol", "FIL" to "Filecoin"
    )

    private val topCoins = mutableListOf(
        CoinItem("BTCUSDT",  "Bitcoin",   "", 0.0, 0.0, 0.0),
        CoinItem("ETHUSDT",  "Ethereum",  "", 0.0, 0.0, 0.0),
        CoinItem("BNBUSDT",  "BNB",       "", 0.0, 0.0, 0.0),
        CoinItem("SOLUSDT",  "Solana",    "", 0.0, 0.0, 0.0),
        CoinItem("XRPUSDT",  "XRP",       "", 0.0, 0.0, 0.0),
        CoinItem("DOGEUSDT", "Dogecoin",  "", 0.0, 0.0, 0.0),
        CoinItem("ADAUSDT",  "Cardano",   "", 0.0, 0.0, 0.0),
        CoinItem("AVAXUSDT", "Avalanche", "", 0.0, 0.0, 0.0),
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireActivity().getSharedPreferences("crypto_prefs", android.content.Context.MODE_PRIVATE)
        isNotificationActive = prefs.getBoolean("notif_tracker_active", false)

        topSection       = view.findViewById(R.id.topSection)
        etSearch         = view.findViewById(R.id.etSearch)
        tvSearchedCoinName  = view.findViewById(R.id.tvSearchedCoinName)
        tvSearchedPrice  = view.findViewById(R.id.tvSearchedPrice)
        tvSearchedChange = view.findViewById(R.id.tvSearchedChange)
        btnNotifications = view.findViewById(R.id.btnActivateNotifications)
        tvNotifIntervalHint = view.findViewById(R.id.tvNotifIntervalHint)
        tvNotifButtonText   = view.findViewById(R.id.tvNotifButtonText)
        ivNotifBell      = view.findViewById(R.id.ivNotifBell)
        tabAlarmMode = view.findViewById(R.id.tabAlarmMode)
        tvAlarmCurrentPrice = view.findViewById(R.id.tvAlarmCurrentPrice)
        seekAlarmTarget = view.findViewById(R.id.seekAlarmTarget)
        etAlarmTarget = view.findViewById(R.id.etAlarmTarget)
        swAlarmEnabled = view.findViewById(R.id.swAlarmRepeat)
        tvAlarmSummary = view.findViewById(R.id.tvAlarmSummary)

        searchDropdownAdapter = SearchDropdownAdapter(requireContext(), topCoins.toMutableList())
        etSearch.setAdapter(searchDropdownAdapter)
        etSearch.threshold = 1

        loadAlarmPrefs()
        setupAlarmControls()

        topSection.post { applyMeshGradient() }

        setupRecyclerView(view)
        setupSearch()
        loadSearchSymbols()
        updateNotificationButtonUI()

        btnNotifications.setOnClickListener { toggleNotificationTracker() }
        startPricesPolling()
    }

    private fun applyMeshGradient() {
        val w = topSection.width.toFloat()
        val h = topSection.height.toFloat()
        if (w == 0f || h == 0f) return

        val bitmap = Bitmap.createBitmap(topSection.width, topSection.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#090D07"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            w * 0.5f, h * 0.38f, w * 0.85f,
            intArrayOf(Color.parseColor("#233318"), Color.parseColor("#162210"), Color.parseColor("#090D07")),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)

        val paint2 = Paint(Paint.ANTI_ALIAS_FLAG)
        paint2.shader = RadialGradient(
            w * 0.72f, h * 0.15f, w * 0.45f,
            intArrayOf(Color.parseColor("#1A2B10"), Color.parseColor("#090D07")),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint2)
        topSection.background = BitmapDrawable(resources, bitmap)
    }

    private fun setupRecyclerView(view: View) {
        rvCoins = view.findViewById(R.id.rvCoins)
        rvCoins.layoutManager = LinearLayoutManager(requireContext())
        coinAdapter = CoinAdapter { symbol ->
            currentSearchSymbol = symbol
            val ticker = symbol.removeSuffix("USDT")
            tvSearchedCoinName.text = coinNameMap[ticker] ?: ticker
            etSearch.setText(ticker)
        }
        rvCoins.adapter = coinAdapter
        coinAdapter.submitList(topCoins.toList())
    }

    private fun setupSearch() {
        etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && etSearch.text?.isNotEmpty() == true) etSearch.showDropDown()
        }
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (suppressSearchRequest) return
                val raw = s.toString().trim().uppercase()
                if (raw.isEmpty()) {
                    currentSearchSymbol = "BTCUSDT"
                    tvSearchedCoinName.text = "Bitcoin"
                    etSearch.dismissDropDown()
                    return
                }
                etSearch.showDropDown()
                searchJob?.cancel()
                searchJob = CoroutineScope(Dispatchers.Main).launch {
                    delay(400)
                    val symbol = if (raw.endsWith("USDT")) raw else "${raw}USDT"
                    currentSearchSymbol = symbol
                    val ticker = symbol.removeSuffix("USDT")
                    tvSearchedCoinName.text = coinNameMap[ticker] ?: ticker
                    persistAlarmSymbolIfNeeded()
                    fetchSearchedCoinPrice(symbol)
                }
            }
        })
        etSearch.setOnItemClickListener { _, _, position, _ ->
            val selected = searchDropdownAdapter.getItem(position) ?: return@setOnItemClickListener
            suppressSearchRequest = true
            etSearch.setText(selected.symbol.removeSuffix("USDT"), false)
            suppressSearchRequest = false
            etSearch.dismissDropDown()
            currentSearchSymbol = selected.symbol
            tvSearchedCoinName.text = selected.name
            persistAlarmSymbolIfNeeded()
            CoroutineScope(Dispatchers.Main).launch { fetchSearchedCoinPrice(selected.symbol) }
        }
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val imm = requireActivity().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
                etSearch.dismissDropDown()
                true
            } else false
        }
        topSection.setOnClickListener { etSearch.dismissDropDown() }
    }

    private fun loadSearchSymbols() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val info = ApiClient.api.getExchangeInfo()
                val allTradable = info.symbols.asSequence()
                    .filter { it.contractType == "PERPETUAL" && it.status == "TRADING" }
                    .map { symbolInfo ->
                        val symbol = symbolInfo.symbol
                        val ticker = symbol.removeSuffix("USDT")
                        CoinItem(symbol, coinNameMap[ticker] ?: ticker, "", 0.0, 0.0, 0.0)
                    }
                    .distinctBy { it.symbol }.sortedBy { it.symbol }.toList()
                withContext(Dispatchers.Main) {
                    if (allTradable.isNotEmpty()) searchDropdownAdapter.updateData(allTradable)
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun fetchSearchedCoinPrice(symbol: String) {
        try {
            val ticker = withContext(Dispatchers.IO) { ApiClient.api.get24hTicker(symbol) }
            updateSearchDisplay(ticker)
        } catch (e: Exception) {
            tvSearchedPrice.text = "—"
            tvSearchedChange.text = "Coin not found"
            tvSearchedChange.setTextColor(Color.parseColor("#848E9C"))
        }
    }

    private fun updateSearchDisplay(ticker: FuturesTicker24h) {
        val price = ticker.lastPrice?.toDoubleOrNull() ?: 0.0
        val changePct = ticker.priceChangePercent?.toDoubleOrNull() ?: 0.0
        val changeRaw = ticker.priceChange?.toDoubleOrNull() ?: 0.0
        tvSearchedPrice.text = formatPrice(price)
        val sign = if (changePct >= 0) "+" else ""
        val rawSign = if (changeRaw >= 0) "+" else ""
        tvSearchedChange.text = "$sign${pctFmt.format(changePct)}%  ($rawSign${formatPrice(changeRaw)})"
        tvSearchedChange.setTextColor(
            if (changePct >= 0) Color.parseColor("#0ECB81") else Color.parseColor("#F6465D")
        )
        onLivePriceUpdated(price)
    }

    private fun loadAlarmPrefs() {
        alarmMode = when (prefs.getString(MainActivity.PREF_ALARM_MODE, "price")) {
            "percent" -> AlarmMode.PERCENT
            else -> AlarmMode.PRICE
        }
        alarmTargetPrice = prefs.getFloat(MainActivity.PREF_ALARM_TARGET_PRICE, 0f).toDouble()
        alarmPercent = prefs.getFloat(MainActivity.PREF_ALARM_PERCENT, 1f).toDouble()
        alarmBasePrice = prefs.getFloat(MainActivity.PREF_ALARM_BASE_PRICE, 0f).toDouble()
    }

    private fun setupAlarmControls() {
        tabAlarmMode.selectTab(tabAlarmMode.getTabAt(if (alarmMode == AlarmMode.PRICE) 0 else 1))
        etAlarmTarget.hint = if (alarmMode == AlarmMode.PRICE) "Target price" else "Target %"

        tabAlarmMode.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                alarmMode = if (tab.position == 0) AlarmMode.PRICE else AlarmMode.PERCENT
                prefs.edit().putString(
                    MainActivity.PREF_ALARM_MODE,
                    if (alarmMode == AlarmMode.PRICE) "price" else "percent"
                ).apply()
                if (alarmMode == AlarmMode.PERCENT && lastSearchedPrice > 0) {
                    alarmBasePrice = lastSearchedPrice
                    prefs.edit().putFloat(MainActivity.PREF_ALARM_BASE_PRICE, alarmBasePrice.toFloat()).apply()
                }
                syncAlarmInputsWithState()
                saveAlarmConfigAndResetTrigger()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        seekAlarmTarget.max = 1000
        seekAlarmTarget.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                if (alarmMode == AlarmMode.PRICE) {
                    val ratio = progress / seekAlarmTarget.max.toDouble()
                    alarmTargetPrice = (sliderMinPrice + (sliderMaxPrice - sliderMinPrice) * ratio).coerceAtLeast(0.0)
                } else {
                    alarmPercent = -20.0 + 40.0 * (progress / seekAlarmTarget.max.toDouble())
                }
                syncAlarmInputsWithState()
                saveAlarmConfigAndResetTrigger()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        etAlarmTarget.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (suppressAlarmInputUpdate) return
                val value = s?.toString()?.trim()?.toDoubleOrNull() ?: return
                if (alarmMode == AlarmMode.PRICE) {
                    alarmTargetPrice = value.coerceAtLeast(0.0)
                } else {
                    alarmPercent = value.coerceIn(-99.0, 99.0)
                }
                syncAlarmInputsWithState()
                saveAlarmConfigAndResetTrigger()
            }
        })

        swAlarmEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(MainActivity.PREF_ALARM_ENABLED, isChecked)
                .putBoolean(MainActivity.PREF_ALARM_TRIGGERED, false)
                .putLong(MainActivity.PREF_ALARM_LAST_TRIGGER_AT, 0L)
                .apply()
            if (isChecked) {
                if (alarmMode == AlarmMode.PERCENT && lastSearchedPrice > 0) {
                    alarmBasePrice = lastSearchedPrice
                    prefs.edit().putFloat(MainActivity.PREF_ALARM_BASE_PRICE, alarmBasePrice.toFloat()).apply()
                }
                persistAlarmSymbolIfNeeded()
                ensureServiceRunningForAlarm()
            }
            syncAlarmSummary()
        }

        swAlarmEnabled.isChecked = prefs.getBoolean(MainActivity.PREF_ALARM_ENABLED, false)
        syncAlarmInputsWithState()
    }

    private fun onLivePriceUpdated(price: Double) {
        lastSearchedPrice = price
        tvAlarmCurrentPrice.text = "Current: ${formatPrice(price)}"
        if (alarmMode == AlarmMode.PRICE) {
            val pad = (price * 0.15).coerceAtLeast(0.01)
            sliderMinPrice = (price - pad).coerceAtLeast(0.0)
            sliderMaxPrice = (price + pad).coerceAtLeast(sliderMinPrice + 0.01)
            if (alarmTargetPrice <= 0.0) alarmTargetPrice = price
        } else if (alarmBasePrice <= 0.0) {
            alarmBasePrice = price
            prefs.edit().putFloat(MainActivity.PREF_ALARM_BASE_PRICE, alarmBasePrice.toFloat()).apply()
        }
        syncAlarmInputsWithState()
        persistAlarmSymbolIfNeeded()
    }

    private fun syncAlarmInputsWithState() {
        etAlarmTarget.hint = if (alarmMode == AlarmMode.PRICE) "Target price" else "Target %"
        suppressAlarmInputUpdate = true
        if (alarmMode == AlarmMode.PRICE) {
            val clamped = alarmTargetPrice.coerceIn(sliderMinPrice, sliderMaxPrice)
            val progress = if (sliderMaxPrice > sliderMinPrice) {
                (((clamped - sliderMinPrice) / (sliderMaxPrice - sliderMinPrice)) * seekAlarmTarget.max)
                    .toInt()
                    .coerceIn(0, seekAlarmTarget.max)
            } else {
                seekAlarmTarget.max / 2
            }
            seekAlarmTarget.progress = progress
            etAlarmTarget.setText(DecimalFormat("0.########", US).format(alarmTargetPrice.coerceAtLeast(0.0)))
        } else {
            val progress = (((alarmPercent + 20.0) / 40.0) * seekAlarmTarget.max)
                .toInt()
                .coerceIn(0, seekAlarmTarget.max)
            seekAlarmTarget.progress = progress
            etAlarmTarget.setText(DecimalFormat("0.####", US).format(alarmPercent))
        }
        etAlarmTarget.setSelection(etAlarmTarget.text?.length ?: 0)
        suppressAlarmInputUpdate = false
        syncAlarmSummary()
    }

    private fun syncAlarmSummary() {
        val enabled = prefs.getBoolean(MainActivity.PREF_ALARM_ENABLED, false)
        if (!enabled) {
            tvAlarmSummary.text = "Alarm off"
            tvAlarmSummary.setTextColor(Color.parseColor("#8FA881"))
            return
        }
        val targetText = if (alarmMode == AlarmMode.PRICE) {
            "touches ${formatPrice(alarmTargetPrice)}"
        } else {
            val symbol = prefs.getString(MainActivity.PREF_ALARM_SYMBOL, currentSearchSymbol).orEmpty()
            val targetPrice = alarmBasePrice * (1.0 + alarmPercent / 100.0)
            "${symbol.removeSuffix("USDT")} ${if (alarmPercent >= 0) "+" else ""}${pctFmt.format(alarmPercent)}% (${formatPrice(targetPrice)})"
        }
        tvAlarmSummary.text = "Alarm active: $targetText"
        tvAlarmSummary.setTextColor(Color.parseColor("#D7E8CA"))
    }

    private fun saveAlarmConfigAndResetTrigger() {
        prefs.edit()
            .putString(MainActivity.PREF_ALARM_MODE, if (alarmMode == AlarmMode.PRICE) "price" else "percent")
            .putFloat(MainActivity.PREF_ALARM_TARGET_PRICE, alarmTargetPrice.toFloat())
            .putFloat(MainActivity.PREF_ALARM_PERCENT, alarmPercent.toFloat())
            .putFloat(MainActivity.PREF_ALARM_BASE_PRICE, alarmBasePrice.toFloat())
            .putBoolean(MainActivity.PREF_ALARM_TRIGGERED, false)
            .putLong(MainActivity.PREF_ALARM_LAST_TRIGGER_AT, 0L)
            .apply()
        syncAlarmSummary()
    }

    private fun persistAlarmSymbolIfNeeded() {
        prefs.edit().putString(MainActivity.PREF_ALARM_SYMBOL, currentSearchSymbol).apply()
    }

    private fun ensureServiceRunningForAlarm() {
        val notifGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!notifGranted) return
        ContextCompat.startForegroundService(requireContext(), Intent(requireContext(), PriceService::class.java))
    }

    private fun formatPrice(price: Double): String = when {
        price >= 1000 -> "\$${DecimalFormat("#,##0.00", US).format(price)}"
        price >= 1    -> "\$${DecimalFormat("0.00##", US).format(price)}"
        price > 0     -> "\$${DecimalFormat("0.00######", US).format(price)}"
        else          -> "\$0.00"
    }

    private fun startPricesPolling() {
        pricesJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val updatedCoins = topCoins.map { coin ->
                        try {
                            val t = ApiClient.api.get24hTicker(coin.symbol)
                            coin.copy(
                                price = t.lastPrice?.toDoubleOrNull() ?: 0.0,
                                priceChangeRaw = t.priceChange?.toDoubleOrNull() ?: 0.0,
                                priceChangePercent = t.priceChangePercent?.toDoubleOrNull() ?: 0.0
                            )
                        } catch (e: Exception) { coin }
                    }
                    val searchTicker = try { ApiClient.api.get24hTicker(currentSearchSymbol) } catch (e: Exception) { null }
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            coinAdapter.submitList(updatedCoins)
                            searchTicker?.let { updateSearchDisplay(it) }
                        }
                    }
                } catch (e: Exception) {}
                delay(3000)
            }
        }
    }

    private fun requestPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        } else {
            ContextCompat.startForegroundService(requireContext(), Intent(requireContext(), PriceService::class.java))
            isNotificationActive = true
            prefs.edit().putBoolean("notif_tracker_active", true).apply()
            updateNotificationButtonUI()
        }
    }

    private fun updateNotificationButtonUI() {
        val interval = UpdateIntervals.byLabel(prefs.getString(UpdateIntervals.NOTIF_INTERVAL_KEY, "1s"))
        tvNotifIntervalHint.text = "Notification interval: ${interval.label}"
        if (isNotificationActive) {
            btnNotifications.setBackgroundResource(R.drawable.bg_notify_btn_active)
            tvNotifButtonText.text = "Deactivate Notif Tracker"
            tvNotifButtonText.setTextColor(Color.WHITE)
            ivNotifBell.setColorFilter(Color.WHITE)
            tvNotifIntervalHint.setTextColor(Color.parseColor("#FFB4B4"))
        } else {
            btnNotifications.setBackgroundResource(R.drawable.bg_notify_btn)
            tvNotifButtonText.text = "Activate Notification Tracker"
            tvNotifButtonText.setTextColor(Color.parseColor("#1A1A1A"))
            ivNotifBell.clearColorFilter()
            tvNotifIntervalHint.setTextColor(Color.parseColor("#9AB58A"))
        }
    }

    private fun toggleNotificationTracker() {
        if (isNotificationActive) {
            if (!WidgetUpdater.hasAnyWidgets(requireContext())) {
                requireContext().stopService(Intent(requireContext(), PriceService::class.java))
            }
            isNotificationActive = false
            prefs.edit().putBoolean("notif_tracker_active", false).apply()
            updateNotificationButtonUI()
        } else {
            showIntervalPicker(UpdateIntervals.NOTIF_INTERVAL_KEY, "1s") { requestPermissionAndStart() }
        }
    }

    private fun showIntervalPicker(prefKey: String, defaultLabel: String, onSelected: () -> Unit) {
        val labels = UpdateIntervals.labels()
        var selectedLabel = UpdateIntervals.byLabel(prefs.getString(prefKey, defaultLabel)).label
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_interval_selector, null)
        val tvHeader = view.findViewById<TextView>(R.id.tvIntervalHeader)
        val listView = view.findViewById<ListView>(R.id.lvIntervalOptions)
        fun updateHeader() { tvHeader.text = "Notification interval: $selectedLabel" }
        updateHeader()
        listView.adapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, labels) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val row = super.getView(position, convertView, parent) as TextView
                val label = getItem(position).orEmpty()
                row.text = label
                row.textSize = 16f
                row.setPadding(42, 34, 42, 34)
                if (label == selectedLabel) {
                    row.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_interval_option_selected)
                    row.setTextColor(Color.WHITE)
                } else {
                    row.setBackgroundColor(Color.parseColor("#00000000"))
                    row.setTextColor(Color.parseColor("#2A2353"))
                }
                return row
            }
        }
        listView.setOnItemClickListener { _, _, position, _ ->
            selectedLabel = labels[position]
            prefs.edit().putString(prefKey, selectedLabel).apply()
            updateNotificationButtonUI()
            dialog.dismiss()
            onSelected()
        }
        view.findViewById<LinearLayout>(R.id.intervalHeader).setOnClickListener {
            listView.smoothScrollToPosition(labels.indexOf(selectedLabel).coerceAtLeast(0))
        }
        dialog.setContentView(view)
        dialog.show()
    }

    override fun onDestroyView() {
        pricesJob?.cancel()
        searchJob?.cancel()
        super.onDestroyView()
    }
}
