package com.example.myhrms.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myhrms.R
import com.example.myhrms.api.ContWindingEntry

class ContWindingSummaryAdapter(
    private val onClick: (ContWindingEntry) -> Unit = {}
) : RecyclerView.Adapter<ContWindingSummaryAdapter.VH>() {

    private val items = mutableListOf<ContWindingEntry>()

    fun update(list: List<ContWindingEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cont_winding_summary, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], onClick)
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvQuality : TextView = itemView.findViewById(R.id.tvQuality)
        private val tvProdKgs : TextView = itemView.findViewById(R.id.tvProdKgs)

        fun bind(row: ContWindingEntry, onClick: (ContWindingEntry) -> Unit) {
            tvQuality.text = row.qualityName?.takeIf { it.isNotBlank() } ?: "-"
            tvProdKgs.text = (row.prodKgs ?: 0).toString()
            itemView.setOnClickListener { onClick(row) }
        }
    }
}
