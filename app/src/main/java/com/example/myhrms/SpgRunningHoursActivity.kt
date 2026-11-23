package com.example.myhrms

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myhrms.adapter.SpgRunningGridAdapter
import com.example.myhrms.adapter.SpgRunningRow
import com.example.myhrms.api.*
import com.example.myhrms.ui.ResultDialog
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Spg Running Hours — pick a date + spell, then enter Run Hrs / Stop M / Stop E /
 * Stop O for every machine in an editable grid (same pattern as NewMachineActivity).
 * Each row saves independently via its own save icon.
 *
 * Backed by tbl_daily_vvfd_transaction. Stop D (loss_min_doff) was removed from the
 * UI; it is never sent, so the backend leaves existing values untouched on update.
 */
class SpgRunningHoursActivity : AppCompatActivity() {

    private val apiDate  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dispDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    private var branchId  = 0
    private var userId    = 0
    private var entryDate = ""

    private lateinit var tvEntryDate : TextView
    private lateinit var spEntrySpell: Spinner
    private lateinit var rvMachines  : RecyclerView
    private lateinit var tvGridEmpty : TextView
    private lateinit var pbGrid      : ProgressBar

    private val spellList = mutableListOf<Spell>()
    private lateinit var adapter: SpgRunningGridAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spg_running_hours)

        branchId = intent.getIntExtra("BRANCH_ID", 0)
        userId   = getSharedPreferences("LoginPrefs", MODE_PRIVATE).getInt("user_id", 0)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        tvEntryDate  = findViewById(R.id.tvEntryDate)
        spEntrySpell = findViewById(R.id.spEntrySpell)
        rvMachines   = findViewById(R.id.rvMachines)
        tvGridEmpty  = findViewById(R.id.tvGridEmpty)
        pbGrid       = findViewById(R.id.pbGrid)

        adapter = SpgRunningGridAdapter { row -> saveRow(row) }
        rvMachines.layoutManager = LinearLayoutManager(this)
        rvMachines.adapter = adapter
        rvMachines.isNestedScrollingEnabled = false

        val cal = Calendar.getInstance()
        entryDate        = apiDate.format(cal.time)
        tvEntryDate.text = dispDate.format(cal.time)

        findViewById<View>(R.id.btnEntryDate).setOnClickListener { pickDate() }

        spEntrySpell.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                rebuildGrid()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        loadSpells()
    }

    private fun pickDate() {
        val cal = Calendar.getInstance()
        try { apiDate.parse(entryDate)?.let { cal.time = it } } catch (_: Exception) {}
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            entryDate        = apiDate.format(cal.time)
            tvEntryDate.text = dispDate.format(cal.time)
            rebuildGrid()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun loadSpells() {
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
                        this@SpgRunningHoursActivity,
                        R.layout.spinner_item_black,
                        spellList.map { it.spellName ?: "" }
                    ).also { it.setDropDownViewResource(R.layout.spinner_dropdown_item_black) }
                    if (spellList.isNotEmpty()) spEntrySpell.setSelection(0) else rebuildGrid()
                }
                override fun onFailure(call: Call<SpellResponse>, t: Throwable) {
                    Toast.makeText(this@SpgRunningHoursActivity, "Failed to load spells", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun selectedSpellId(): Int? =
        spellList.getOrNull(spEntrySpell.selectedItemPosition)?.spellId

    // ── Grid build + prefill ───────────────────────────────────────────────────
    private fun rebuildGrid() {
        val spellId = selectedSpellId()
        if (spellId == null || branchId <= 0) {
            showEmpty(if (branchId <= 0) "Branch not set" else "Select a spell to load machines")
            return
        }

        tvGridEmpty.visibility = View.GONE
        rvMachines.visibility  = View.GONE
        pbGrid.visibility      = View.VISIBLE

        RetrofitClient.getApiService(this).getSpg1MechCodes(
            date     = entryDate,
            spellId  = spellId,
            branchId = branchId
        ).enqueue(object : Callback<Spg1MechCodesResponse> {
            override fun onResponse(call: Call<Spg1MechCodesResponse>, response: Response<Spg1MechCodesResponse>) {
                val machines = response.body()?.codes.orEmpty()
                if (machines.isEmpty()) {
                    showEmpty("No machines found for selected date/spell")
                    return
                }
                val baseRows = machines.map {
                    SpgRunningRow(
                        mcId = it.mcId,
                        machineName = it.mechPostingCode?.toString()
                            ?: it.mcCode?.takeIf { c -> c.isNotBlank() }
                            ?: "MC${it.mcId}"
                    )
                }
                prefill(baseRows, spellId)
            }
            override fun onFailure(call: Call<Spg1MechCodesResponse>, t: Throwable) {
                showEmpty("Failed to load machines")
            }
        })
    }

    private fun prefill(baseRows: List<SpgRunningRow>, spellId: Int) {
        RetrofitClient.getApiService(this).getSpgRunningHours(
            date     = entryDate,
            spellId  = spellId,
            branchId = branchId.takeIf { it > 0 }
        ).enqueue(object : Callback<SpgRunningListResponse> {
            override fun onResponse(call: Call<SpgRunningListResponse>, response: Response<SpgRunningListResponse>) {
                val existing = response.body()?.entries.orEmpty().associateBy { it.mcId }
                baseRows.forEach { row ->
                    existing[row.mcId]?.let { e ->
                        row.existingId = e.id
                        row.runHrs = e.mcRunsTime?.toCell(3) ?: ""
                        row.stopM  = e.lossMinMech?.toCell(2) ?: ""
                        row.stopE  = e.lossMinElec?.toCell(2) ?: ""
                        row.stopO  = e.lossMinOth?.toCell(2)  ?: ""
                    }
                }
                showGrid(baseRows)
            }
            override fun onFailure(call: Call<SpgRunningListResponse>, t: Throwable) {
                showGrid(baseRows)   // show blank grid; saving still works
            }
        })
    }

    private fun showGrid(rows: List<SpgRunningRow>) {
        pbGrid.visibility     = View.GONE
        rvMachines.visibility = View.VISIBLE
        adapter.submit(rows)
    }

    private fun showEmpty(msg: String) {
        adapter.submit(emptyList())
        pbGrid.visibility      = View.GONE
        rvMachines.visibility  = View.GONE
        tvGridEmpty.visibility = View.VISIBLE
        tvGridEmpty.text       = msg
    }

    private fun Double.toCell(decimals: Int): String =
        String.format(Locale.getDefault(), "%.${decimals}f", this)

    private fun String.dec(): Double? = trim().toDoubleOrNull()

    // ── Save a single row (insert or update) ───────────────────────────────────
    private fun saveRow(row: SpgRunningRow) {
        val spellId = selectedSpellId() ?: return
        val hours = row.runHrs.dec()
        if (hours == null || hours <= 0.0) {
            Toast.makeText(this, "Enter Running Hours for ${row.machineName}", Toast.LENGTH_SHORT).show()
            return
        }

        val cb = object : Callback<SpgRunningSaveResponse> {
            override fun onResponse(call: Call<SpgRunningSaveResponse>, response: Response<SpgRunningSaveResponse>) {
                if (response.isSuccessful && response.body()?.status == "success") {
                    // Promote this row to "existing" so a re-save updates instead of
                    // inserting a duplicate. Other rows' unsaved edits are preserved.
                    if (row.existingId == null) row.existingId = response.body()?.id
                    ResultDialog.success(this@SpgRunningHoursActivity,
                        message = response.body()?.message ?: "Saved MC ${row.machineName}")
                } else {
                    ResultDialog.error(this@SpgRunningHoursActivity,
                        title = "Save Failed",
                        message = response.body()?.message ?: "Could not save MC ${row.machineName}")
                }
            }
            override fun onFailure(call: Call<SpgRunningSaveResponse>, t: Throwable) {
                ResultDialog.error(this@SpgRunningHoursActivity,
                    title = "Error", message = t.localizedMessage ?: "Network error")
            }
        }

        val id = row.existingId
        if (id != null) {
            RetrofitClient.getApiService(this).updateSpgRunningHours(
                id,
                SpgRunningUpdateRequest(
                    mcId = row.mcId, branchId = branchId, mcRunsTime = hours,
                    kwConsumption = null,
                    lossMinDoff = null,
                    lossMinMech = row.stopM.dec(),
                    lossMinElec = row.stopE.dec(),
                    lossMinOth  = row.stopO.dec(),
                    userId = userId
                )
            ).enqueue(cb)
        } else {
            RetrofitClient.getApiService(this).saveSpgRunningHours(
                SpgRunningSaveRequest(
                    date          = entryDate,
                    spellId       = spellId,
                    branchId      = branchId,
                    mcId          = row.mcId,
                    mcRunsTime    = hours,
                    kwConsumption = null,
                    lossMinDoff   = null,
                    lossMinMech   = row.stopM.dec(),
                    lossMinElec   = row.stopE.dec(),
                    lossMinOth    = row.stopO.dec(),
                    userId        = userId
                )
            ).enqueue(cb)
        }
    }
}
