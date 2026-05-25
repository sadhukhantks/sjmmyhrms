package com.example.myhrms.adapter

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myhrms.R
import com.example.myhrms.api.DoffMachine
import com.example.myhrms.api.FrameEntry
import com.example.myhrms.api.SpinningQuality

class FrameEntryAdapter(
    private var items: List<DoffMachine> = emptyList(),
    private val selected: MutableSet<Int> = mutableSetOf(),
    private val onSelectionChanged: (Int) -> Unit = {},
    /** Resolve a typed EB No (emp_code) to (eb_id, emp_name). The host runs the
     *  network call and invokes the callback with nulls when not found. */
    private val onEbLookup: (String, (Long?, String?) -> Unit) -> Unit = { _, cb -> cb(null, null) }
) : RecyclerView.Adapter<FrameEntryAdapter.VH>() {

    private var qualities: List<SpinningQuality> = emptyList()
    /** mc_id → quality_id chosen for that machine (persists across check toggles). */
    private val qualityByMcId: MutableMap<Int, Int> = mutableMapOf()
    /** Per-row EB No state, all keyed by mc_id and persisted across check toggles. */
    private val ebCodeByMcId: MutableMap<Int, String> = mutableMapOf()  // typed emp_code
    private val ebIdByMcId:   MutableMap<Int, Long>   = mutableMapOf()  // resolved eb_id
    private val ebNameByMcId: MutableMap<Int, String> = mutableMapOf()  // resolved name

    private val ebLookupHandler   = Handler(Looper.getMainLooper())
    private val ebLookupRunnables = mutableMapOf<Int, Runnable>()

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val cb: CheckBox       = v.findViewById(R.id.cbFrame)
        val tvCode: TextView   = v.findViewById(R.id.tvFrameCode)
        val tvName: TextView   = v.findViewById(R.id.tvFrameName)
        val etEbNo: EditText   = v.findViewById(R.id.etEbNo)
        val tvEbName: TextView = v.findViewById(R.id.tvEbName)
        val spQuality: Spinner = v.findViewById(R.id.spQuality)
        var boundMcId: Int = -1
        var ebWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_frame_entry, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val m = items[pos]
        h.tvCode.text = m.mcCode?.takeIf { it.isNotBlank() } ?: ""
        h.tvName.text = (m.mcName ?: "").trim()

        // Checkbox
        h.cb.setOnCheckedChangeListener(null)
        h.cb.isChecked = selected.contains(m.mcId)
        h.cb.setOnCheckedChangeListener { _, checked ->
            if (checked) selected.add(m.mcId) else selected.remove(m.mcId)
            onSelectionChanged(selected.size)
        }
        h.itemView.setOnClickListener { h.cb.isChecked = !h.cb.isChecked }

        // EB No (per-row) -- detach old watcher before priming the field
        h.boundMcId = m.mcId
        h.ebWatcher?.let { h.etEbNo.removeTextChangedListener(it) }
        h.etEbNo.setText(ebCodeByMcId[m.mcId] ?: "")
        h.tvEbName.text = ebNameByMcId[m.mcId] ?: ""
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val mc = h.boundMcId
                val code = s?.toString()?.trim().orEmpty()
                ebCodeByMcId[mc] = code
                ebIdByMcId.remove(mc)
                ebNameByMcId.remove(mc)
                h.tvEbName.text = ""
                scheduleEbLookup(mc, code, h)
            }
        }
        h.etEbNo.addTextChangedListener(watcher)
        h.ebWatcher = watcher

        // Quality spinner
        if (qualities.isEmpty()) {
            h.spQuality.onItemSelectedListener = null
            h.spQuality.adapter = null
        } else {
            val ctx = h.itemView.context
            val spinAdapter = ArrayAdapter(ctx, R.layout.spinner_item_black, qualities).also {
                it.setDropDownViewResource(R.layout.spinner_dropdown_item_black)
            }
            h.spQuality.onItemSelectedListener = null
            h.spQuality.adapter = spinAdapter

            val currentQid = qualityByMcId[m.mcId]
            val idx = if (currentQid != null) qualities.indexOfFirst { it.qualityId == currentQid } else -1
            val safeIdx = if (idx >= 0) idx else 0
            h.spQuality.setSelection(safeIdx, false)
            // Make sure the map reflects what is actually displayed (so save works
            // even if user never opens the dropdown).
            qualityByMcId[m.mcId] = qualities[safeIdx].qualityId

            h.spQuality.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, position: Int, id: Long) {
                    qualities.getOrNull(position)?.let { qualityByMcId[m.mcId] = it.qualityId }
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
    }

    private fun scheduleEbLookup(mc: Int, code: String, h: VH) {
        ebLookupRunnables.remove(mc)?.let { ebLookupHandler.removeCallbacks(it) }
        if (code.isEmpty()) return
        val r = Runnable {
            onEbLookup(code) { ebId, name ->
                if (ebId != null && ebId > 0L) {
                    ebIdByMcId[mc]   = ebId
                    ebNameByMcId[mc] = name ?: ""
                } else {
                    ebIdByMcId.remove(mc)
                    ebNameByMcId[mc] = ""
                }
                // Update the visible row only if it still shows this machine
                if (h.boundMcId == mc) h.tvEbName.text = ebNameByMcId[mc] ?: ""
            }
        }
        ebLookupRunnables[mc] = r
        ebLookupHandler.postDelayed(r, 500)
    }

    fun update(newItems: List<DoffMachine>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun setQualities(list: List<SpinningQuality>) {
        qualities = list
        notifyDataSetChanged()
    }

    /** Pre-fill quality selection per machine (last-used per mc_id). */
    fun setQualityDefaults(map: Map<Int, Int>, replaceAll: Boolean = false) {
        if (replaceAll) qualityByMcId.clear()
        for ((mc, qid) in map) qualityByMcId[mc] = qid
        notifyDataSetChanged()
    }

    /** Seed the per-row EB No state from saved entries (eb_id + emp_code + name). */
    fun setEbDefaults(entries: List<FrameEntry>) {
        ebCodeByMcId.clear(); ebIdByMcId.clear(); ebNameByMcId.clear()
        for (e in entries) {
            e.ebId?.let { ebIdByMcId[e.mcId] = it }
            if (!e.empCode.isNullOrEmpty()) ebCodeByMcId[e.mcId] = e.empCode
            if (!e.empName.isNullOrEmpty()) ebNameByMcId[e.mcId] = e.empName
        }
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

    /** Build entries payload for currently checked machines using the per-row
     *  chosen quality (falls back to first available quality) and resolved EB. */
    fun selectedEntries(): List<FrameEntry> {
        val fallback = qualities.firstOrNull()?.qualityId
        return items.asSequence()
            .filter { selected.contains(it.mcId) }
            .map { FrameEntry(it.mcId, qualityByMcId[it.mcId] ?: fallback, ebIdByMcId[it.mcId]) }
            .toList()
    }

    /** Frame codes of checked rows where an EB No was TYPED but did not resolve.
     *  A blank EB No is allowed (skips validation) and is saved as null. */
    fun checkedMissingEb(): List<String> =
        items.filter {
            selected.contains(it.mcId) &&
            !ebCodeByMcId[it.mcId].isNullOrBlank() &&     // user typed something
            (ebIdByMcId[it.mcId] ?: 0L) <= 0L             // but it didn't resolve
        }.map { it.mcCode?.takeIf { c -> c.isNotBlank() } ?: (it.mcName ?: it.mcId.toString()) }

    fun totalCount(): Int = items.size
}
