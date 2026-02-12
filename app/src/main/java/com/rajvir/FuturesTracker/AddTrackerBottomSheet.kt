package com.rajvir.FuturesTracker

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddTrackerBottomSheet(private val onActivate: () -> Unit) : BottomSheetDialogFragment() {

    private lateinit var prefs: SharedPreferences
    private var previewJob: Job? = null
    private var symbolsLoaded = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_tracker_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        prefs = requireContext().getSharedPreferences("crypto_prefs", Context.MODE_PRIVATE)

        val etSymbol = view.findViewById<AutoCompleteTextView>(R.id.etSymbol)
        val tvPrice = view.findViewById<TextView>(R.id.tvPricePreview)
        val btn = view.findViewById<MaterialButton>(R.id.btnToggle)

        etSymbol.setText(prefs.getString("symbol", "BTCUSDT"))
        
        loadSymbols(etSymbol)

        etSymbol.setOnClickListener {
            if (symbolsLoaded) {
                etSymbol.showDropDown()
            }
        }

        etSymbol.setOnItemClickListener { _, _, _, _ ->
            prefs.edit().putString("symbol", etSymbol.text.toString().uppercase()).apply()
        }

        fun refreshPreview(price: Double) {
            val raw = String.format("%.4f", price)
            val clean = raw.replace(".", "")
            
            val spannable = SpannableString(raw)
            var mapIndex = 0

            for (i in raw.indices) {
                if (raw[i] != '.') {
                    if (mapIndex in 0 until Math.min(3, clean.length)) {
                        spannable.setSpan(
                            ForegroundColorSpan(Color.parseColor("#F0B90B")),
                            i, i + 1,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    } else {
                        spannable.setSpan(
                            ForegroundColorSpan(Color.parseColor("#848E9C")),
                            i, i + 1,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    mapIndex++
                }
            }

            tvPrice.text = spannable
        }

        previewJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val symbol = etSymbol.text.toString().uppercase()
                    if (symbol.isNotEmpty()) {
                        val priceObj = ApiClient.api.getFuturesPrice(symbol)
                        val price = priceObj.markPrice.toDouble()
                        withContext(Dispatchers.Main) {
                            refreshPreview(price)
                        }
                    }
                } catch (_: Exception) {}
                delay(1000)
            }
        }
        
        btn.setOnClickListener {
            prefs.edit().putString("symbol", etSymbol.text.toString().uppercase()).apply()
            onActivate()
            dismiss()
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
                        requireContext(),
                        android.R.layout.simple_dropdown_item_1line,
                        symbols
                    )
                    et.setAdapter(adapter)
                    symbolsLoaded = true
                }
            } catch (_: Exception) {}
        }
    }

    override fun onDestroyView() {
        previewJob?.cancel()
        super.onDestroyView()
    }
}
