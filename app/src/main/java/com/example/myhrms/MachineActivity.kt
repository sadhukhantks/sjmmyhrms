package com.example.myhrms

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myhrms.adapter.MachineSummaryAdapter
import com.example.myhrms.api.*
import com.example.myhrms.ui.ResultDialog
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Daily Machine Summary — date + machine + 5 spell production values
 * (A1, A2, B1, B2, C). Backed by tbl_daily_summ_mechine_data; shift_a/b/c are
 * derived on the backend. Machine dropdown comes from mechine_code_master.
 */
class MachineActivity : AppCompatActivity() {

    private val apiDate  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dispDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    private var branchId  = 0
    private var userId    = 0
    private var entryDate = ""

    private lateinit var tvEntryDate    : TextView
    private lateinit var spMachine      : Spinner
    private lateinit var etSpellA1      : EditText
    private lateinit var etSpellA2      : EditText
    private lateinit var etSpellB1      : EditText
    private lateinit var etSpellB2      : EditText
    private lateinit var etSpellC       : EditText
    private lateinit var btnSave        : Button
    private lateinit var rvSummary      : RecyclerView
    private lateinit var tvSummaryEmpty : TextView
    private lateinit var pbSummary      : ProgressBar

    private val machineList = mutableListOf<McCode>()
    private lateinit var summaryAdapter: MachineSummaryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_machine)

        branchId = intent.getIntExtra("BRANCH_ID", 0)
        userId   = getSharedPreferences("LoginPrefs", MODE_PRIVATE).getInt("user_id", 0)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        tvEntryDate    = findViewById(R.id.tvEntryDate)
        spMachine      = findViewById(R.id.spMachine)
        etSpellA1      = findViewById(R.id.etSpellA1)
        etSpellA2      = findViewById(R.id.etSpellA2)
        etSpellB1      = findViewById(R.id.etSpellB1)
        etSpellB2      = findViewById(R.id.etSpellB2)
        etSpellC       = findViewById(R.id.etSpellC)
        btnSave        = findViewById(R.id.btnSave)
        rvSummary      = findViewById(R.id.rvSummary)
        tvSummaryEmpty = findViewById(R.id.tvSummaryEmpty)
        pbSummary      = findViewById(R.id.pbSummary)

        summaryAdapter = MachineSummaryAdapter { row -> showRowOptions(row) }
        rvSummary.layoutManager = LinearLayoutManager(this)
        rvSummary.adapter = summaryAdapter
        rvSummary.isNestedScrollingEnabled = false

        val cal = Calendar.getInstance()
        entryDate        = apiDate.format(cal.time)
        tvEntryDate.text = dispDate.format(cal.time)

        findViewById<View>(R.id.btnEntryDate).setOnClickListener { pickDate() }
        btnSave.setOnClickListener { saveEntry() }

        loadMachines()
        loadSummary()
    }

    // ── Date picker ──────────────────────────────────────────────────────────
    private fun pickDate() {
        val cal = Calendar.getInstance()
        try { apiDate.parse(entryDate)?.let { cal.time = it } } catch (_: Exception) {}
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            entryDate        = apiDate.format(cal.time)
            tvEntryDate.text = dispDate.format(cal.time)
            loadSummary()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    // ── Machines (mechine_code_master) ─────────────────────────────────────────
    private fun loadMachines() {
        if (branchId <= 0) return
        RetrofitClient.getApiService(this).getRollStockMcCodes(branchId)
            .enqueue(object : Callback<McCodeResponse> {
                override fun onResponse(call: Call<McCodeResponse>, response: Response<McCodeResponse>) {
                    machineList.clear()
                    machineList.addAll(response.body()?.machines.orEmpty())
                    spMachine.adapter = ArrayAdapter(
                        this@MachineActivity,
                        R.layout.spinner_item_black,
                        machineList.map { it.label() }
                    ).also { it.setDropDownViewResource(R.layout.spinner_dropdown_item_black) }
                }
                override fun onFailure(call: Call<McCodeResponse>, t: Throwable) {
                    Toast.makeText(this@MachineActivity,
                        "Failed to load machines", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun selectedMcCodeId(): Int? =
        machineList.getOrNull(spMachine.selectedItemPosition)?.mcCodeId

    private fun EditText.dec(): Double? = text.toString().trim().toDoubleOrNull()

    // ── Save ───────────────────────────────────────────────────────────────────
    private fun saveEntry() {
        val mcId = selectedMcCodeId()
        if (mcId == null) {
            Toast.makeText(this, "Please select a machine", Toast.LENGTH_SHORT).show(); return
        }
        val a1 = etSpellA1.dec(); val a2 = etSpellA2.dec()
        val b1 = etSpellB1.dec(); val b2 = etSpellB2.dec()
        val c  = etSpellC.dec()
        if (listOf(a1, a2, b1, b2, c).all { it == null }) {
            Toast.makeText(this, "Enter at least one spell value", Toast.LENGTH_SHORT).show(); return
        }

        val req = MachineSummarySaveRequest(
            date     = entryDate,
            branchId = branchId,
            mcCodeId = mcId,
            spellA1  = a1, spellA2 = a2,
            spellB1  = b1, spellB2 = b2,
            spellC   = c,
            userId   = userId
        )
        btnSave.isEnabled = false
        RetrofitClient.getApiService(this).saveMachineSummary(req)
            .enqueue(object : Callback<MachineSummarySaveResponse> {
                override fun onResponse(call: Call<MachineSummarySaveResponse>, response: Response<MachineSummarySaveResponse>) {
                    btnSave.isEnabled = true
                    if (response.isSuccessful && response.body()?.status == "success") {
                        ResultDialog.success(
                            this@MachineActivity,
                            message = response.body()?.message ?: "Saved successfully"
                        )
                        clearForm()
                        loadSummary()
                    } else {
                        ResultDialog.error(
                            this@MachineActivity,
                            title   = "Save Failed",
                            message = response.body()?.message ?: "Could not save the entry"
                        )
                    }
                }
                override fun onFailure(call: Call<MachineSummarySaveResponse>, t: Throwable) {
                    btnSave.isEnabled = true
                    ResultDialog.error(
                        this@MachineActivity,
                        title   = "Error",
                        message = t.localizedMessage ?: "Network error"
                    )
                }
            })
    }

    private fun clearForm() {
        etSpellA1.setText(""); etSpellA2.setText("")
        etSpellB1.setText(""); etSpellB2.setText("")
        etSpellC.setText("")
    }

    // ── Summary list ────────────────────────────────────────────────────────────
    private fun loadSummary() {
        pbSummary.visibility      = View.VISIBLE
        tvSummaryEmpty.visibility = View.GONE
        rvSummary.visibility      = View.GONE

        RetrofitClient.getApiService(this).getMachineSummaries(entryDate, branchId.takeIf { it > 0 })
            .enqueue(object : Callback<MachineSummaryListResponse> {
                override fun onResponse(call: Call<MachineSummaryListResponse>, response: Response<MachineSummaryListResponse>) {
                    pbSummary.visibility = View.GONE
                    val list = response.body()?.entries.orEmpty()
                    if (list.isEmpty()) {
                        tvSummaryEmpty.visibility = View.VISIBLE
                    } else {
                        rvSummary.visibility = View.VISIBLE
                        summaryAdapter.update(list)
                    }
                }
                override fun onFailure(call: Call<MachineSummaryListResponse>, t: Throwable) {
                    pbSummary.visibility      = View.GONE
                    tvSummaryEmpty.visibility = View.VISIBLE
                    tvSummaryEmpty.text       = "Failed to load summary"
                }
            })
    }

    // ── Row options (edit / delete) ──────────────────────────────────────────────
    private fun showRowOptions(row: MachineSummaryEntry) {
        AlertDialog.Builder(this)
            .setTitle(row.machineLabel())
            .setItems(arrayOf("Edit", "Delete")) { _, which ->
                when (which) {
                    0 -> showEditDialog(row)
                    1 -> confirmDelete(row)
                }
            }
            .show()
    }

    private fun showEditDialog(row: MachineSummaryEntry) {
        val dp = resources.displayMetrics.density
        val pad = (20 * dp).toInt()
        val container = android.widget.LinearLayout(this)
        container.orientation = android.widget.LinearLayout.VERTICAL
        container.setPadding(pad, (10 * dp).toInt(), pad, 0)

        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(
            this, R.layout.spinner_item_black, machineList.map { it.label() }
        ).also { it.setDropDownViewResource(R.layout.spinner_dropdown_item_black) }
        machineList.indexOfFirst { it.mcCodeId == row.mcCodeId }
            .takeIf { it >= 0 }?.let { sp.setSelection(it) }
        container.addView(sp)

        fun field(label: String, value: Double?): EditText {
            val tv = TextView(this)
            tv.text = label
            tv.setTextColor(0xFF000000.toInt())
            container.addView(tv)
            val et = EditText(this)
            et.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (value != null) et.setText(String.format(Locale.getDefault(), "%.2f", value))
            container.addView(et)
            return et
        }

        val eA1 = field("Spell A1", row.spellA1)
        val eA2 = field("Spell A2", row.spellA2)
        val eB1 = field("Spell B1", row.spellB1)
        val eB2 = field("Spell B2", row.spellB2)
        val eC  = field("Spell C",  row.spellC)

        val scroll = android.widget.ScrollView(this)
        scroll.addView(container)

        AlertDialog.Builder(this)
            .setTitle("Edit Entry")
            .setView(scroll)
            .setPositiveButton("Update") { _, _ ->
                val id = row.id ?: return@setPositiveButton
                val mcId = machineList.getOrNull(sp.selectedItemPosition)?.mcCodeId
                if (mcId == null) {
                    Toast.makeText(this, "Select a machine", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                updateEntry(id, MachineSummaryUpdateRequest(
                    mcCodeId = mcId,
                    spellA1  = eA1.text.toString().trim().toDoubleOrNull(),
                    spellA2  = eA2.text.toString().trim().toDoubleOrNull(),
                    spellB1  = eB1.text.toString().trim().toDoubleOrNull(),
                    spellB2  = eB2.text.toString().trim().toDoubleOrNull(),
                    spellC   = eC.text.toString().trim().toDoubleOrNull(),
                    userId   = userId
                ))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateEntry(id: Int, req: MachineSummaryUpdateRequest) {
        RetrofitClient.getApiService(this).updateMachineSummary(id, req)
            .enqueue(object : Callback<MachineSummarySaveResponse> {
                override fun onResponse(call: Call<MachineSummarySaveResponse>, response: Response<MachineSummarySaveResponse>) {
                    if (response.isSuccessful && response.body()?.status == "success") {
                        ResultDialog.success(this@MachineActivity,
                            message = response.body()?.message ?: "Updated")
                        loadSummary()
                    } else {
                        ResultDialog.error(this@MachineActivity,
                            title = "Update Failed",
                            message = response.body()?.message ?: "Could not update")
                    }
                }
                override fun onFailure(call: Call<MachineSummarySaveResponse>, t: Throwable) {
                    ResultDialog.error(this@MachineActivity,
                        title = "Error", message = t.localizedMessage ?: "Network error")
                }
            })
    }

    private fun confirmDelete(row: MachineSummaryEntry) {
        val id = row.id ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete entry?")
            .setMessage("Remove ${row.machineLabel()} for ${tvEntryDate.text}?")
            .setPositiveButton("Delete") { _, _ ->
                RetrofitClient.getApiService(this).deleteMachineSummary(id)
                    .enqueue(object : Callback<MachineSummarySaveResponse> {
                        override fun onResponse(call: Call<MachineSummarySaveResponse>, response: Response<MachineSummarySaveResponse>) {
                            if (response.isSuccessful && response.body()?.status == "success") {
                                ResultDialog.success(this@MachineActivity,
                                    message = response.body()?.message ?: "Deleted")
                                loadSummary()
                            } else {
                                ResultDialog.error(this@MachineActivity,
                                    title = "Delete Failed",
                                    message = response.body()?.message ?: "Could not delete")
                            }
                        }
                        override fun onFailure(call: Call<MachineSummarySaveResponse>, t: Throwable) {
                            ResultDialog.error(this@MachineActivity,
                                title = "Error", message = t.localizedMessage ?: "Network error")
                        }
                    })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
