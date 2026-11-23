package com.example.myhrms

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myhrms.adapter.NewMachineGridAdapter
import com.example.myhrms.adapter.NewMachineRow
import com.example.myhrms.api.*
import com.example.myhrms.ui.ResultDialog
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * New Machine Entry — pick a date + department, then enter the five spell
 * production values (A1, A2, B1, B2, C) for every machine of that department in
 * an editable grid. Each row saves independently via its own save icon.
 *
 * Backed by tbl_daily_summ_mechine_data, reusing the same backend endpoints as
 * MachineActivity. Department is a client-side filter only (machines carry
 * dept_id); it is never written to the table.
 */
class NewMachineActivity : AppCompatActivity() {

    private val apiDate  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dispDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    private var branchId  = 0
    private var userId    = 0
    private var entryDate = ""
    private var shedType  = "Old Shed"

    private lateinit var tvEntryDate : TextView
    private lateinit var spDepartment: Spinner
    private lateinit var rvMachines  : RecyclerView
    private lateinit var tvGridEmpty : TextView
    private lateinit var pbGrid      : ProgressBar

    private val departments = mutableListOf<Department>()
    private val allMachines  = mutableListOf<McCode>()
    private lateinit var adapter: NewMachineGridAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_machine)

        branchId = intent.getIntExtra("BRANCH_ID", 0)
        userId   = getSharedPreferences("LoginPrefs", MODE_PRIVATE).getInt("user_id", 0)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        tvEntryDate  = findViewById(R.id.tvEntryDate)
        spDepartment = findViewById(R.id.spDepartment)
        rvMachines   = findViewById(R.id.rvMachines)
        tvGridEmpty  = findViewById(R.id.tvGridEmpty)
        pbGrid       = findViewById(R.id.pbGrid)

        adapter = NewMachineGridAdapter { row -> saveRow(row) }
        rvMachines.layoutManager = LinearLayoutManager(this)
        rvMachines.adapter = adapter
        rvMachines.isNestedScrollingEnabled = false

        val cal = Calendar.getInstance()
        entryDate        = apiDate.format(cal.time)
        tvEntryDate.text = dispDate.format(cal.time)

        findViewById<View>(R.id.btnEntryDate).setOnClickListener { pickDate() }

        findViewById<View>(R.id.ivHandsReport).setOnClickListener { openReport("hands") }
        findViewById<View>(R.id.ivHandsStd).setOnClickListener { openReport("std") }

        spDepartment.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                rebuildGrid()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.toggleShed)
            .addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    shedType = if (checkedId == R.id.btnShedNew) "New Shed" else "Old Shed"
                    rebuildGrid()
                }
            }

        loadDepartments()
        loadMachines()
    }

    // Open the on-screen report viewer (Excel/PDF export happens from there).
    private fun openReport(type: String) {
        val i = android.content.Intent(this, HandsReportActivity::class.java)
        i.putExtra("BRANCH_ID", branchId)
        i.putExtra("DATE", entryDate)
        i.putExtra("DATE_DISP", tvEntryDate.text.toString())
        i.putExtra("REPORT_TYPE", type)
        startActivity(i)
    }

    // ── Date ─────────────────────────────────────────────────────────────────
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

    // ── Departments ───────────────────────────────────────────────────────────
    // Sourced from dept_mst (the key mechine_code_master.dept_id references), not
    // the sub-department list, so the grid filter machine.deptId == dept.id works.
    private fun loadDepartments() {
        if (branchId <= 0) { rebuildGrid(); return }
        RetrofitClient.getApiService(this).getMachineDepartments(branchId)
            .enqueue(object : Callback<DepartmentResponse> {
            override fun onResponse(call: Call<DepartmentResponse>, response: Response<DepartmentResponse>) {
                departments.clear()
                departments.addAll(response.body()?.departments.orEmpty())
                spDepartment.adapter = ArrayAdapter(
                    this@NewMachineActivity,
                    R.layout.spinner_item_black,
                    departments.map { it.name }
                ).also { it.setDropDownViewResource(R.layout.spinner_dropdown_item_black) }
                if (departments.isNotEmpty()) spDepartment.setSelection(0) else rebuildGrid()
            }
            override fun onFailure(call: Call<DepartmentResponse>, t: Throwable) {
                Toast.makeText(this@NewMachineActivity, "Failed to load departments", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // ── Machines (all for branch; filtered client-side by department) ──────────
    private fun loadMachines() {
        if (branchId <= 0) return
        RetrofitClient.getApiService(this).getRollStockMcCodes(branchId)
            .enqueue(object : Callback<McCodeResponse> {
                override fun onResponse(call: Call<McCodeResponse>, response: Response<McCodeResponse>) {
                    allMachines.clear()
                    allMachines.addAll(response.body()?.machines.orEmpty())
                    rebuildGrid()
                }
                override fun onFailure(call: Call<McCodeResponse>, t: Throwable) {
                    Toast.makeText(this@NewMachineActivity, "Failed to load machines", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun selectedDeptId(): Int? =
        departments.getOrNull(spDepartment.selectedItemPosition)?.id

    // ── Grid build + prefill ───────────────────────────────────────────────────
    private fun rebuildGrid() {
        val deptId = selectedDeptId()
        // Backend orders by order_no; keep that order.
        val machines = allMachines
            .filter { it.mcCodeId != null && it.deptId == deptId &&
                      (it.shedType ?: "Old Shed").equals(shedType, ignoreCase = true) }

        if (deptId == null || machines.isEmpty()) {
            adapter.submit(emptyList())
            rvMachines.visibility = View.GONE
            pbGrid.visibility     = View.GONE
            tvGridEmpty.visibility = View.VISIBLE
            tvGridEmpty.text = when {
                deptId == null      -> "Select a department to load machines"
                allMachines.isEmpty() -> "Loading machines…"
                else                -> "No machines for this department in $shedType"
            }
            return
        }

        val baseRows = machines.map {
            NewMachineRow(mcCodeId = it.mcCodeId!!, machineName = it.label())
        }

        tvGridEmpty.visibility = View.GONE
        pbGrid.visibility      = View.VISIBLE
        rvMachines.visibility  = View.GONE

        // Prefill existing values for this date + branch.
        RetrofitClient.getApiService(this).getMachineSummaries(entryDate, branchId.takeIf { it > 0 })
            .enqueue(object : Callback<MachineSummaryListResponse> {
                override fun onResponse(call: Call<MachineSummaryListResponse>, response: Response<MachineSummaryListResponse>) {
                    pbGrid.visibility = View.GONE
                    val existing = response.body()?.entries.orEmpty().associateBy { it.mcCodeId }
                    baseRows.forEach { row ->
                        existing[row.mcCodeId]?.let { e ->
                            row.existingId = e.id
                            row.a1 = e.spellA1.toCell(); row.a2 = e.spellA2.toCell()
                            row.b1 = e.spellB1.toCell(); row.b2 = e.spellB2.toCell()
                            row.c  = e.spellC.toCell()
                        }
                    }
                    rvMachines.visibility = View.VISIBLE
                    adapter.submit(baseRows)
                }
                override fun onFailure(call: Call<MachineSummaryListResponse>, t: Throwable) {
                    pbGrid.visibility     = View.GONE
                    rvMachines.visibility = View.VISIBLE
                    adapter.submit(baseRows)   // show blank grid; saving still works
                }
            })
    }

    private fun Double?.toCell(): String =
        if (this == null) "" else String.format(Locale.getDefault(), "%.2f", this)

    private fun String.dec(): Double? = trim().toDoubleOrNull()

    // ── Save a single row (insert or update) ───────────────────────────────────
    private fun saveRow(row: NewMachineRow) {
        val a1 = row.a1.dec(); val a2 = row.a2.dec()
        val b1 = row.b1.dec(); val b2 = row.b2.dec()
        val c  = row.c.dec()
        if (listOf(a1, a2, b1, b2, c).all { it == null }) {
            Toast.makeText(this, "Enter at least one value for ${row.machineName}", Toast.LENGTH_SHORT).show()
            return
        }

        val cb = object : Callback<MachineSummarySaveResponse> {
            override fun onResponse(call: Call<MachineSummarySaveResponse>, response: Response<MachineSummarySaveResponse>) {
                if (response.isSuccessful && response.body()?.status == "success") {
                    // Promote this row to "existing" so a re-save updates instead of
                    // inserting a duplicate. Other rows' unsaved edits are preserved.
                    if (row.existingId == null) row.existingId = response.body()?.id
                    ResultDialog.success(this@NewMachineActivity,
                        message = response.body()?.message ?: "Saved ${row.machineName}")
                } else {
                    ResultDialog.error(this@NewMachineActivity,
                        title = "Save Failed",
                        message = response.body()?.message ?: "Could not save ${row.machineName}")
                }
            }
            override fun onFailure(call: Call<MachineSummarySaveResponse>, t: Throwable) {
                ResultDialog.error(this@NewMachineActivity,
                    title = "Error", message = t.localizedMessage ?: "Network error")
            }
        }

        val id = row.existingId
        if (id != null) {
            RetrofitClient.getApiService(this).updateMachineSummary(
                id,
                MachineSummaryUpdateRequest(
                    mcCodeId = row.mcCodeId,
                    spellA1 = a1, spellA2 = a2, spellB1 = b1, spellB2 = b2, spellC = c,
                    userId = userId
                )
            ).enqueue(cb)
        } else {
            RetrofitClient.getApiService(this).saveMachineSummary(
                MachineSummarySaveRequest(
                    date = entryDate, branchId = branchId, mcCodeId = row.mcCodeId,
                    spellA1 = a1, spellA2 = a2, spellB1 = b1, spellB2 = b2, spellC = c,
                    userId = userId
                )
            ).enqueue(cb)
        }
    }
}
