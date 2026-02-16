package com.rajvir.FuturesTracker

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class HomeFragment : Fragment() {

    companion object {
        const val PREF_ALARM_LIST_JSON = "alarm_list_json"
        private const val PREF_HOME_AUTO_REFRESH = "home_auto_refresh"
        private const val PREF_HOME_REFRESH_INTERVAL = "home_refresh_interval"
        private const val PREF_HOME_SORT_MODE = "home_sort_mode"
    }

    private enum class AlarmMode { PRICE, PERCENT }
    private enum class AlarmDirection { EITHER, ABOVE, BELOW }
    private enum class SortMode { CHANGE_DESC, PRICE_DESC, SYMBOL_ASC }

    data class AlarmEntry(
        val id: String,
        val symbol: String,
        val mode: String,
        val targetPrice: Double,
        val basePrice: Double,
        val percent: Double,
        val direction: String,
        val repeat: Boolean,
        val cooldownMin: Int,
        val lastTriggerAt: Long,
        val createdAt: Long
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id)
            put("symbol", symbol)
            put("mode", mode)
            put("targetPrice", targetPrice)
            put("basePrice", basePrice)
            put("percent", percent)
            put("direction", direction)
            put("repeat", repeat)
            put("cooldownMin", cooldownMin)
            put("lastTriggerAt", lastTriggerAt)
            put("createdAt", createdAt)
        }

        companion object {
            fun fromJson(obj: JSONObject): AlarmEntry = AlarmEntry(
                id = obj.optString("id", UUID.randomUUID().toString()),
                symbol = obj.optString("symbol", "BTCUSDT"),
                mode = obj.optString("mode", "price"),
                targetPrice = obj.optDouble("targetPrice", 0.0),
                basePrice = obj.optDouble("basePrice", 0.0),
                percent = obj.optDouble("percent", 0.0),
                direction = obj.optString("direction", "either"),
                repeat = obj.optBoolean("repeat", false),
                cooldownMin = obj.optInt("cooldownMin", 5),
                lastTriggerAt = obj.optLong("lastTriggerAt", 0L),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }

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
    private lateinit var tabAlarmDirection: TabLayout
    private lateinit var tvAlarmCurrentPrice: TextView
    private lateinit var etAlarmTarget: EditText
    private lateinit var seekAlarmTarget: SeekBar
    private lateinit var btnSetAlarm: TextView
    private lateinit var swAlarmRepeat: SwitchCompat
    private lateinit var etAlarmCooldownMin: EditText
    private lateinit var tvPresetNeg5: TextView
    private lateinit var tvPresetNeg3: TextView
    private lateinit var tvPresetNeg1: TextView
    private lateinit var tvPresetPos1: TextView
    private lateinit var tvPresetPos3: TextView
    private lateinit var tvPresetPos5: TextView
    private lateinit var tvAlarmSummary: TextView
    private lateinit var alarmListContainer: LinearLayout

    private lateinit var tvLastUpdated: TextView
    private lateinit var swAutoRefresh: SwitchCompat
    private lateinit var tvRefreshIntervalSelector: TextView
    private lateinit var btnManualRefresh: TextView
    private lateinit var tvSortModeSelector: TextView
    private lateinit var tvTopMoverInsight: TextView

    private var pricesJob: Job? = null
    private var searchJob: Job? = null
    private var isFetchingNow = false
    private var currentSearchSymbol = "BTCUSDT"
    private var isNotificationActive = false
    private var suppressSearchRequest = false
    private var suppressAlarmInputUpdate = false

    private var alarmMode = AlarmMode.PRICE
    private var alarmDirection = AlarmDirection.EITHER
    private var alarmRepeat = false
    private var alarmCooldownMin = 5
    private var alarmTargetPrice = 0.0
    private var alarmBasePrice = 0.0
    private var alarmPercent = 0.0
    private var lastSearchedPrice = 0.0
    private var sliderMinPrice = 0.0
    private var sliderMaxPrice = 0.0

    private var sortMode = SortMode.CHANGE_DESC
    private var pollingIntervalMs = 3000L
    private var autoRefreshEnabled = true

    private val us = DecimalFormatSymbols(Locale.US)
    private val pctFmt = DecimalFormat("0.00", us)
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

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
        CoinItem("BTCUSDT", "Bitcoin", "", 0.0, 0.0, 0.0),
        CoinItem("ETHUSDT", "Ethereum", "", 0.0, 0.0, 0.0),
        CoinItem("BNBUSDT", "BNB", "", 0.0, 0.0, 0.0),
        CoinItem("SOLUSDT", "Solana", "", 0.0, 0.0, 0.0),
        CoinItem("XRPUSDT", "XRP", "", 0.0, 0.0, 0.0),
        CoinItem("DOGEUSDT", "Dogecoin", "", 0.0, 0.0, 0.0),
        CoinItem("ADAUSDT", "Cardano", "", 0.0, 0.0, 0.0),
        CoinItem("AVAXUSDT", "Avalanche", "", 0.0, 0.0, 0.0)
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireActivity().getSharedPreferences("crypto_prefs", Context.MODE_PRIVATE)
        isNotificationActive = prefs.getBoolean("notif_tracker_active", false)

        topSection = view.findViewById(R.id.topSection)
        etSearch = view.findViewById(R.id.etSearch)
        tvSearchedCoinName = view.findViewById(R.id.tvSearchedCoinName)
        tvSearchedPrice = view.findViewById(R.id.tvSearchedPrice)
        tvSearchedChange = view.findViewById(R.id.tvSearchedChange)
        btnNotifications = view.findViewById(R.id.btnActivateNotifications)
        tvNotifIntervalHint = view.findViewById(R.id.tvNotifIntervalHint)
        tvNotifButtonText = view.findViewById(R.id.tvNotifButtonText)
        ivNotifBell = view.findViewById(R.id.ivNotifBell)

        tabAlarmMode = view.findViewById(R.id.tabAlarmMode)
        tabAlarmDirection = view.findViewById(R.id.tabAlarmDirection)
        tvAlarmCurrentPrice = view.findViewById(R.id.tvAlarmCurrentPrice)
        etAlarmTarget = view.findViewById(R.id.etAlarmTarget)
        seekAlarmTarget = view.findViewById(R.id.seekAlarmTarget)
        btnSetAlarm = view.findViewById(R.id.btnSetAlarm)
        swAlarmRepeat = view.findViewById(R.id.swAlarmRepeat)
        etAlarmCooldownMin = view.findViewById(R.id.etAlarmCooldownMin)
        tvPresetNeg5 = view.findViewById(R.id.tvPresetNeg5)
        tvPresetNeg3 = view.findViewById(R.id.tvPresetNeg3)
        tvPresetNeg1 = view.findViewById(R.id.tvPresetNeg1)
        tvPresetPos1 = view.findViewById(R.id.tvPresetPos1)
        tvPresetPos3 = view.findViewById(R.id.tvPresetPos3)
        tvPresetPos5 = view.findViewById(R.id.tvPresetPos5)
        tvAlarmSummary = view.findViewById(R.id.tvAlarmSummary)
        alarmListContainer = view.findViewById(R.id.alarmListContainer)

        tvLastUpdated = view.findViewById(R.id.tvLastUpdated)
        swAutoRefresh = view.findViewById(R.id.swAutoRefresh)
        tvRefreshIntervalSelector = view.findViewById(R.id.tvRefreshIntervalSelector)
        btnManualRefresh = view.findViewById(R.id.btnManualRefresh)
        tvSortModeSelector = view.findViewById(R.id.tvSortModeSelector)
        tvTopMoverInsight = view.findViewById(R.id.tvTopMoverInsight)

        searchDropdownAdapter = SearchDropdownAdapter(requireContext(), topCoins.toMutableList())
        etSearch.setAdapter(searchDropdownAdapter)
        etSearch.threshold = 1

        loadLocalState()
        setupAlarmUi()
        setupToolsUi()
        setupPriceCopyAction()

        topSection.post { applyMeshGradient() }

        setupRecyclerView(view)
        setupSearch()
        loadSearchSymbols()
        updateNotificationButtonUI()
        renderAlarmList()

        btnNotifications.setOnClickListener { toggleNotificationTracker() }
        startPricesPolling()
    }

    private fun loadLocalState() {
        alarmMode = when (prefs.getString(MainActivity.PREF_ALARM_MODE, "price")) {
            "percent" -> AlarmMode.PERCENT
            else -> AlarmMode.PRICE
        }
        alarmDirection = when (prefs.getString(MainActivity.PREF_ALARM_DIRECTION, "either")) {
            "above" -> AlarmDirection.ABOVE
            "below" -> AlarmDirection.BELOW
            else -> AlarmDirection.EITHER
        }
        alarmRepeat = prefs.getBoolean(MainActivity.PREF_ALARM_REPEAT, false)
        alarmCooldownMin = prefs.getInt(MainActivity.PREF_ALARM_COOLDOWN_MIN, 5).coerceIn(1, 240)
        alarmTargetPrice = prefs.getFloat(MainActivity.PREF_ALARM_TARGET_PRICE, 0f).toDouble()
        alarmBasePrice = prefs.getFloat(MainActivity.PREF_ALARM_BASE_PRICE, 0f).toDouble()
        alarmPercent = prefs.getFloat(MainActivity.PREF_ALARM_PERCENT, 1f).toDouble()

        autoRefreshEnabled = prefs.getBoolean(PREF_HOME_AUTO_REFRESH, true)
        pollingIntervalMs = prefs.getLong(PREF_HOME_REFRESH_INTERVAL, 3000L).coerceIn(1000L, 10000L)
        sortMode = when (prefs.getString(PREF_HOME_SORT_MODE, SortMode.CHANGE_DESC.name)) {
            SortMode.PRICE_DESC.name -> SortMode.PRICE_DESC
            SortMode.SYMBOL_ASC.name -> SortMode.SYMBOL_ASC
            else -> SortMode.CHANGE_DESC
        }
    }

    private fun setupAlarmUi() {
        tabAlarmMode.selectTab(tabAlarmMode.getTabAt(if (alarmMode == AlarmMode.PRICE) 0 else 1))
        tabAlarmDirection.selectTab(tabAlarmDirection.getTabAt(
            when (alarmDirection) {
                AlarmDirection.EITHER -> 0
                AlarmDirection.ABOVE -> 1
                AlarmDirection.BELOW -> 2
            }
        ))
        swAlarmRepeat.isChecked = alarmRepeat
        etAlarmCooldownMin.setText(alarmCooldownMin.toString())

        tabAlarmMode.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                alarmMode = if (tab.position == 0) AlarmMode.PRICE else AlarmMode.PERCENT
                syncAlarmUiState()
                saveAlarmUiState()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        tabAlarmDirection.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                alarmDirection = when (tab.position) {
                    1 -> AlarmDirection.ABOVE
                    2 -> AlarmDirection.BELOW
                    else -> AlarmDirection.EITHER
                }
                saveAlarmUiState()
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
                    alarmPercent = if (alarmBasePrice > 0) ((alarmTargetPrice / alarmBasePrice) - 1.0) * 100.0 else 0.0
                } else {
                    alarmPercent = -10.0 + 20.0 * (progress / seekAlarmTarget.max.toDouble())
                    alarmTargetPrice = (alarmBasePrice * (1.0 + alarmPercent / 100.0)).coerceAtLeast(0.0)
                }
                syncAlarmUiState()
                saveAlarmUiState()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        etAlarmTarget.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (suppressAlarmInputUpdate) return
                val enteredPrice = s?.toString()?.trim()?.toDoubleOrNull() ?: return
                alarmTargetPrice = enteredPrice.coerceAtLeast(0.0)
                if (alarmBasePrice > 0.0) {
                    alarmPercent = ((alarmTargetPrice / alarmBasePrice) - 1.0) * 100.0
                }
                syncAlarmUiState()
                saveAlarmUiState()
            }
        })

        swAlarmRepeat.setOnCheckedChangeListener { _, isChecked ->
            alarmRepeat = isChecked
            saveAlarmUiState()
        }

        etAlarmCooldownMin.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                alarmCooldownMin = s?.toString()?.trim()?.toIntOrNull()?.coerceIn(1, 240) ?: 5
                saveAlarmUiState()
            }
        })

        bindPreset(tvPresetNeg5, -5.0)
        bindPreset(tvPresetNeg3, -3.0)
        bindPreset(tvPresetNeg1, -1.0)
        bindPreset(tvPresetPos1, 1.0)
        bindPreset(tvPresetPos3, 3.0)
        bindPreset(tvPresetPos5, 5.0)

        btnSetAlarm.setOnClickListener { addAlarmFromCurrentState() }

        syncAlarmUiState()
    }

    private fun bindPreset(view: TextView, percent: Double) {
        view.setOnClickListener {
            alarmMode = AlarmMode.PERCENT
            tabAlarmMode.selectTab(tabAlarmMode.getTabAt(1))
            if (alarmBasePrice <= 0) alarmBasePrice = lastSearchedPrice
            alarmPercent = percent
            alarmTargetPrice = (alarmBasePrice * (1.0 + percent / 100.0)).coerceAtLeast(0.0)
            syncAlarmUiState()
            saveAlarmUiState()
        }
    }

    private fun syncAlarmUiState() {
        suppressAlarmInputUpdate = true
        etAlarmTarget.hint = if (alarmMode == AlarmMode.PRICE) "Target price" else "Target price from %"

        if (alarmMode == AlarmMode.PRICE) {
            val clamped = alarmTargetPrice.coerceIn(sliderMinPrice, sliderMaxPrice)
            val progress = if (sliderMaxPrice > sliderMinPrice) {
                (((clamped - sliderMinPrice) / (sliderMaxPrice - sliderMinPrice)) * seekAlarmTarget.max)
                    .toInt().coerceIn(0, seekAlarmTarget.max)
            } else seekAlarmTarget.max / 2
            seekAlarmTarget.progress = progress
        } else {
            val progress = (((alarmPercent + 10.0) / 20.0) * seekAlarmTarget.max)
                .toInt().coerceIn(0, seekAlarmTarget.max)
            seekAlarmTarget.progress = progress
        }

        etAlarmTarget.setText(DecimalFormat("0.########", us).format(alarmTargetPrice.coerceAtLeast(0.0)))
        etAlarmTarget.setSelection(etAlarmTarget.text?.length ?: 0)
        suppressAlarmInputUpdate = false

        val modeText = if (alarmMode == AlarmMode.PRICE) "price" else "%->price"
        tvAlarmSummary.text = "Ready: ${formatPrice(alarmTargetPrice)} ($modeText)"
    }

    private fun addAlarmFromCurrentState() {
        val target = etAlarmTarget.text?.toString()?.trim()?.toDoubleOrNull() ?: alarmTargetPrice
        if (target <= 0) {
            Toast.makeText(requireContext(), "Enter a valid target", Toast.LENGTH_SHORT).show()
            return
        }

        val entry = AlarmEntry(
            id = UUID.randomUUID().toString(),
            symbol = currentSearchSymbol,
            mode = if (alarmMode == AlarmMode.PRICE) "price" else "percent",
            targetPrice = target,
            basePrice = alarmBasePrice,
            percent = if (alarmBasePrice > 0) ((target / alarmBasePrice) - 1.0) * 100.0 else alarmPercent,
            direction = when (alarmDirection) {
                AlarmDirection.EITHER -> "either"
                AlarmDirection.ABOVE -> "above"
                AlarmDirection.BELOW -> "below"
            },
            repeat = alarmRepeat,
            cooldownMin = alarmCooldownMin,
            lastTriggerAt = 0L,
            createdAt = System.currentTimeMillis()
        )

        val alarms = loadAlarmList().toMutableList()
        alarms.add(entry)
        saveAlarmList(alarms)
        prefs.edit().putBoolean(MainActivity.PREF_ALARM_ENABLED, alarms.isNotEmpty()).apply()
        ensureServiceRunningForAlarm()
        renderAlarmList()
        Toast.makeText(requireContext(), "Alarm set", Toast.LENGTH_SHORT).show()
    }

    private fun renderAlarmList() {
        val alarms = loadAlarmList()
        alarmListContainer.removeAllViews()
        if (alarms.isEmpty()) {
            tvAlarmSummary.text = "No alarms yet"
            return
        }

        tvAlarmSummary.text = "${alarms.size} alarm(s) active"

        alarms.forEach { alarm ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8 }
                setPadding(10, 10, 10, 10)
                background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_glass)
            }

            val label = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                val pct = if (alarm.percent >= 0) "+${pctFmt.format(alarm.percent)}" else pctFmt.format(alarm.percent)
                text = "${alarm.symbol.removeSuffix("USDT")}: ${formatPrice(alarm.targetPrice)} ($pct%)"
                setTextColor(Color.WHITE)
                textSize = 12f
            }

            val delete = TextView(requireContext()).apply {
                text = "Delete"
                setTextColor(Color.parseColor("#FFB4B4"))
                textSize = 12f
                setOnClickListener {
                    val updated = loadAlarmList().filterNot { it.id == alarm.id }
                    saveAlarmList(updated)
                    prefs.edit().putBoolean(MainActivity.PREF_ALARM_ENABLED, updated.isNotEmpty()).apply()
                    renderAlarmList()
                }
            }

            row.addView(label)
            row.addView(delete)
            alarmListContainer.addView(row)
        }
    }

    private fun loadAlarmList(): List<AlarmEntry> {
        val raw = prefs.getString(PREF_ALARM_LIST_JSON, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    add(AlarmEntry.fromJson(arr.getJSONObject(i)))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAlarmList(list: List<AlarmEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(PREF_ALARM_LIST_JSON, arr.toString()).apply()
    }

    private fun saveAlarmUiState() {
        prefs.edit()
            .putString(MainActivity.PREF_ALARM_MODE, if (alarmMode == AlarmMode.PRICE) "price" else "percent")
            .putString(
                MainActivity.PREF_ALARM_DIRECTION,
                when (alarmDirection) {
                    AlarmDirection.EITHER -> "either"
                    AlarmDirection.ABOVE -> "above"
                    AlarmDirection.BELOW -> "below"
                }
            )
            .putBoolean(MainActivity.PREF_ALARM_REPEAT, alarmRepeat)
            .putInt(MainActivity.PREF_ALARM_COOLDOWN_MIN, alarmCooldownMin)
            .putFloat(MainActivity.PREF_ALARM_TARGET_PRICE, alarmTargetPrice.toFloat())
            .putFloat(MainActivity.PREF_ALARM_BASE_PRICE, alarmBasePrice.toFloat())
            .putFloat(MainActivity.PREF_ALARM_PERCENT, alarmPercent.toFloat())
            .apply()
    }

    private fun setupToolsUi() {
        swAutoRefresh.isChecked = autoRefreshEnabled
        tvRefreshIntervalSelector.text = "Interval: ${pollingIntervalMs / 1000}s"
        updateSortLabel()

        swAutoRefresh.setOnCheckedChangeListener { _, checked ->
            autoRefreshEnabled = checked
            prefs.edit().putBoolean(PREF_HOME_AUTO_REFRESH, checked).apply()
            if (checked) runSingleRefresh(false)
        }

        tvRefreshIntervalSelector.setOnClickListener {
            val options = listOf(1000L, 3000L, 5000L, 10000L)
            val idx = options.indexOf(pollingIntervalMs)
            pollingIntervalMs = options[(if (idx == -1) 0 else (idx + 1) % options.size)]
            prefs.edit().putLong(PREF_HOME_REFRESH_INTERVAL, pollingIntervalMs).apply()
            tvRefreshIntervalSelector.text = "Interval: ${pollingIntervalMs / 1000}s"
        }

        tvSortModeSelector.setOnClickListener {
            sortMode = when (sortMode) {
                SortMode.CHANGE_DESC -> SortMode.PRICE_DESC
                SortMode.PRICE_DESC -> SortMode.SYMBOL_ASC
                SortMode.SYMBOL_ASC -> SortMode.CHANGE_DESC
            }
            prefs.edit().putString(PREF_HOME_SORT_MODE, sortMode.name).apply()
            updateSortLabel()
            coinAdapter.submitList(sortedCoins(coinAdapter.currentList.toList()))
        }

        btnManualRefresh.setOnClickListener { runSingleRefresh(true) }
    }

    private fun updateSortLabel() {
        tvSortModeSelector.text = when (sortMode) {
            SortMode.CHANGE_DESC -> "Sort: % Change"
            SortMode.PRICE_DESC -> "Sort: Price"
            SortMode.SYMBOL_ASC -> "Sort: Symbol"
        }
    }

    private fun setupPriceCopyAction() {
        tvSearchedPrice.setOnLongClickListener {
            val value = tvSearchedPrice.text?.toString().orEmpty()
            if (value.isBlank() || value == "—") return@setOnLongClickListener true
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("price", value))
            Toast.makeText(requireContext(), "Price copied", Toast.LENGTH_SHORT).show()
            true
        }
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
            persistAlarmSymbolIfNeeded()
        }
        rvCoins.adapter = coinAdapter
        coinAdapter.submitList(sortedCoins(topCoins.toList()))
    }

    private fun sortedCoins(items: List<CoinItem>): List<CoinItem> = when (sortMode) {
        SortMode.CHANGE_DESC -> items.sortedByDescending { it.priceChangePercent }
        SortMode.PRICE_DESC -> items.sortedByDescending { it.price }
        SortMode.SYMBOL_ASC -> items.sortedBy { it.symbol }
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
                val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
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
                    .distinctBy { it.symbol }
                    .sortedBy { it.symbol }
                    .toList()
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
        } catch (_: Exception) {
            tvSearchedPrice.text = "—"
            tvSearchedChange.text = "Coin not found"
            tvSearchedChange.setTextColor(Color.parseColor("#848E9C"))
        }
    }

    private fun updateSearchDisplay(ticker: FuturesTicker24h) {
        val price = ticker.lastPrice?.toDoubleOrNull() ?: 0.0
        val changePct = ticker.priceChangePercent.toDoubleOrNull() ?: 0.0
        val changeRaw = ticker.priceChange?.toDoubleOrNull() ?: 0.0

        tvSearchedPrice.text = formatPrice(price)
        val sign = if (changePct >= 0) "+" else ""
        val rawSign = if (changeRaw >= 0) "+" else ""
        tvSearchedChange.text = "$sign${pctFmt.format(changePct)}%  ($rawSign${formatPrice(changeRaw)})"
        tvSearchedChange.setTextColor(if (changePct >= 0) Color.parseColor("#0ECB81") else Color.parseColor("#F6465D"))

        onLivePriceUpdated(price)
    }

    private fun onLivePriceUpdated(price: Double) {
        lastSearchedPrice = price
        tvAlarmCurrentPrice.text = "Current: ${formatPrice(price)}"

        if (alarmBasePrice <= 0.0) alarmBasePrice = price

        sliderMinPrice = (price * 0.90).coerceAtLeast(0.0)
        sliderMaxPrice = (price * 1.10).coerceAtLeast(sliderMinPrice + 0.01)

        if (alarmTargetPrice <= 0.0) alarmTargetPrice = price
        if (alarmMode == AlarmMode.PERCENT) {
            alarmTargetPrice = (alarmBasePrice * (1.0 + alarmPercent / 100.0)).coerceAtLeast(0.0)
        } else {
            alarmPercent = if (alarmBasePrice > 0) ((alarmTargetPrice / alarmBasePrice) - 1.0) * 100 else 0.0
        }

        syncAlarmUiState()
        persistAlarmSymbolIfNeeded()
    }

    private fun runSingleRefresh(manual: Boolean) {
        if (isFetchingNow) return
        isFetchingNow = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val updatedCoins = topCoins.map { coin ->
                    try {
                        val t = ApiClient.api.get24hTicker(coin.symbol)
                        coin.copy(
                            price = t.lastPrice?.toDoubleOrNull() ?: 0.0,
                            priceChangeRaw = t.priceChange?.toDoubleOrNull() ?: 0.0,
                            priceChangePercent = t.priceChangePercent.toDoubleOrNull() ?: 0.0
                        )
                    } catch (_: Exception) {
                        coin
                    }
                }

                val searchTicker = try { ApiClient.api.get24hTicker(currentSearchSymbol) } catch (_: Exception) { null }

                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    val sorted = sortedCoins(updatedCoins)
                    coinAdapter.submitList(sorted)
                    searchTicker?.let { updateSearchDisplay(it) }
                    updateTopMoverInsight(sorted)
                    tvLastUpdated.text = "Last updated: ${timeFmt.format(Date())}"
                    if (manual) Toast.makeText(requireContext(), "Refreshed", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
            } finally {
                isFetchingNow = false
            }
        }
    }

    private fun updateTopMoverInsight(items: List<CoinItem>) {
        val top = items.maxByOrNull { kotlin.math.abs(it.priceChangePercent) }
        if (top == null) {
            tvTopMoverInsight.text = "Top mover: --"
            return
        }
        val sign = if (top.priceChangePercent >= 0) "+" else ""
        tvTopMoverInsight.text = "Top mover: ${top.symbol.removeSuffix("USDT")} $sign${pctFmt.format(top.priceChangePercent)}%"
    }

    private fun startPricesPolling() {
        pricesJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                if (autoRefreshEnabled) runSingleRefresh(false)
                delay(pollingIntervalMs)
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

        fun updateHeader() {
            tvHeader.text = "Notification interval: $selectedLabel"
        }
        updateHeader()

        listView.adapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, labels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
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

    private fun ensureServiceRunningForAlarm() {
        val notifGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!notifGranted) return
        ContextCompat.startForegroundService(requireContext(), Intent(requireContext(), PriceService::class.java))
    }

    private fun persistAlarmSymbolIfNeeded() {
        prefs.edit().putString(MainActivity.PREF_ALARM_SYMBOL, currentSearchSymbol).apply()
    }

    private fun formatPrice(price: Double): String = when {
        price >= 1000 -> "\$${DecimalFormat("#,##0.00", us).format(price)}"
        price >= 1 -> "\$${DecimalFormat("0.00##", us).format(price)}"
        price > 0 -> "\$${DecimalFormat("0.00######", us).format(price)}"
        else -> "\$0.00"
    }

    override fun onDestroyView() {
        pricesJob?.cancel()
        searchJob?.cancel()
        super.onDestroyView()
    }
}
