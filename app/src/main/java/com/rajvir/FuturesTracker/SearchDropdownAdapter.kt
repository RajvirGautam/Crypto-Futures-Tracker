package com.rajvir.FuturesTracker

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView

class SearchDropdownAdapter(
    context: Context,
    private val items: MutableList<CoinItem>
) : ArrayAdapter<CoinItem>(context, 0, items) {

    private val allItems = mutableListOf<CoinItem>()

    init {
        allItems.addAll(items)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return buildRow(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return buildRow(position, convertView, parent)
    }

    private fun buildRow(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)

        val coin = getItem(position) ?: return view
        val title = view.findViewById<TextView>(android.R.id.text1)
        val subtitle = view.findViewById<TextView>(android.R.id.text2)
        val ticker = coin.symbol.removeSuffix("USDT")

        title.text = coin.name
        title.setTextColor(android.graphics.Color.WHITE)
        title.textSize = 14f

        subtitle.text = "$ticker • ${coin.symbol}"
        subtitle.setTextColor(android.graphics.Color.parseColor("#8AAF6A"))
        subtitle.textSize = 12f

        val horizontal = (16 * context.resources.displayMetrics.density).toInt()
        val vertical = (10 * context.resources.displayMetrics.density).toInt()
        view.setPadding(horizontal, vertical, horizontal, vertical)

        if (position % 2 == 0) {
            view.setBackgroundColor(android.graphics.Color.parseColor("#252C29"))
        } else {
            view.setBackgroundColor(android.graphics.Color.parseColor("#1F2620"))
        }

        return view
    }

    fun updateData(newItems: List<CoinItem>) {
        allItems.clear()
        allItems.addAll(newItems)
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint?.toString()?.trim()?.uppercase().orEmpty()
                val results = if (query.isEmpty()) {
                    allItems.take(12)
                } else {
                    allItems.filter {
                        val symbol = it.symbol.uppercase()
                        val ticker = it.symbol.removeSuffix("USDT").uppercase()
                        val name = it.name.uppercase()
                        symbol.contains(query) || ticker.contains(query) || name.contains(query)
                    }.take(12)
                }

                return FilterResults().apply {
                    values = results
                    count = results.size
                }
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                val filtered = (results?.values as? List<*>)
                    ?.filterIsInstance<CoinItem>()
                    .orEmpty()

                items.clear()
                items.addAll(filtered)
                notifyDataSetChanged()
            }

            override fun convertResultToString(resultValue: Any?): CharSequence {
                val coin = resultValue as? CoinItem ?: return ""
                return coin.symbol.removeSuffix("USDT")
            }
        }
    }
}
