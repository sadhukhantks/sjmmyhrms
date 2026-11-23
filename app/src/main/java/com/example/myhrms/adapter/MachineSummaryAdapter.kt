package com.example.myhrms.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myhrms.R
import com.example.myhrms.api.MachineSummaryEntry
import java.util.Locale

class MachineSummaryAdapter(
    private val onClick: (MachineSummaryEntry) -> Unit = {}
) : RecyclerView.Adapter<MachineSummaryAdapter.VH>() {

    private val items = mutableListOf<MachineSummaryEntry>()

    fun update(list: List<MachineSummaryEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_machine_summary, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], onClick)
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMachine : TextView = itemView.findViewById(R.id.tvMachine)
        private val tvShiftA  : TextView = itemView.findViewById(R.id.tvShiftA)
        private val tvShiftB  : TextView = itemView.findViewById(R.id.tvShiftB)
        private val tvShiftC  : TextView = itemView.findViewById(R.id.tvShiftC)

        fun bind(row: MachineSummaryEntry, onClick: (MachineSummaryEntry) -> Unit) {
            tvMachine.text = row.machineLabel()
            tvShiftA.text  = fmt(row.shiftA)
            tvShiftB.text  = fmt(row.shiftB)
            tvShiftC.text  = fmt(row.shiftC)
            itemView.setOnClickListener { onClick(row) }
        }

        private fun fmt(v: Double?) =
            if (v == null) "-" else String.format(Locale.getDefault(), "%.2f", v)
    }
}
