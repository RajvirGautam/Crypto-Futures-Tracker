package com.rajvir.FuturesTracker

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.Locale

data class CoinItem(
    val symbol: String,
    val name: String,
    val amountStr: String,
    val price: Double,
    val priceChangePercent: Double,
    val priceChangeRaw: Double
)

class CoinAdapter(private val onClick: (String) -> Unit) : 
    ListAdapter<CoinItem, CoinAdapter.CoinViewHolder>(CoinDiffCallback()) {

    private val priceFormatter = NumberFormat.getCurrencyInstance(Locale.US)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoinViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_coin_card, parent, false)
        return CoinViewHolder(view)
    }

    override fun onBindViewHolder(holder: CoinViewHolder, position: Int) {
        val coin = getItem(position)
        holder.bind(coin)
        holder.itemView.setOnClickListener { onClick(coin.symbol) }
    }

    inner class CoinViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvCoinName)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvCoinAmount)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvCoinPrice)
        private val tvChange: TextView = itemView.findViewById(R.id.tvCoinChange)

        fun bind(coin: CoinItem) {
            tvName.text = coin.name
            tvAmount.text = coin.amountStr
            
            tvPrice.text = priceFormatter.format(coin.price)
            
            val sign = if (coin.priceChangeRaw >= 0) "+" else ""
            tvChange.text = "$sign${priceFormatter.format(coin.priceChangeRaw)}"
            if (coin.priceChangeRaw >= 0) {
                tvChange.setTextColor(Color.parseColor("#00B873")) // green
            } else {
                tvChange.setTextColor(Color.parseColor("#F6465D")) // red
            }
        }
    }

    class CoinDiffCallback : DiffUtil.ItemCallback<CoinItem>() {
        override fun areItemsTheSame(oldItem: CoinItem, newItem: CoinItem): Boolean {
            return oldItem.symbol == newItem.symbol
        }

        override fun areContentsTheSame(oldItem: CoinItem, newItem: CoinItem): Boolean {
            return oldItem == newItem
        }
    }
}
