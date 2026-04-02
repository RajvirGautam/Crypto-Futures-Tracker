package com.rajvir.FuturesTracker

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class WidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var allSymbols: List<String> = emptyList()
    private lateinit var coinAdapter: CoinConfigAdapter
    private var touchHelper: ItemTouchHelper? = null
    private val maxWidgetCoins = 4

    private var selectedWidgetIntervalLabel = "1m"
    private var selectedGraphTimeframeLabel = "1m"
    private var previewJob: Job? = null
    private var rowPreviewJob: Job? = null
    private var activePopup: PopupWindow? = null

    private lateinit var btnAddCoin: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        setContentView(R.layout.activity_widget_config)

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val savedCoins = WidgetPrefs.getCoins(this, appWidgetId).toMutableList()
        val savedDecimals = WidgetPrefs.getDecimals(this, appWidgetId)
        val savedBorder = WidgetPrefs.getBorderAlpha(this, appWidgetId)
        val savedGraphTimeframe = WidgetPrefs.getGraphTimeframeLabel(this, appWidgetId)
        val savedWidgetInterval = WidgetPrefs.getWidgetUpdateIntervalLabel(this, appWidgetId)
        val savedShade = WidgetPrefs.getShadeGraph(this, appWidgetId)

        selectedWidgetIntervalLabel = UpdateIntervals.byLabel(savedWidgetInterval).label
        selectedGraphTimeframeLabel = GraphTimeframes.byLabel(savedGraphTimeframe).label

        val initialCoins = savedCoins.take(maxWidgetCoins).toMutableList()
        if (initialCoins.isEmpty()) initialCoins.add("")

        coinAdapter = CoinConfigAdapter(
            symbols = allSymbols,
            items = initialCoins,
            touchHelper = { touchHelper },
            onChanged = { updateAddButtonState() }
        )

        val rvCoins = findViewById<RecyclerView>(R.id.rvCoins)
        rvCoins.layoutManager = LinearLayoutManager(this)
        rvCoins.adapter = coinAdapter
        touchHelper = ItemTouchHelper(CoinDragCallback(coinAdapter)).also {
            it.attachToRecyclerView(rvCoins)
        }

        btnAddCoin = findViewById(R.id.btnAddCoin)
        btnAddCoin.setOnClickListener {
            if (coinAdapter.getItems().size < maxWidgetCoins) {
                coinAdapter.addSlot("")
                updateAddButtonState()
            }
        }
        updateAddButtonState()

        val tvDecimals = findViewById<TextView>(R.id.tvDecimalsValue)
        val tvDecimalPreview = findViewById<TextView>(R.id.tvDecimalPricePreview)
        val seekDecimals = findViewById<SeekBar>(R.id.seekDecimals)
        seekDecimals.progress = savedDecimals
        tvDecimals.text = savedDecimals.toString()
        seekDecimals.setOnSeekBarChangeListener(simpleListener { progress ->
            tvDecimals.text = progress.toString()
            renderDecimalPreview(tvDecimalPreview, latestPreviewPrice, progress)
        })
        renderDecimalPreview(tvDecimalPreview, latestPreviewPrice, savedDecimals)

        val tvBorder = findViewById<TextView>(R.id.tvBorderAlphaValue)
        val seekBorder = findViewById<SeekBar>(R.id.seekBorderAlpha)
        seekBorder.progress = savedBorder
        tvBorder.text = "$savedBorder%"
        seekBorder.setOnSeekBarChangeListener(simpleListener { progress ->
            tvBorder.text = "$progress%"
        })

        val intervalLabels = UpdateIntervals.labels()
        val etWidgetInterval = findViewById<AutoCompleteTextView>(R.id.etWidgetInterval)
        etWidgetInterval.keyListener = null
        etWidgetInterval.setText(selectedWidgetIntervalLabel, false)
        etWidgetInterval.setOnClickListener {
            showPopup(etWidgetInterval, intervalLabels, selectedWidgetIntervalLabel) { selectedWidgetIntervalLabel = it }
        }
        etWidgetInterval.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showPopup(etWidgetInterval, intervalLabels, selectedWidgetIntervalLabel) { selectedWidgetIntervalLabel = it }
            }
        }

        val graphLabels = GraphTimeframes.labels()
        val etGraphTimeframe = findViewById<AutoCompleteTextView>(R.id.etGraphTimeframe)
        etGraphTimeframe.keyListener = null
        etGraphTimeframe.setText(selectedGraphTimeframeLabel, false)
        etGraphTimeframe.setOnClickListener {
            showPopup(etGraphTimeframe, graphLabels, selectedGraphTimeframeLabel) { selectedGraphTimeframeLabel = it }
        }
        etGraphTimeframe.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showPopup(etGraphTimeframe, graphLabels, selectedGraphTimeframeLabel) { selectedGraphTimeframeLabel = it }
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val info = ApiClient.api.getExchangeInfo()
                allSymbols = info.symbols
                    .filter { it.contractType == "PERPETUAL" && it.status == "TRADING" }
                    .map { it.symbol }
                    .sorted()

                withContext(Dispatchers.Main) {
                    coinAdapter = CoinConfigAdapter(
                        symbols = allSymbols,
                        items = coinAdapter.getItems().toMutableList(),
                        touchHelper = { touchHelper },
                        onChanged = { updateAddButtonState() }
                    )
                    rvCoins.adapter = coinAdapter
                    touchHelper = ItemTouchHelper(CoinDragCallback(coinAdapter)).also {
                        it.attachToRecyclerView(rvCoins)
                    }
                    updateAddButtonState()
                    startRowPreviewPolling()
                }
            } catch (_: Exception) {
            }
        }

        findViewById<MaterialButton>(R.id.btnWidgetDone).setOnClickListener {
            val coins = coinAdapter.getItems()
                .map { it.uppercase().trim() }
                .filter { it.isNotEmpty() }
                .take(maxWidgetCoins)

            if (coins.isEmpty()) return@setOnClickListener

            val finalCount = coins.size.coerceIn(1, maxWidgetCoins)
            val finalCoins = coins.take(finalCount)

            WidgetPrefs.saveCoinCount(this, appWidgetId, finalCount)
            WidgetPrefs.saveCoins(this, appWidgetId, finalCoins)
            WidgetPrefs.saveDecimals(this, appWidgetId, seekDecimals.progress)
            WidgetPrefs.saveBorderAlpha(this, appWidgetId, seekBorder.progress)
            WidgetPrefs.saveShadeGraph(this, appWidgetId, savedShade)
            WidgetPrefs.saveGraphTimeframeLabel(
                this,
                appWidgetId,
                GraphTimeframes.byLabel(selectedGraphTimeframeLabel).label
            )
            WidgetPrefs.saveWidgetUpdateIntervalLabel(
                this,
                appWidgetId,
                UpdateIntervals.byLabel(selectedWidgetIntervalLabel).label
            )

            val mgr = AppWidgetManager.getInstance(this)
            WidgetUpdater.applyLoadingState(this, mgr, appWidgetId)

            try {
                val svc = Intent(this, PriceService::class.java)
                    .setAction(PriceService.ACTION_FORCE_WIDGET_REFRESH)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(svc)
                } else {
                    startService(svc)
                }
            } catch (_: Exception) {
            }

            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            )
            finish()
        }

        startDecimalPreviewPolling(tvDecimalPreview, seekDecimals)
        startRowPreviewPolling()
    }

    private fun simpleListener(onProgress: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
            onProgress(progress)
        }

        override fun onStartTrackingTouch(sb: SeekBar) {}
        override fun onStopTrackingTouch(sb: SeekBar) {}
    }

    private fun updateAddButtonState() {
        val canAdd = ::coinAdapter.isInitialized && coinAdapter.getItems().size < maxWidgetCoins
        btnAddCoin.isEnabled = canAdd
        btnAddCoin.alpha = if (canAdd) 1f else 0.45f
        btnAddCoin.text = if (canAdd) "+ ADD" else "MAX 4"
    }

    private fun showPopup(
        anchor: AutoCompleteTextView,
        labels: Array<String>,
        selectedLabel: String,
        onSelected: (String) -> Unit
    ) {
        activePopup?.dismiss()

        val listView = ListView(this).apply {
            divider = null
            adapter = object : ArrayAdapter<String>(
                this@WidgetConfigActivity,
                android.R.layout.simple_list_item_1,
                labels
            ) {
                override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                    val view = super.getView(position, convertView, parent) as TextView
                    val label = getItem(position).orEmpty()
                    view.text = label
                    view.setPadding(36, 24, 36, 24)
                    if (label == selectedLabel) {
                        view.setBackgroundColor(Color.parseColor("#F0B90B"))
                        view.setTextColor(Color.parseColor("#0E0E0E"))
                    } else {
                        view.setBackgroundColor(Color.parseColor("#151515"))
                        view.setTextColor(Color.parseColor("#E6E6E6"))
                    }
                    return view
                }
            }

            setOnItemClickListener { _, _, position, _ ->
                val label = labels[position]
                onSelected(label)
                anchor.setText(label, false)
                activePopup?.dismiss()
            }
        }

        val width = anchor.width.coerceAtLeast((220 * resources.displayMetrics.density).toInt())
        activePopup = PopupWindow(
            listView,
            width,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 16f
            isOutsideTouchable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#151515")))
        }

        anchor.post {
            activePopup?.showAsDropDown(anchor, 0, 10)
        }
    }

    private var latestPreviewPrice: Double? = null

    private fun startDecimalPreviewPolling(tvPreview: TextView, seekDec: SeekBar) {
        previewJob?.cancel()
        previewJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val symbol = coinAdapter.getItems().firstOrNull()
                    ?.uppercase()
                    ?.trim()
                    .orEmpty()
                    .ifEmpty { "BTCUSDT" }

                try {
                    val price = ApiClient.api.getFuturesPrice(symbol).markPrice.toDoubleOrNull()
                    latestPreviewPrice = price
                    withContext(Dispatchers.Main) {
                        renderDecimalPreview(tvPreview, price, seekDec.progress)
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        renderDecimalPreview(tvPreview, null, seekDec.progress)
                    }
                }
                delay(1500)
            }
        }
    }

    private fun startRowPreviewPolling() {
        rowPreviewJob?.cancel()
        rowPreviewJob = CoroutineScope(Dispatchers.IO).launch {
            val us = DecimalFormatSymbols(Locale.US)
            val fmt = DecimalFormat("#,##0.00####", us)

            while (isActive) {
                val symbols = coinAdapter.getItems()
                    .map { it.uppercase().trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()

                val previews = mutableMapOf<String, String>()
                for (symbol in symbols) {
                    val value = try {
                        ApiClient.api.getFuturesPrice(symbol).markPrice.toDoubleOrNull()
                    } catch (_: Exception) {
                        null
                    }

                    previews[symbol] = if (value != null) {
                        "Preview: ${fmt.format(value)}"
                    } else {
                        "Preview: --"
                    }
                }

                withContext(Dispatchers.Main) {
                    coinAdapter.updatePreviews(previews)
                }

                delay(1800)
            }
        }
    }

    private fun renderDecimalPreview(tvPreview: TextView, price: Double?, decimals: Int) {
        val value = price ?: run {
            tvPreview.text = "Preview: Loading live price..."
            return
        }

        val fmt = DecimalFormat("#,##0.00000000", DecimalFormatSymbols(Locale.US))
        val formatted = fmt.format(value)
        val dot = formatted.indexOf('.')
        val yellow = Color.parseColor("#F0B90B")
        val dim = Color.parseColor("#7D7D7D")

        tvPreview.text = android.text.SpannableStringBuilder().apply {
            append("Preview: ")
            if (dot < 0) {
                append(formatted)
                return@apply
            }

            val intPart = formatted.substring(0, dot + 1)
            val fracPart = formatted.substring(dot + 1)
            val keep = decimals.coerceIn(0, fracPart.length)

            append(intPart)
            if (keep > 0) append(fracPart.substring(0, keep))
            if (keep < fracPart.length) append(fracPart.substring(keep))
        }
    }

    override fun onDestroy() {
        previewJob?.cancel()
        rowPreviewJob?.cancel()
        activePopup?.dismiss()
        super.onDestroy()
    }
}