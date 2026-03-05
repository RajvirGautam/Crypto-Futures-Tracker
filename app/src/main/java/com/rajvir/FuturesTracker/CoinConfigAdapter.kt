package com.rajvir.FuturesTracker

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

/**
 * RecyclerView adapter for the widget coin list in WidgetConfigActivity.
 * Supports drag-to-reorder via [ItemTouchHelper] and add/remove items.
 */
class CoinConfigAdapter(
    private val symbols: List<String>,                  // autocomplete list
    private var items: MutableList<String>,             // current coin order
    private val touchHelper: () -> ItemTouchHelper?,    // injected after setup
    private val onChanged: (List<String>) -> Unit
) : RecyclerView.Adapter<CoinConfigAdapter.VH>() {

    private val previewBySymbol = mutableMapOf<String, String>()

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val etSymbol: AutoCompleteTextView = view.findViewById(R.id.etCoinSymbol)
        val btnRemove: TextView = view.findViewById(R.id.btnRemoveCoin)
        val ivDrag: View = view.findViewById(R.id.ivDragHandle)
        val tvPreview: TextView = view.findViewById(R.id.tvCoinPreview)

        var isBinding = false

        init {
            etSymbol.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (!isBinding && adapterPosition != RecyclerView.NO_POSITION) {
                        val text = s.toString().uppercase().trim()
                        if (items[adapterPosition] != text) {
                            items[adapterPosition] = text
                            // Call onChanged but DON'T notify range changed to avoid focus loss
                            onChanged(items.toList())
                        }
                    }
                }
            })
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_widget_coin, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains("PREVIEW")) {
            val symbol = items[position].uppercase().trim()
            if (symbol.isEmpty()) {
                h.tvPreview.text = ""
            } else {
                val preview = previewBySymbol[symbol]
                h.tvPreview.text = preview ?: "Preview: Loading..."
            }
            return
        }
        super.onBindViewHolder(h, position, payloads)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        // Populate autocomplete
        h.etSymbol.setAdapter(
            ArrayAdapter(h.etSymbol.context, android.R.layout.simple_dropdown_item_1line, symbols)
        )
        h.isBinding = true
        h.etSymbol.setText(items[position], false)
        h.isBinding = false

        val symbol = items[position].uppercase().trim()
        if (symbol.isEmpty()) {
            h.tvPreview.text = ""
        } else {
            val preview = previewBySymbol[symbol]
            h.tvPreview.text = preview ?: "Preview: Loading..."
        }

        // Show/hide remove button: always allow removal if > 1 item
        h.btnRemove.visibility = if (items.size > 1) View.VISIBLE else View.INVISIBLE
        h.btnRemove.setOnClickListener {
            val pos = h.adapterPosition
            if (pos != RecyclerView.NO_POSITION && items.size > 1) {
                items.removeAt(pos)
                notifyItemRemoved(pos)
                notifyItemRangeChanged(pos, items.size)
                onChanged(items.toList())
            }
        }

        // Drag handle
        h.ivDrag.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                touchHelper()?.startDrag(h)
            }
            false
        }
    }

    override fun getItemCount() = items.size

    /** Called by ItemTouchHelper when user drops the dragged item. */
    fun onItemMove(from: Int, to: Int) {
        Collections.swap(items, from, to)
        notifyItemMoved(from, to)
        onChanged(items.toList())
    }

    fun getItems(): List<String> = items.toList()

    /** Add a blank slot (capped at 4). */
    fun addSlot(defaultSymbol: String = "") {
        if (items.size < 4) {
            items.add(defaultSymbol)
            notifyItemInserted(items.size - 1)
            notifyItemRangeChanged(0, items.size) // refresh remove-button visibility
            onChanged(items.toList())
        }
    }

    /** Trim or expand to exactly [n] items. */
    fun setCount(n: Int, defaults: List<String>) {
        while (items.size > n) {
            items.removeAt(items.size - 1)
            notifyItemRemoved(items.size)
        }
        while (items.size < n) {
            items.add(defaults.getOrElse(items.size) { "BTCUSDT" })
            notifyItemInserted(items.size - 1)
        }
        notifyItemRangeChanged(0, items.size)
        onChanged(items.toList())
    }

    fun updatePreviews(previews: Map<String, String>) {
        previewBySymbol.clear()
        previewBySymbol.putAll(previews)
        notifyItemRangeChanged(0, itemCount, "PREVIEW")
    }
}

/** Drag-and-drop callback wired to [CoinConfigAdapter.onItemMove]. */
class CoinDragCallback(private val adapter: CoinConfigAdapter) : ItemTouchHelper.Callback() {
    override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder) =
        makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

    override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
        adapter.onItemMove(from.adapterPosition, to.adapterPosition)
        return true
    }

    override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
    override fun isLongPressDragEnabled() = false  // drag only via handle
}
