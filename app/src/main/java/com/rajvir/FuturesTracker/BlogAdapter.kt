package com.rajvir.FuturesTracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BlogAdapter(
    private val items: List<BlogItem>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<BlogAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvBlogTitle)
        val tvDate: TextView  = view.findViewById(R.id.tvBlogDate)
        val tvDesc: TextView  = view.findViewById(R.id.tvBlogDesc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_blog, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title
        holder.tvDate.text  = item.date
        holder.tvDesc.text  = item.description
        holder.itemView.setOnClickListener { onClick(item.link) }
    }

    override fun getItemCount() = items.size
}
