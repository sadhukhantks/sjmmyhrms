package com.example.myhrms.adapter

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myhrms.R

/**
 * Grid row for the Spg Running Hours screen (same pattern as NewMachineGridAdapter).
 * One row per machine; Run Hrs / Stop M / Stop E / Stop O are editable in place.
 * [existingId] is the tbl_daily_vvfd_transaction primary key when a record already
 * exists for the selected date+spell+machine (drives insert-vs-update on save).
 */
data class SpgRunningRow(
    val mcId: Int,
    val machineName: String,
    var existingId: Int? = null,
    var runHrs: String = "",
    var stopM: String = "",
    var stopE: String = "",
    var stopO: String = ""
)

class SpgRunningGridAdapter(
    private val onSave: (SpgRunningRow) -> Unit
) : RecyclerView.Adapter<SpgRunningGridAdapter.VH>() {

    private val rows = mutableListOf<SpgRunningRow>()

    fun submit(list: List<SpgRunningRow>) {
        rows.clear(); rows.addAll(list); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_spg_running_row, parent, false))

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(h: VH, position: Int) = h.bind(rows[position])

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvMachine = v.findViewById<TextView>(R.id.tvMachine)
        private val etRunHrs = v.findViewById<EditText>(R.id.etRunHrs)
        private val etStopM  = v.findViewById<EditText>(R.id.etStopM)
        private val etStopE  = v.findViewById<EditText>(R.id.etStopE)
        private val etStopO  = v.findViewById<EditText>(R.id.etStopO)
        private val ivSave   = v.findViewById<ImageView>(R.id.ivSave)

        // Watchers are bound once per holder and re-targeted at the current row,
        // so recycled holders never write into a stale row.
        private var boundRow: SpgRunningRow? = null

        private fun watcher(set: (String) -> Unit) = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { set(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        }

        init {
            etRunHrs.addTextChangedListener(watcher { boundRow?.runHrs = it })
            etStopM.addTextChangedListener(watcher  { boundRow?.stopM  = it })
            etStopE.addTextChangedListener(watcher  { boundRow?.stopE  = it })
            etStopO.addTextChangedListener(watcher  { boundRow?.stopO  = it })
            ivSave.setOnClickListener { boundRow?.let { onSave(it) } }
        }

        fun bind(row: SpgRunningRow) {
            boundRow = null            // detach watchers while we repopulate
            tvMachine.text = row.machineName
            if (etRunHrs.text.toString() != row.runHrs) etRunHrs.setText(row.runHrs)
            if (etStopM.text.toString()  != row.stopM)  etStopM.setText(row.stopM)
            if (etStopE.text.toString()  != row.stopE)  etStopE.setText(row.stopE)
            if (etStopO.text.toString()  != row.stopO)  etStopO.setText(row.stopO)
            boundRow = row
        }
    }
}
