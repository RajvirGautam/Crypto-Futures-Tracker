package com.rajvir.FuturesTracker

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class CoinItem(
    val symbol: String,
    val name: String,
    val holding: String,
    val price: Double,
    val priceChangeRaw: Double,
    val priceChangePercent: Double
)

class CoinAdapter(
    private val onClick: (String) -> Unit
) : ListAdapter<CoinItem, CoinAdapter.VH>(DIFF) {

    private val US = DecimalFormatSymbols(Locale.US)
    private val priceFmt = DecimalFormat("#,##0.00", US)
    private val pctFmt = DecimalFormat("0.00", US)

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvIcon: TextView = v.findViewById(R.id.tvCoinIcon)
        val tvName: TextView = v.findViewById(R.id.tvCoinName)
        val tvSymbol: TextView = v.findViewById(R.id.tvCoinSymbol)
        val tvPrice: TextView = v.findViewById(R.id.tvCoinPrice)
        val tvChange: TextView = v.findViewById(R.id.tvCoinChange)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_coin, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = getItem(position)
        val ticker = item.symbol.removeSuffix("USDT")

        // Icon circle
        val iconBg = GradientDrawable()
        iconBg.shape = GradientDrawable.OVAL
        iconBg.setColor(getCoinColor(ticker))
        h.tvIcon.background = iconBg
        h.tvIcon.text = ticker.take(2)

        h.tvName.text = item.name
        h.tvSymbol.text = ticker

        h.tvPrice.text = if (item.price > 0) formatPrice(item.price) else "—"

        val pct = item.priceChangePercent
        val sign = if (pct >= 0) "+" else ""
        h.tvChange.text = "$sign${pctFmt.format(pct)}%"
        h.tvChange.setTextColor(if (pct >= 0) 0xFF0ECB81.toInt() else 0xFFF6465D.toInt())

        h.itemView.setOnClickListener { onClick(item.symbol) }
    }

    private fun formatPrice(price: Double): String {
        return when {
            price >= 1000 -> "$${DecimalFormat("#,##0.00", US).format(price)}"
            price >= 1 -> "$${DecimalFormat("0.00##", US).format(price)}"
            else -> "$${DecimalFormat("0.00######", US).format(price)}"
        }
    }

    private fun getCoinColor(ticker: String): Int = when (ticker) {
        "BTC"  -> Color.parseColor("#F7931A")
        "ETH"  -> Color.parseColor("#627EEA")
        "BNB"  -> Color.parseColor("#F3BA2F")
        "SOL"  -> Color.parseColor("#9945FF")
        "XRP"  -> Color.parseColor("#346AA9")
        "ADA"  -> Color.parseColor("#0033AD")
        "DOGE" -> Color.parseColor("#C2A633")
        "AVAX" -> Color.parseColor("#E84142")
        "DOT"  -> Color.parseColor("#E6007A")
        "MATIC","POL" -> Color.parseColor("#8247E5")
        "LINK" -> Color.parseColor("#2A5ADA")
        "LTC"  -> Color.parseColor("#345D9D")
        "UNI"  -> Color.parseColor("#FF007A")
        "ATOM" -> Color.parseColor("#2E3148")
        "SUI"  -> Color.parseColor("#4CA3FF")
        else   -> Color.parseColor("#848E9C")
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<CoinItem>() {
            override fun areItemsTheSame(a: CoinItem, b: CoinItem) = a.symbol == b.symbol
            override fun areContentsTheSame(a: CoinItem, b: CoinItem) = a == b
        }
    }
}
