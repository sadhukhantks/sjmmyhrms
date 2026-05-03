package com.example.myhrms

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myhrms.adapter.FrameEntryAdapter
import com.example.myhrms.api.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SpellWiseFrameEntryActivity : AppCompatActivity() {

    private val apiDate  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dispDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    private var branchId = 0
    private var userId   = 0
    private var entryDate = ""

    private lateinit var tvEntryDate: TextView
    private lateinit var spEntrySpell: Spinner
    private lateinit var cbSelectAll: CheckBox
    private lateinit var tvSelectedCount: TextView
    private lateinit var rvFrames: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var btnSave: Button
    private lateinit var btnReload: Button

    private lateinit var adapter: FrameEntryAdapter

    private val spellList = mutableListOf<Spell>()
    private val machineList = mutableListOf<DoffMachine>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spell_wise_frame_entry)

        branchId = intent.getIntExtra("BRANCH_ID", 0)
        userId   = getSharedPreferences("LoginPrefs", MODE_PRIVATE).getInt("user_id", 0)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        tvEntryDate     = findViewById(R.id.tvEntryDate)
        spEntrySpell    = findViewById(R.id.spEntrySpell)
        cbSelectAll     = findViewById(R.id.cbSelectAll)
        tvSelectedCount = findViewById(R.id.tvSelectedCount)
        rvFrames        = findViewById(R.id.rvFrames)
        progressBar     = findViewById(R.id.progressBar)
        tvEmpty         = findViewById(R.id.tvEmpty)
        btnSave         = findViewById(R.id.btnSave)
        btnReload       = findViewById(R.id.btnReload)

        val cal = Calendar.getInstance()
        entryDate = apiDate.format(cal.time)
        tvEntryDate.text = dispDate.format(cal.time)

        findViewById<View>(R.id.btnEntryDate).setOnClickListener { pickEntryDate() }

        adapter = FrameEntryAdapter(onSelectionChanged = { count -> updateSelectedLabel(count) })
        rvFrames.layoutManager = LinearLayoutManager(this)
        rvFrames.adapter = adapter

        cbSelectAll.setOnClickListener {
            if (cbSelectAll.isChecked) adapter.selectAll() else adapter.clearAll()
        }

        spEntrySpell.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                reloadEntries()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        btnSave.setOnClickListener { saveEntries() }
        btnReload.setOnClickListener { reloadEntries() }

        if (branchId <= 0) {
            Toast.makeText(this, "Branch not selected", Toast.LENGTH_SHORT).show()
        }

        // Sequence: load machines, then spells (which triggers reloadEntries on first selection)
        loadMachines { loadSpells { reloadEntries() } }
    }

    // ── Date picker ────────────────────────────────────────────────
    private fun pickEntryDate() {
        val cal = Calendar.getInstance()
        try { apiDate.parse(entryDate)?.let { cal.time = it } } catch (_: Exception) {}
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            entryDate = apiDate.format(cal.time)
            tvEntryDate.text = dispDate.format(cal.time)
            reloadEntries()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    // ── Loaders ────────────────────────────────────────────────────
    private fun loadMachines(onDone: () -> Unit) {
        if (branchId <= 0) { onDone(); return }
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        RetrofitClient.getApiService(this).getFrameMachines(branchId)
            .enqueue(object : Callback<DoffMachineResponse> {
                override fun onResponse(call: Call<DoffMachineResponse>, response: Response<DoffMachineResponse>) {
                    progressBar.visibility = View.GONE
                    machineList.clear()
                    response.body()?.machines?.let { machineList.addAll(it) }
                    if (machineList.isEmpty()) tvEmpty.visibility = View.VISIBLE
                    adapter.update(machineList)
                    onDone()
                }
                override fun onFailure(call: Call<DoffMachineResponse>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                    Toast.makeText(this@SpellWiseFrameEntryActivity,
                        "Failed to load frames: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                    onDone()
                }
            })
    }

    private fun loadSpells(onDone: () -> Unit) {
        RetrofitClient.getApiService(this).getSpells(branchId.takeIf { it > 0 })
            .enqueue(object : Callback<SpellResponse> {
                override fun onResponse(call: Call<SpellResponse>, response: Response<SpellResponse>) {
                    spellList.clear()
                    response.body()?.spells?.let {
                        spellList.addAll(it.filter { sp ->
                            !sp.spellName.equals("All Spells", ignoreCase = true)
                        })
                    }
                    spEntrySpell.adapter = ArrayAdapter(
                        this@SpellWiseFrameEntryActivity,
                        R.layout.spinner_item_black,
                        spellList.map { it.spellName ?: "" }
                    ).also { it.setDropDownViewResource(R.layout.spinner_dropdown_item_black) }
                    if (spellList.isNotEmpty()) spEntrySpell.setSelection(0)
                    onDone()
                }
                override fun onFailure(call: Call<SpellResponse>, t: Throwable) { onDone() }
            })
    }

    private fun selectedSpellId(): Int? =
        spellList.getOrNull(spEntrySpell.selectedItemPosition)?.spellId

    // ── Reload existing entries → mark checkboxes ──────────────────
    private fun reloadEntries() {
        val spellId = selectedSpellId() ?: return
        if (branchId <= 0) return
        RetrofitClient.getApiService(this).getFrameEntries(entryDate, spellId, branchId)
            .enqueue(object : Callback<FrameEntriesResponse> {
                override fun onResponse(call: Call<FrameEntriesResponse>, response: Response<FrameEntriesResponse>) {
                    val ids = response.body()?.mcIds.orEmpty()
                    adapter.setSelected(ids)
                    cbSelectAll.isChecked = ids.size == machineList.size && machineList.isNotEmpty()
                }
                override fun onFailure(call: Call<FrameEntriesResponse>, t: Throwable) {
                    adapter.setSelected(emptyList())
                }
            })
    }

    private fun updateSelectedLabel(count: Int) {
        val total = adapter.totalCount()
        tvSelectedCount.text = String.format(Locale.getDefault(), "Selected: %d / %d", count, total)
        // Sync header checkbox without retriggering its listener loop
        if (total > 0 && count == total) {
            if (!cbSelectAll.isChecked) cbSelectAll.isChecked = true
        } else if (count == 0) {
            if (cbSelectAll.isChecked) cbSelectAll.isChecked = false
        }
    }

    // ── Save ───────────────────────────────────────────────────────
    private fun saveEntries() {
        val spellId = selectedSpellId()
        if (spellId == null) {
            Toast.makeText(this, "Please select spell", Toast.LENGTH_SHORT).show(); return
        }
        if (branchId <= 0) {
            Toast.makeText(this, "Branch not selected", Toast.LENGTH_SHORT).show(); return
        }
        val req = FrameEntriesSaveRequest(
            date     = entryDate,
            spellId  = spellId,
            branchId = branchId,
            userId   = userId,
            mcIds    = adapter.selectedIds()
        )
        btnSave.isEnabled = false
        RetrofitClient.getApiService(this).saveFrameEntries(req)
            .enqueue(object : Callback<FrameEntriesSaveResponse> {
                override fun onResponse(call: Call<FrameEntriesSaveResponse>, response: Response<FrameEntriesSaveResponse>) {
                    btnSave.isEnabled = true
                    val msg = response.body()?.message ?: if (response.isSuccessful) "Saved" else "Failed"
                    Toast.makeText(this@SpellWiseFrameEntryActivity, msg, Toast.LENGTH_SHORT).show()
                }
                override fun onFailure(call: Call<FrameEntriesSaveResponse>, t: Throwable) {
                    btnSave.isEnabled = true
                    Toast.makeText(this@SpellWiseFrameEntryActivity,
                        "Error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            })
    }
}

