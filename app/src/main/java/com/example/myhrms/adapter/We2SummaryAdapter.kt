package com.example.myhrms.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myhrms.R
import com.example.myhrms.api.We2GroupedRow
import java.util.Locale

class We2SummaryAdapter(
    private val onClick: (We2GroupedRow) -> Unit = {}
) : RecyclerView.Adapter<We2SummaryAdapter.VH>() {

    private val items = mutableListOf<We2GroupedRow>()

    fun update(list: List<We2GroupedRow>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_we2_summary, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], position + 1, onClick)
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSlNo     : TextView = itemView.findViewById(R.id.tvSlNo)
        private val tvEmpName  : TextView = itemView.findViewById(R.id.tvEmpName)
        private val tvEbNo     : TextView = itemView.findViewById(R.id.tvEbNo)
        private val tvQuality  : TextView = itemView.findViewById(R.id.tvQuality)
        private val tvWeights  : TextView = itemView.findViewById(R.id.tvWeights)
        private val tvNoOfSpools : TextView = itemView.findViewById(R.id.tvNoOfSpools)
        private val tvNoOfDoff : TextView = itemView.findViewById(R.id.tvNoOfDoff)

        fun bind(row: We2GroupedRow, slNo: Int, onClick: (We2GroupedRow) -> Unit) {
            tvSlNo.text     = slNo.toString()
            tvEmpName.text  = row.empName?.takeIf { it.isNotBlank() } ?: row.empCode ?: "-"
            val ebNo = row.empCode?.takeIf { it.isNotBlank() }
            tvEbNo.text = ebNo ?: ""
            tvEbNo.visibility = if (ebNo != null) View.VISIBLE else View.GONE
            tvQuality.text  = row.qualityName?.takeIf { it.isNotBlank() } ?: "-"

            val wtParts = row.weights?.map { fmt(it) } ?: emptyList()
            val total   = fmt(row.totalWt)
            tvWeights.text = if (wtParts.isEmpty()) total
                             else "${wtParts.joinToString("+")} = $total"

            tvNoOfSpools.text = row.noOfSpools.toString()
            tvNoOfDoff.text = row.noOfDoff.toString()
            itemView.setOnClickListener { onClick(row) }
        }

        private fun fmt(v: Double) = String.format(Locale.getDefault(), "%.0f", v)
    }
}
