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
import com.example.myhrms.adapter.ContWindingSummaryAdapter
import com.example.myhrms.api.*
import com.example.myhrms.ui.ResultDialog
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Continuous Winding Entry — a simple date + winding-quality + production-kgs
 * entry backed by tbl_cont_widning_entry. No spell / branch dimension.
 */
class ContWindingEntryActivity : AppCompatActivity() {

    private val apiDate  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dispDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    private var userId    = 0
    private var entryDate = ""

    private lateinit var tvEntryDate    : TextView
    private lateinit var spQuality      : Spinner
    private lateinit var etProdKgs      : EditText
    private lateinit var btnSave        : Button
    private lateinit var rvSummary      : RecyclerView
    private lateinit var tvSummaryEmpty : TextView
    private lateinit var pbSummary      : ProgressBar

    private val qualityList = mutableListOf<SpinningQuality>()
    private lateinit var summaryAdapter: ContWindingSummaryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cont_winding_entry)

        userId = getSharedPreferences("LoginPrefs", MODE_PRIVATE).getInt("user_id", 0)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        tvEntryDate    = findViewById(R.id.tvEntryDate)
        spQuality      = findViewById(R.id.spQuality)
        etProdKgs      = findViewById(R.id.etProdKgs)
        btnSave        = findViewById(R.id.btnSave)
        rvSummary      = findViewById(R.id.rvSummary)
        tvSummaryEmpty = findViewById(R.id.tvSummaryEmpty)
        pbSummary      = findViewById(R.id.pbSummary)

        summaryAdapter = ContWindingSummaryAdapter { row -> showRowOptions(row) }
        rvSummary.layoutManager = LinearLayoutManager(this)
        rvSummary.adapter = summaryAdapter
        rvSummary.isNestedScrollingEnabled = false

        val cal = Calendar.getInstance()
        // 00:00–05:59 belongs to the previous day's shift → default to current date − 1
        if (cal.get(Calendar.HOUR_OF_DAY) < 6) cal.add(Calendar.DAY_OF_MONTH, -1)
        entryDate        = apiDate.format(cal.time)
        tvEntryDate.text = dispDate.format(cal.time)

        findViewById<View>(R.id.btnEntryDate).setOnClickListener { pickDate() }
        btnSave.setOnClickListener { saveEntry() }

        loadQualities()
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

    // ── Qualities (winding_quality_master) ─────────────────────────────────────
    private fun loadQualities() {
        RetrofitClient.getApiService(this).getWindingQualities(null)
            .enqueue(object : Callback<WindingQualityResponse> {
                override fun onResponse(call: Call<WindingQualityResponse>, response: Response<WindingQualityResponse>) {
                    qualityList.clear()
                    qualityList.addAll(response.body()?.qualities.orEmpty())
                    spQuality.adapter = ArrayAdapter(
                        this@ContWindingEntryActivity,
                        R.layout.spinner_item_black,
                        qualityList.map { it.qualityName ?: "" }
                    ).also { it.setDropDownViewResource(R.layout.spinner_dropdown_item_black) }
                }
                override fun onFailure(call: Call<WindingQualityResponse>, t: Throwable) {
                    Toast.makeText(this@ContWindingEntryActivity,
                        "Failed to load qualities", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun selectedQualityId(): Int? =
        qualityList.getOrNull(spQuality.selectedItemPosition)?.qualityId

    // ── Save ───────────────────────────────────────────────────────────────────
    private fun saveEntry() {
        val qualityId = selectedQualityId()
        if (qualityId == null) {
            Toast.makeText(this, "Please select a quality", Toast.LENGTH_SHORT).show(); return
        }
        val kgs = etProdKgs.text.toString().trim().toIntOrNull()
        if (kgs == null || kgs <= 0) {
            Toast.makeText(this, "Please enter Prod Kgs", Toast.LENGTH_SHORT).show(); return
        }

        val req = ContWindingSaveRequest(
            date      = entryDate,
            qualityId = qualityId,
            prodKgs   = kgs,
            userId    = userId
        )
        btnSave.isEnabled = false
        RetrofitClient.getApiService(this).saveContWindingEntry(req)
            .enqueue(object : Callback<ContWindingSaveResponse> {
                override fun onResponse(call: Call<ContWindingSaveResponse>, response: Response<ContWindingSaveResponse>) {
                    btnSave.isEnabled = true
                    if (response.isSuccessful && response.body()?.status == "success") {
                        ResultDialog.success(
                            this@ContWindingEntryActivity,
                            message = response.body()?.message ?: "Saved successfully"
                        )
                        etProdKgs.setText("")
                        loadSummary()
                    } else {
                        ResultDialog.error(
                            this@ContWindingEntryActivity,
                            title   = "Save Failed",
                            message = response.body()?.message ?: "Could not save the entry"
                        )
                    }
                }
                override fun onFailure(call: Call<ContWindingSaveResponse>, t: Throwable) {
                    btnSave.isEnabled = true
                    ResultDialog.error(
                        this@ContWindingEntryActivity,
                        title   = "Error",
                        message = t.localizedMessage ?: "Network error"
                    )
                }
            })
    }

    // ── Summary list ────────────────────────────────────────────────────────────
    private fun loadSummary() {
        pbSummary.visibility      = View.VISIBLE
        tvSummaryEmpty.visibility = View.GONE
        rvSummary.visibility      = View.GONE

        RetrofitClient.getApiService(this).getContWindingEntries(entryDate)
            .enqueue(object : Callback<ContWindingEntriesResponse> {
                override fun onResponse(call: Call<ContWindingEntriesResponse>, response: Response<ContWindingEntriesResponse>) {
                    pbSummary.visibility = View.GONE
                    val list = response.body()?.entries.orEmpty()
                    if (list.isEmpty()) {
                        tvSummaryEmpty.visibility = View.VISIBLE
                    } else {
                        rvSummary.visibility = View.VISIBLE
                        summaryAdapter.update(list)
                    }
                }
                override fun onFailure(call: Call<ContWindingEntriesResponse>, t: Throwable) {
                    pbSummary.visibility      = View.GONE
                    tvSummaryEmpty.visibility = View.VISIBLE
                    tvSummaryEmpty.text       = "Failed to load summary"
                }
            })
    }

    // ── Row options (edit / delete) ──────────────────────────────────────────────
    private fun showRowOptions(row: ContWindingEntry) {
        AlertDialog.Builder(this)
            .setTitle(row.qualityName ?: "Entry")
            .setItems(arrayOf("Edit", "Delete")) { _, which ->
                when (which) {
                    0 -> showEditDialog(row)
                    1 -> confirmDelete(row)
                }
            }
            .show()
    }

    private fun showEditDialog(row: ContWindingEntry) {
        val dp = resources.displayMetrics.density
        val container = android.widget.LinearLayout(this)
        container.orientation = android.widget.LinearLayout.VERTICAL
        container.setPadding((20 * dp).toInt(), (10 * dp).toInt(), (20 * dp).toInt(), 0)

        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(
            this, R.layout.spinner_item_black, qualityList.map { it.qualityName ?: "" }
        ).also { it.setDropDownViewResource(R.layout.spinner_dropdown_item_black) }
        qualityList.indexOfFirst { it.qualityId == row.qualityId }
            .takeIf { it >= 0 }?.let { sp.setSelection(it) }
        container.addView(sp)

        val et = EditText(this)
        et.hint = "Prod Kgs"
        et.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        et.setText((row.prodKgs ?: 0).toString())
        container.addView(et)

        AlertDialog.Builder(this)
            .setTitle("Edit Entry")
            .setView(container)
            .setPositiveButton("Update") { _, _ ->
                val id = row.id ?: return@setPositiveButton
                val qId = qualityList.getOrNull(sp.selectedItemPosition)?.qualityId
                val kgs = et.text.toString().trim().toIntOrNull()
                if (qId == null || kgs == null || kgs <= 0) {
                    Toast.makeText(this, "Enter valid quality and Prod Kgs", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                updateEntry(id, ContWindingUpdateRequest(qId, kgs, userId))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateEntry(id: Int, req: ContWindingUpdateRequest) {
        RetrofitClient.getApiService(this).updateContWindingEntry(id, req)
            .enqueue(object : Callback<ContWindingSaveResponse> {
                override fun onResponse(call: Call<ContWindingSaveResponse>, response: Response<ContWindingSaveResponse>) {
                    if (response.isSuccessful && response.body()?.status == "success") {
                        ResultDialog.success(this@ContWindingEntryActivity,
                            message = response.body()?.message ?: "Updated")
                        loadSummary()
                    } else {
                        ResultDialog.error(this@ContWindingEntryActivity,
                            title = "Update Failed",
                            message = response.body()?.message ?: "Could not update")
                    }
                }
                override fun onFailure(call: Call<ContWindingSaveResponse>, t: Throwable) {
                    ResultDialog.error(this@ContWindingEntryActivity,
                        title = "Error", message = t.localizedMessage ?: "Network error")
                }
            })
    }

    private fun confirmDelete(row: ContWindingEntry) {
        val id = row.id ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete entry?")
            .setMessage("Remove ${row.qualityName ?: "this"} (${row.prodKgs ?: 0} kgs)?")
            .setPositiveButton("Delete") { _, _ ->
                RetrofitClient.getApiService(this).deleteContWindingEntry(id)
                    .enqueue(object : Callback<ContWindingSaveResponse> {
                        override fun onResponse(call: Call<ContWindingSaveResponse>, response: Response<ContWindingSaveResponse>) {
                            if (response.isSuccessful && response.body()?.status == "success") {
                                ResultDialog.success(this@ContWindingEntryActivity,
                                    message = response.body()?.message ?: "Deleted")
                                loadSummary()
                            } else {
                                ResultDialog.error(this@ContWindingEntryActivity,
                                    title = "Delete Failed",
                                    message = response.body()?.message ?: "Could not delete")
                            }
                        }
                        override fun onFailure(call: Call<ContWindingSaveResponse>, t: Throwable) {
                            ResultDialog.error(this@ContWindingEntryActivity,
                                title = "Error", message = t.localizedMessage ?: "Network error")
                        }
                    })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
