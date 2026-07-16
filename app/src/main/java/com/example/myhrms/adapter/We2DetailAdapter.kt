package com.example.myhrms.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myhrms.R
import com.example.myhrms.api.We2SummaryRow
import java.util.Locale

class We2DetailAdapter(
    private val onEdit: (We2SummaryRow) -> Unit,
    private val onDelete: (We2SummaryRow) -> Unit
) : RecyclerView.Adapter<We2DetailAdapter.VH>() {

    private val items = mutableListOf<We2SummaryRow>()

    fun update(list: List<We2SummaryRow>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_we2_detail, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(position + 1, items[position], onEdit, onDelete)

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSeq     : TextView = itemView.findViewById(R.id.tvSeq)
        private val tvScType  : TextView = itemView.findViewById(R.id.tvScType)
        private val tvQuality : TextView = itemView.findViewById(R.id.tvQuality)
        private val tvTrollyNo: TextView = itemView.findViewById(R.id.tvTrollyNo)
        private val tvGrossWt : TextView = itemView.findViewById(R.id.tvGrossWt)
        private val tvTareWt  : TextView = itemView.findViewById(R.id.tvTareWt)
        private val tvNoOfSpools : TextView = itemView.findViewById(R.id.tvNoOfSpools)
        private val tvNetWt   : TextView = itemView.findViewById(R.id.tvNetWt)
        private val tvEdit    : TextView = itemView.findViewById(R.id.tvEdit)
        private val tvDelete  : TextView = itemView.findViewById(R.id.tvDelete)

        fun bind(seq: Int, row: We2SummaryRow,
                 onEdit: (We2SummaryRow) -> Unit, onDelete: (We2SummaryRow) -> Unit) {
            tvEdit.setOnClickListener { onEdit(row) }
            tvDelete.setOnClickListener { onDelete(row) }
            tvSeq.text      = seq.toString()
            tvScType.text   = row.scType ?: "-"
            tvQuality.text  = row.qualityName?.takeIf { it.isNotBlank() } ?: "-"
            tvTrollyNo.text = row.trollyName ?: (row.trollyId?.toString() ?: "-")
            tvGrossWt.text  = row.grossWeight?.let { fmt(it) } ?: "-"
            tvTareWt.text   = row.tareWeight?.let  { fmt(it) } ?: "-"
            tvNoOfSpools.text = row.noOfSpools?.toString() ?: "-"
            tvNetWt.text    = row.netWeight?.let   { Math.round(it).toString() } ?: "-"
        }

        private fun fmt(v: Double) = String.format(Locale.getDefault(), "%.3f", v)
    }
}

