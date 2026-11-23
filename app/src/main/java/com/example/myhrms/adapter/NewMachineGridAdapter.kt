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
 * Grid row for the New Machine Entry screen.
 * One row per machine; A1/A2/B1/B2/C are editable in place. [existingId] is the
 * tbl_daily_summ_mechine_data primary key when a record already exists for the
 * selected date+machine (drives insert-vs-update on save).
 */
data class NewMachineRow(
    val mcCodeId: Int,
    val machineName: String,
    var existingId: Int? = null,
    var a1: String = "",
    var a2: String = "",
    var b1: String = "",
    var b2: String = "",
    var c:  String = ""
)

class NewMachineGridAdapter(
    private val onSave: (NewMachineRow) -> Unit
) : RecyclerView.Adapter<NewMachineGridAdapter.VH>() {

    private val rows = mutableListOf<NewMachineRow>()

    fun submit(list: List<NewMachineRow>) {
        rows.clear(); rows.addAll(list); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_new_machine_row, parent, false))

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(h: VH, position: Int) = h.bind(rows[position])

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvMachine = v.findViewById<TextView>(R.id.tvMachine)
        private val etA1 = v.findViewById<EditText>(R.id.etA1)
        private val etA2 = v.findViewById<EditText>(R.id.etA2)
        private val etB1 = v.findViewById<EditText>(R.id.etB1)
        private val etB2 = v.findViewById<EditText>(R.id.etB2)
        private val etC  = v.findViewById<EditText>(R.id.etC)
        private val ivSave = v.findViewById<ImageView>(R.id.ivSave)

        // Watchers are bound once per holder and re-targeted at the current row,
        // so recycled holders never write into a stale row.
        private var boundRow: NewMachineRow? = null

        private fun watcher(set: (String) -> Unit) = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { set(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        }

        init {
            etA1.addTextChangedListener(watcher { boundRow?.a1 = it })
            etA2.addTextChangedListener(watcher { boundRow?.a2 = it })
            etB1.addTextChangedListener(watcher { boundRow?.b1 = it })
            etB2.addTextChangedListener(watcher { boundRow?.b2 = it })
            etC.addTextChangedListener(watcher  { boundRow?.c  = it })
            ivSave.setOnClickListener { boundRow?.let { onSave(it) } }
        }

        fun bind(row: NewMachineRow) {
            boundRow = null            // detach watchers while we repopulate
            tvMachine.text = row.machineName
            if (etA1.text.toString() != row.a1) etA1.setText(row.a1)
            if (etA2.text.toString() != row.a2) etA2.setText(row.a2)
            if (etB1.text.toString() != row.b1) etB1.setText(row.b1)
            if (etB2.text.toString() != row.b2) etB2.setText(row.b2)
            if (etC.text.toString()  != row.c)  etC.setText(row.c)
            boundRow = row
        }
    }
}
