package com.example.myhrms.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myhrms.R
import com.example.myhrms.api.DrawingSummaryItem
import java.util.Locale

class DrawingSummaryAdapter : RecyclerView.Adapter<DrawingSummaryAdapter.ViewHolder>() {

    private val items = mutableListOf<DrawingSummaryItem>()

    fun submitList(newItems: List<DrawingSummaryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_drawing_summary, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvMcName: TextView = view.findViewById(R.id.tvMcName)
        private val tvHours: TextView = view.findViewById(R.id.tvHours)
        private val tvConst: TextView = view.findViewById(R.id.tvConst)
        private val tvUnit: TextView = view.findViewById(R.id.tvUnit)
        private val tvMtrHrs: TextView = view.findViewById(R.id.tvMtrHrs)
        private val tvMtrEff: TextView = view.findViewById(R.id.tvMtrEff)
        private val tvEff: TextView = view.findViewById(R.id.tvEff)

        fun bind(item: DrawingSummaryItem) {
            tvMcName.text = item.mcShortName ?: "—"
            tvHours.text = fmtNum(item.runningHours)
            tvConst.text = (item.constMeter?.toInt() ?: 0).toString()
            tvUnit.text = (item.unit?.toInt() ?: 0).toString()  // Show as integer
            tvMtrHrs.text = fmtNum(item.meterHours)
            tvMtrEff.text = String.format(Locale.getDefault(), "%.2f%%", item.meterEff ?: 0.0)
            tvEff.text = String.format(Locale.getDefault(), "%.2f%%", item.eff ?: 0.0)
        }

        /** Whole numbers show without a decimal (8), fractional with up to 2 (7.5). */
        private fun fmtNum(value: Double?): String {
            val v = value ?: 0.0
            return if (v == v.toLong().toDouble()) v.toLong().toString()
            else String.format(Locale.getDefault(), "%.2f", v)
        }
    }
}

