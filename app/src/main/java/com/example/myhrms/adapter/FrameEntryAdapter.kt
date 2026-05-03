package com.example.myhrms.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myhrms.R
import com.example.myhrms.api.DoffMachine

class FrameEntryAdapter(
    private var items: List<DoffMachine> = emptyList(),
    private val selected: MutableSet<Int> = mutableSetOf(),
    private val onSelectionChanged: (Int) -> Unit = {}
) : RecyclerView.Adapter<FrameEntryAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val cb: CheckBox       = v.findViewById(R.id.cbFrame)
        val tvCode: TextView   = v.findViewById(R.id.tvFrameCode)
        val tvName: TextView   = v.findViewById(R.id.tvFrameName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_frame_entry, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val m = items[pos]
        h.tvCode.text = m.mcCode?.takeIf { it.isNotBlank() } ?: ""
        h.tvName.text = (m.mcName ?: "").trim()

        h.cb.setOnCheckedChangeListener(null)
        h.cb.isChecked = selected.contains(m.mcId)
        h.cb.setOnCheckedChangeListener { _, checked ->
            if (checked) selected.add(m.mcId) else selected.remove(m.mcId)
            onSelectionChanged(selected.size)
        }
        h.itemView.setOnClickListener { h.cb.isChecked = !h.cb.isChecked }
    }

    fun update(newItems: List<DoffMachine>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun setSelected(ids: Collection<Int>) {
        selected.clear(); selected.addAll(ids)
        notifyDataSetChanged()
        onSelectionChanged(selected.size)
    }

    fun selectAll() { selected.clear(); selected.addAll(items.map { it.mcId })
        notifyDataSetChanged(); onSelectionChanged(selected.size) }

    fun clearAll() { selected.clear(); notifyDataSetChanged(); onSelectionChanged(0) }

    fun selectedIds(): List<Int> = selected.toList()
    fun totalCount(): Int = items.size
}

