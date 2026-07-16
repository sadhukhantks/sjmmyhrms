package com.example.myhrms

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myhrms.adapter.Spg1SummaryAdapter
import com.example.myhrms.api.*
import com.example.myhrms.ui.ResultDialog
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SpgDoffEntry1Activity : AppCompatActivity() {

    private val apiDate  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dispDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    private var branchId = 0
    private var userId   = 0
    private var entryDate = ""

    private lateinit var rootLayout: LinearLayout
    private lateinit var cardEntry: androidx.cardview.widget.CardView
    private lateinit var cardSummary: androidx.cardview.widget.CardView
    private lateinit var tvEntryDate: TextView
    private lateinit var spEntrySpell: Spinner
    private lateinit var llMechCodes: FlowLayout
    private lateinit var tvMechCodesStatus: TextView
    private lateinit var etTrollyNo: EditText
    private lateinit var tvTrollyInfo: TextView
    private lateinit var etNoOfSpindle: EditText
    private lateinit var etSpoolWt: EditText
    private lateinit var etGrossWt: EditText
    private lateinit var etTareWt: EditText
    private lateinit var etNetWt: EditText
    /** bobbin_weight for the selected machine; spool_wt = no_of_spindle * bobbin_weight. */
    private var bobbinWeight = 0
    private lateinit var btnSave: Button
    private lateinit var rvSummary: RecyclerView
    private lateinit var tvSummaryEmpty: TextView
    private lateinit var pbSummary: ProgressBar
    private lateinit var ivQualityShiftReport: ImageView
    private lateinit var ivPrintSummary: ImageView
    private lateinit var btnWinding: Button

    private val spellList = mutableListOf<Spell>()
    private lateinit var summaryAdapter: Spg1SummaryAdapter

    // Latest summary rows (quality-wise ordered) — used for printing.
    private val currentSummary = mutableListOf<Spg1SummaryRow>()

    // Selected machine from mech-code buttons
    private var selectedMcId: Int? = null
    private var selectedMechCode: Int? = null

    // Validated trolly
    private var validatedTrollyId: Int? = null
    private var validatedTareWt: Double = 0.0

    private val mainHandler = Handler(Looper.getMainLooper())
    private var trollyDebounce: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spg_doff_entry1)

        branchId = intent.getIntExtra("BRANCH_ID", 0)
        userId   = getSharedPreferences("LoginPrefs", MODE_PRIVATE).getInt("user_id", 0)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        rootLayout        = findViewById(R.id.rootLayout)
        cardEntry         = findViewById(R.id.cardEntry)
        cardSummary       = findViewById(R.id.cardSummary)
        tvEntryDate       = findViewById(R.id.tvEntryDate)
        spEntrySpell      = findViewById(R.id.spEntrySpell)
        llMechCodes       = findViewById(R.id.llMechCodes)
        tvMechCodesStatus = findViewById(R.id.tvMechCodesStatus)
        etTrollyNo        = findViewById(R.id.etTrollyNo)
        tvTrollyInfo      = findViewById(R.id.tvTrollyInfo)
        etNoOfSpindle     = findViewById(R.id.etNoOfSpindle)
        etSpoolWt         = findViewById(R.id.etSpoolWt)
        etGrossWt         = findViewById(R.id.etGrossWt)
        etTareWt          = findViewById(R.id.etTareWt)
        etNetWt           = findViewById(R.id.etNetWt)
        btnSave           = findViewById(R.id.btnSave)
        rvSummary         = findViewById(R.id.rvSummary)
        tvSummaryEmpty    = findViewById(R.id.tvSummaryEmpty)
        pbSummary         = findViewById(R.id.pbSummary)
        ivQualityShiftReport = findViewById(R.id.ivQualityShiftReport)
        ivPrintSummary       = findViewById(R.id.ivPrintSummary)
        btnWinding           = findViewById(R.id.btnWinding)

        summaryAdapter = Spg1SummaryAdapter { row -> showDetailDialog(row) }
        rvSummary.layoutManager = LinearLayoutManager(this)
        rvSummary.adapter = summaryAdapter
        rvSummary.isNestedScrollingEnabled = false

        // Report icon click listener
        ivQualityShiftReport.setOnClickListener {
            startActivity(android.content.Intent(this, HandsReportActivity::class.java).apply {
                putExtra("BRANCH_ID", branchId)
                putExtra("DATE", entryDate)
                putExtra("DATE_DISP", tvEntryDate.text.toString())
                putExtra("REPORT_TYPE", "spg1")
            })
        }

        ivPrintSummary.setOnClickListener { printSummaryPdf() }

        btnWinding.setOnClickListener {
            val intent = android.content.Intent(this, WindingEntryActivity::class.java)
            intent.putExtra("BRANCH_ID", branchId)
            startActivity(intent)
        }

        // Init date — 00:00–05:59 belongs to the previous day's shift → current date − 1
        // (applyDefaultSpell() refines this once spells load; this is the fallback.)
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.HOUR_OF_DAY) < 6) cal.add(Calendar.DAY_OF_MONTH, -1)
        entryDate = apiDate.format(cal.time)
        tvEntryDate.text = dispDate.format(cal.time)

        findViewById<View>(R.id.btnEntryDate).setOnClickListener { pickDate() }

        // Gross weight watcher → recalculate net
        etGrossWt.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { recalcNet() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // No of Spindle change → spool_wt = no_of_spindle * bobbin_weight, then tare
        etNoOfSpindle.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val n = etNoOfSpindle.text.toString().toIntOrNull() ?: 0
                etSpoolWt.setText((n * bobbinWeight).toString())
                recalcTare()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // Trolly No: debounced lookup
        etTrollyNo.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                resetTrolly()
                trollyDebounce?.let { mainHandler.removeCallbacks(it) }
                val v = s?.toString()?.trim().orEmpty()
                if (v.isEmpty()) return
                val r = Runnable { lookupTrolly(v) }
                trollyDebounce = r
                mainHandler.postDelayed(r, 500)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        etTrollyNo.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val v = etTrollyNo.text.toString().trim()
                if (v.isNotEmpty() && validatedTrollyId == null) lookupTrolly(v)
            }
        }

        // Spell change → reload mech codes
        spEntrySpell.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                applySpellBackground()
                loadMechCodes()
                loadSummary()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        btnSave.setOnClickListener { saveEntry() }

        loadSpells {
            loadMechCodes()
            loadSummary()
        }
    }

    // ── Date picker ─────────────────────────────────────────────────────────
    private fun pickDate() {
        val cal = Calendar.getInstance()
        try { apiDate.parse(entryDate)?.let { cal.time = it } } catch (_: Exception) {}
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            entryDate = apiDate.format(cal.time)
            tvEntryDate.text = dispDate.format(cal.time)
            loadMechCodes()
            loadSummary()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    // ── Spells ───────────────────────────────────────────────────────────────
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
                        this@SpgDoffEntry1Activity,
                        R.layout.spinner_item_black,
                        spellList.map { it.spellName ?: "" }
                    ).also { it.setDropDownViewResource(R.layout.spinner_dropdown_item_black) }
                    if (spellList.isNotEmpty()) applyDefaultSpell()
                    onDone()
                }
                override fun onFailure(call: Call<SpellResponse>, t: Throwable) { onDone() }
            })
    }

    // Default spell + production date based on current clock time:
    //   06:00–13:59 → A | 14:00–17:59 → B1 | 18:00–21:59 → B2
    //   22:00–23:59 & 00:00–05:59 → C (overnight shift → production date = current date − 1)
    private fun applyDefaultSpell() {
        val cal = Calendar.getInstance()
        val mins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val code = when (mins) {
            in 360..839   -> "A"    // 06:00–13:59
            in 840..1079  -> "B1"   // 14:00–17:59
            in 1080..1319 -> "B2"   // 18:00–21:59
            else          -> "C"    // 22:00–23:59 and 00:00–05:59
        }
        if (code == "C") cal.add(Calendar.DAY_OF_MONTH, -1)
        entryDate        = apiDate.format(cal.time)
        tvEntryDate.text = dispDate.format(cal.time)

        val idx = spellList.indexOfFirst { normalizeSpell(it.spellName) == code }
        spEntrySpell.setSelection(if (idx >= 0) idx else 0)
    }

    private fun normalizeSpell(name: String?): String =
        (name ?: "").uppercase().filter { it.isLetterOrDigit() }.removePrefix("SPELL")

    /** Tint the whole page (background + cards) based on the selected spell:
     *  A → light green, B1 → light blue, B2 → light orange, C → light grey. */
    private fun applySpellBackground() {
        val code = normalizeSpell(spellList.getOrNull(spEntrySpell.selectedItemPosition)?.spellName)
        val color = when (code) {
            "A"  -> "#DCEDC8"   // light green
            "B1" -> "#BBDEFB"   // light blue
            "B2" -> "#FFE0B2"   // light orange
            "C"  -> "#E0E0E0"   // light grey
            else -> "#F2F2F2"   // default
        }
        val parsed = Color.parseColor(color)
        rootLayout.setBackgroundColor(parsed)
        cardEntry.setCardBackgroundColor(parsed)
        cardSummary.setCardBackgroundColor(parsed)
    }

    private fun selectedSpellId(): Int? =
        spellList.getOrNull(spEntrySpell.selectedItemPosition)?.spellId

    // ── Mech Codes ───────────────────────────────────────────────────────────
    private fun loadMechCodes() {
        val spellId = selectedSpellId() ?: return
        if (branchId <= 0) return

        llMechCodes.removeAllViews()
        selectedMcId     = null
        selectedMechCode = null
        tvMechCodesStatus.visibility = View.VISIBLE
        tvMechCodesStatus.text = "Loading…"

        RetrofitClient.getApiService(this).getSpg1MechCodes(
            date     = entryDate,
            spellId  = spellId,
            branchId = branchId
        ).enqueue(object : Callback<Spg1MechCodesResponse> {
            override fun onResponse(call: Call<Spg1MechCodesResponse>, response: Response<Spg1MechCodesResponse>) {
                val codes = response.body()?.codes.orEmpty()
                if (codes.isEmpty()) {
                    tvMechCodesStatus.text = "No machines found for selected date/spell"
                    tvMechCodesStatus.visibility = View.VISIBLE
                } else {
                    tvMechCodesStatus.visibility = View.GONE
                    llMechCodes.removeAllViews()
                    for (code in codes) {
                        val rawLabel = code.mechPostingCode?.toString()
                            ?: code.mcCode?.takeIf { it.isNotBlank() }
                            ?: "MC${code.mcId}"
                        val label = rawLabel.take(3)   // max 3 chars
                        val btn = Button(this@SpgDoffEntry1Activity)
                        btn.text = label
                        btn.textSize = 11f
                        btn.setTypeface(null, Typeface.BOLD)
                        btn.setTextColor(Color.WHITE)
                        btn.setBackgroundColor(Color.parseColor("#1565C0"))  // blue default
                        val dp = resources.displayMetrics.density
                        val lp = LinearLayout.LayoutParams(
                            (44 * dp).toInt(),
                            (34 * dp).toInt()
                        )
                        lp.setMargins(0, 0, (6 * dp).toInt(), (6 * dp).toInt())
                        btn.layoutParams = lp
                        btn.setPadding(0, 0, 0, 0)
                        btn.tag = code
                        btn.setOnClickListener { onMechCodeSelected(btn, code) }
                        llMechCodes.addView(btn)
                    }
                }
            }
            override fun onFailure(call: Call<Spg1MechCodesResponse>, t: Throwable) {
                tvMechCodesStatus.text = "Failed to load mech codes"
                tvMechCodesStatus.visibility = View.VISIBLE
            }
        })
    }

    private fun onMechCodeSelected(btn: Button, code: Spg1MechCode) {
        selectedMcId     = code.mcId
        selectedMechCode = code.mechPostingCode

        // No of spindle + spool weight come from this machine's frame_details_mst
        fetchFrameSpindle(code.mcId)

        // Highlight selected=green, reset others=blue
        for (i in 0 until llMechCodes.childCount) {
            val child = llMechCodes.getChildAt(i) as? Button ?: continue
            if (child == btn) {
                child.setTextColor(Color.WHITE)
                child.setBackgroundColor(Color.parseColor("#2E7D32"))  // green selected
            } else {
                child.setTextColor(Color.WHITE)
                child.setBackgroundColor(Color.parseColor("#1565C0"))  // blue default
            }
        }
    }

    // ── Trolly lookup ─────────────────────────────────────────────────────────
    private fun resetTrolly() {
        validatedTrollyId = null
        validatedTareWt   = 0.0
        etTareWt.setText("")
        etNetWt.setText("")
        tvTrollyInfo.text = ""
        etTrollyNo.setBackgroundResource(R.drawable.bg_input_rounded_light)
    }

    private fun lookupTrolly(trollyNo: String) {
        RetrofitClient.getApiService(this).validateDoffTrolly(
            trollyNo = trollyNo,
            branchId = branchId.takeIf { it > 0 }
        ).enqueue(object : Callback<DoffTrollyValidateResponse> {
            override fun onResponse(call: Call<DoffTrollyValidateResponse>, response: Response<DoffTrollyValidateResponse>) {
                val body = response.body()
                if (response.isSuccessful && body?.status == "success" && body.trollyId != null) {
                    validatedTrollyId = body.trollyId
                    val tare = (body.trollyWeight ?: 0.0) + (body.bucketWeight ?: 0.0)
                    validatedTareWt = tare
                    etTrollyNo.setBackgroundResource(R.drawable.bg_input_valid)
                    tvTrollyInfo.text = (body.trollyName ?: "Valid") +
                        if (tare > 0) "  (Tare: ${fmt(tare)})" else ""
                    tvTrollyInfo.setTextColor(Color.parseColor("#2E7D32"))
                    recalcTare()   // tare = trolly tare + spool_wt/1000
                } else {
                    etTrollyNo.setBackgroundResource(R.drawable.bg_input_invalid)
                    tvTrollyInfo.text = body?.message ?: "Trolly not found"
                    tvTrollyInfo.setTextColor(Color.parseColor("#C62828"))
                }
            }
            override fun onFailure(call: Call<DoffTrollyValidateResponse>, t: Throwable) {
                etTrollyNo.setBackgroundResource(R.drawable.bg_input_invalid)
                tvTrollyInfo.text = "Lookup failed"
                tvTrollyInfo.setTextColor(Color.parseColor("#C62828"))
            }
        })
    }

    // ── Net weight calculation ────────────────────────────────────────────────
    private fun recalcNet() {
        val gross = etGrossWt.text.toString().toDoubleOrNull() ?: 0.0
        val tare  = etTareWt.text.toString().toDoubleOrNull()  ?: 0.0
        val net   = kotlin.math.round(gross - tare)
        etNetWt.setText(String.format(Locale.getDefault(), "%.0f", net))
        etNetWt.setTextColor(if (net < 0.0) Color.parseColor("#C62828") else Color.parseColor("#1E1E1E"))

        // Gross wt background feedback
        when {
            etGrossWt.text.toString().isBlank() ->
                etGrossWt.setBackgroundResource(R.drawable.bg_input_rounded_light)
            gross > 0.0 ->
                etGrossWt.setBackgroundResource(R.drawable.bg_input_valid)
            else ->
                etGrossWt.setBackgroundResource(R.drawable.bg_input_invalid)
        }
    }

    /** Tare = trolly tare + spool_wt/1000 (spool_wt = no_of_spindle * bobbin_weight). */
    private fun recalcTare() {
        val spool = etSpoolWt.text.toString().toDoubleOrNull() ?: 0.0
        etTareWt.setText(fmt(validatedTareWt + spool / 1000.0))
        recalcNet()
    }

    /** Fetch no_of_spindle + bobbin_weight for [mcId] from frame_details_mst and
     *  fill the fields. Editing No of Spindle recomputes spool_wt from the cached
     *  bobbin_weight. */
    private fun fetchFrameSpindle(mcId: Int) {
        RetrofitClient.getApiService(this).getDoffFrameSpindle(mcId)
            .enqueue(object : Callback<FrameSpindleResponse> {
                override fun onResponse(call: Call<FrameSpindleResponse>, response: Response<FrameSpindleResponse>) {
                    val body = response.body()
                    bobbinWeight = body?.bobbinWeight ?: 0
                    // Setting No of Spindle triggers its watcher → spool_wt + tare
                    etNoOfSpindle.setText((body?.noOfSpindle ?: 0).toString())
                }
                override fun onFailure(call: Call<FrameSpindleResponse>, t: Throwable) {
                    bobbinWeight = 0
                    etNoOfSpindle.setText("")
                    etSpoolWt.setText("")
                }
            })
    }

    private fun fmt(v: Double) = String.format(Locale.getDefault(), "%.3f", v)

    // Quality-wise shift-wise report values shown without decimals
    private fun fmtReport(v: Double) = String.format(Locale.getDefault(), "%.0f", v)

    // ── Save ─────────────────────────────────────────────────────────────────
    private fun saveEntry() {
        val spellId = selectedSpellId()
        if (spellId == null) { Toast.makeText(this, "Please select a spell", Toast.LENGTH_SHORT).show(); return }
        val mcId = selectedMcId
        if (mcId == null) { Toast.makeText(this, "Please select a Mech Posting Code", Toast.LENGTH_SHORT).show(); return }
        val trollyId = validatedTrollyId
        if (trollyId == null) { Toast.makeText(this, "Please enter a valid Trolly No", Toast.LENGTH_SHORT).show(); return }
        val gross = etGrossWt.text.toString().toDoubleOrNull()
        if (gross == null || gross <= 0.0) { Toast.makeText(this, "Please enter Gross Weight", Toast.LENGTH_SHORT).show(); return }
        val tare = etTareWt.text.toString().toDoubleOrNull() ?: 0.0
        val net  = kotlin.math.round(gross - tare)
        if (net < 0.0) { Toast.makeText(this, "Net Weight is negative. Cannot save.", Toast.LENGTH_LONG).show(); return }

        val req = DoffSaveRequest(
            id = null, doffDate = entryDate, spellId = spellId, mcId = mcId,
            qualityId = null, trollyId = trollyId, ebId = 0,
            grossWeight = gross, tareWeight = tare, netWeight = net,
            weightType = "SPG1", branchId = branchId.takeIf { it > 0 }, userId = userId
        )
        btnSave.isEnabled = false
        RetrofitClient.getApiService(this).saveDoffTransaction(req)
            .enqueue(object : Callback<DoffSaveResponse> {
                override fun onResponse(call: Call<DoffSaveResponse>, response: Response<DoffSaveResponse>) {
                    btnSave.isEnabled = true
                    if (response.isSuccessful && response.body()?.status == "success") {
                        ResultDialog.success(this@SpgDoffEntry1Activity, message = response.body()?.message ?: "Saved")
                        clearForm()
                        loadSummary()
                    } else {
                        ResultDialog.error(this@SpgDoffEntry1Activity, title = "Save Failed", message = response.body()?.message ?: "Save failed")
                    }
                }
                override fun onFailure(call: Call<DoffSaveResponse>, t: Throwable) {
                    btnSave.isEnabled = true
                    ResultDialog.error(this@SpgDoffEntry1Activity, title = "Error", message = t.localizedMessage ?: "Network error")
                }
            })
    }

    private fun clearForm() {
        etTrollyNo.setText(""); etGrossWt.setText(""); etTareWt.setText(""); etNetWt.setText("")
        etNoOfSpindle.setText(""); etSpoolWt.setText(""); bobbinWeight = 0
        etGrossWt.setBackgroundResource(R.drawable.bg_input_rounded_light)
        resetTrolly()
        // Deselect mech code buttons
        for (i in 0 until llMechCodes.childCount) {
            val child = llMechCodes.getChildAt(i) as? Button ?: continue
            child.setTextColor(Color.WHITE)
            child.setBackgroundColor(Color.parseColor("#1565C0"))
        }
        selectedMcId     = null
        selectedMechCode = null
    }

    // ── Summary list ──────────────────────────────────────────────────────────
    private fun loadSummary() {
        val spellId = selectedSpellId() ?: return
        if (branchId <= 0) return

        pbSummary.visibility    = View.VISIBLE
        tvSummaryEmpty.visibility = View.GONE
        rvSummary.visibility    = View.GONE

        RetrofitClient.getApiService(this).getSpg1Summary(
            date     = entryDate,
            spellId  = spellId,
            branchId = branchId
        ).enqueue(object : Callback<Spg1SummaryResponse> {
            override fun onResponse(call: Call<Spg1SummaryResponse>, response: Response<Spg1SummaryResponse>) {
                pbSummary.visibility = View.GONE
                // Order the summary quality-wise (then by machine within a quality).
                val list = response.body()?.summary.orEmpty().sortedWith(
                    compareBy({ it.qualityName?.lowercase(Locale.getDefault()) ?: "" },
                              { it.mechPostingCode ?: Int.MAX_VALUE },
                              { it.mcId })
                )
                currentSummary.clear()
                currentSummary.addAll(list)
                if (list.isEmpty()) {
                    tvSummaryEmpty.visibility = View.VISIBLE
                } else {
                    rvSummary.visibility = View.VISIBLE
                    summaryAdapter.update(list)
                }
            }
            override fun onFailure(call: Call<Spg1SummaryResponse>, t: Throwable) {
                pbSummary.visibility    = View.GONE
                tvSummaryEmpty.visibility = View.VISIBLE
                tvSummaryEmpty.text     = "Failed to load summary"
                currentSummary.clear()
            }
        })
    }

    // ── Print summary to PDF ─────────────────────────────────────────────────
    // Rows are already quality-wise ordered (see loadSummary). A subtotal row is
    // printed after each quality block, and a grand total at the very end.
    private fun printSummaryPdf() {
        if (currentSummary.isEmpty()) {
            ResultDialog.error(this, title = "Nothing to print", message = "No summary available")
            return
        }

        val spellName = spellList.getOrNull(spEntrySpell.selectedItemPosition)?.spellName ?: ""

        // Page geometry (A4 @ 72dpi ≈ 595 x 842)
        val pageW = 595; val pageH = 842
        val left = 30f; val right = pageW - 30f
        val rowH = 20f

        // Columns: Sl | Mc No | EB No | Emp Name | Quality | Weight | Doffs
        val xSl    = left
        val xMc    = left + 35f
        val xEb    = left + 95f
        val xName  = left + 165f
        val xQual  = left + 310f
        val xWt    = left + 440f
        val xDoff  = left + 515f

        val pdf = android.graphics.pdf.PdfDocument()
        val title  = android.graphics.Paint().apply { textSize = 14f; isFakeBoldText = true }
        val head   = android.graphics.Paint().apply { textSize = 10f; isFakeBoldText = true }
        val cell   = android.graphics.Paint().apply { textSize = 10f }
        val cellB  = android.graphics.Paint().apply { textSize = 10f; isFakeBoldText = true }
        val line   = android.graphics.Paint().apply { strokeWidth = 0.6f; color = Color.DKGRAY }

        var page = pdf.startPage(
            android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
        var canvas = page.canvas
        var y = 40f
        var pageNo = 1

        fun fmt(v: Double) = String.format(Locale.getDefault(), "%.0f", v)

        fun drawHeaderBlock() {
            canvas.drawText("Spg Doff Entry (1) — Summary", left, y, title); y += 18f
            canvas.drawText("Date: ${tvEntryDate.text}    Spell: $spellName", left, y, cell); y += 16f
            canvas.drawText("Sl", xSl, y, head)
            canvas.drawText("Mc No", xMc, y, head)
            canvas.drawText("EB No", xEb, y, head)
            canvas.drawText("Emp Name", xName, y, head)
            canvas.drawText("Quality", xQual, y, head)
            canvas.drawText("Weight", xWt, y, head)
            canvas.drawText("Doff", xDoff, y, head)
            y += 6f
            canvas.drawLine(left, y, right, y, line); y += 14f
        }

        fun newPage() {
            pdf.finishPage(page)
            pageNo += 1
            page = pdf.startPage(
                android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            canvas = page.canvas
            y = 40f
            drawHeaderBlock()
        }

        fun ensureSpace() { if (y > pageH - 60f) newPage() }

        drawHeaderBlock()

        var sl = 1
        var gWt = 0.0; var gDoff = 0
        var i = 0
        while (i < currentSummary.size) {
            val quality = currentSummary[i].qualityName ?: "-"
            var qWt = 0.0; var qDoff = 0

            while (i < currentSummary.size && (currentSummary[i].qualityName ?: "-") == quality) {
                val r = currentSummary[i]
                val mcNo = r.mechPostingCode?.toString()
                    ?: r.mcCode?.takeIf { it.isNotBlank() } ?: "${r.mcId}"
                ensureSpace()
                canvas.drawText(sl.toString(), xSl, y, cell)
                canvas.drawText(mcNo, xMc, y, cell)
                canvas.drawText(r.empCode?.takeIf { it.isNotBlank() } ?: "-", xEb, y, cell)
                canvas.drawText(r.empName?.takeIf { it.isNotBlank() } ?: "-", xName, y, cell)
                canvas.drawText(quality, xQual, y, cell)
                canvas.drawText(fmt(r.totalWt), xWt, y, cell)
                canvas.drawText(r.noOfDoff.toString(), xDoff, y, cell)
                y += rowH
                qWt += r.totalWt; qDoff += r.noOfDoff
                sl++; i++
            }

            // quality subtotal
            ensureSpace()
            canvas.drawLine(xQual, y - rowH + 4f, right, y - rowH + 4f, line)
            canvas.drawText("Total — $quality", xQual, y, cellB)
            canvas.drawText(fmt(qWt), xWt, y, cellB)
            canvas.drawText(qDoff.toString(), xDoff, y, cellB)
            y += rowH + 6f

            gWt += qWt; gDoff += qDoff
        }

        // grand total
        ensureSpace()
        canvas.drawLine(left, y - rowH + 4f, right, y - rowH + 4f, line)
        canvas.drawText("GRAND TOTAL", xName, y, cellB)
        canvas.drawText(fmt(gWt), xWt, y, cellB)
        canvas.drawText(gDoff.toString(), xDoff, y, cellB)
        canvas.drawLine(left, y + 5f, right, y + 5f, line)

        pdf.finishPage(page)

        try {
            val dir = java.io.File(cacheDir, "pdfs").apply { mkdirs() }
            val fileName = "SPG1_Summary_${entryDate.replace("-", "_")}.pdf"
            val file = java.io.File(dir, fileName)
            file.outputStream().use { pdf.writeTo(it) }
            pdf.close()

            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(android.content.Intent.createChooser(intent, "Open PDF"))
        } catch (e: Exception) {
            pdf.close()
            ResultDialog.error(this, title = "PDF error", message = e.localizedMessage)
        }
    }

    // ── Detail dialog on row click ────────────────────────────────────────────
    private fun showDetailDialog(row: Spg1SummaryRow) {
        val spellId = selectedSpellId() ?: return
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_spg1_detail, null)
        val tvTitle    = dialogView.findViewById<TextView>(R.id.tvDetailTitle)
        val rvDetail   = dialogView.findViewById<RecyclerView>(R.id.rvDetailList)

        val mcLabel = row.mechPostingCode?.toString()
            ?: row.mcCode?.takeIf { it.isNotBlank() }
            ?: "${row.mcId}"
        tvTitle.text = "Machine $mcLabel — Details"

        lateinit var reload: () -> Unit

        val detailAdapter = DetailRowAdapter(
            onEdit   = { t -> showEditWtDialog(t) { reload(); loadSummary() } },
            onDelete = { t -> confirmDelete(t)    { reload(); loadSummary() } }
        )
        rvDetail.layoutManager = LinearLayoutManager(this)
        rvDetail.adapter = detailAdapter

        // Fetch transactions for this mc_id / date / spell
        reload = {
            RetrofitClient.getApiService(this).getDoffTransactions(
                date     = entryDate,
                spellId  = spellId,
                branchId = branchId.takeIf { it > 0 },
                mcId     = row.mcId
            ).enqueue(object : Callback<DoffListResponse> {
                override fun onResponse(call: Call<DoffListResponse>, response: Response<DoffListResponse>) {
                    val list = response.body()?.transactions
                        ?.filter { it.weightType == "SPG1" }
                        .orEmpty()
                    detailAdapter.update(list)
                }
                override fun onFailure(call: Call<DoffListResponse>, t: Throwable) {}
            })
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .create()
            .show()
        reload()
    }

    /** Edit Trolly No + Gross Wt of a saved transaction. Like the entry screen,
     *  tare = trolly tare + spool part and net = round(gross − tare) recalc
     *  automatically; the spool part is kept from the stored tare. */
    private fun showEditWtDialog(t: DoffTransaction, onSaved: () -> Unit) {
        val recId  = t.id ?: return
        val mcId   = t.mcId ?: return
        val spell  = t.spellId ?: selectedSpellId() ?: return
        val dp     = resources.displayMetrics.density

        // spool_wt/1000 component of the stored tare; the trolly part is
        // replaced whenever a different trolly is validated.
        val spoolPart = (t.tareWeight ?: 0.0) -
            ((t.trollyWeight ?: 0.0) + (t.bucketWeight ?: 0.0))
        var trollyId: Int?     = t.trollyId
        var trollyTare: Double = (t.trollyWeight ?: 0.0) + (t.bucketWeight ?: 0.0)

        val etTrolly = EditText(this).apply {
            hint = "Trolly No"
            setBackgroundResource(R.drawable.bg_input_rounded_light)
        }
        val tvTrollyInfo = TextView(this).apply { textSize = 12f }
        val etGross = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Gross Wt"
            setText(t.grossWeight?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: "")
        }
        val tvCalc = TextView(this).apply { textSize = 13f }

        fun tare() = trollyTare + spoolPart
        fun recalc() {
            val g   = etGross.text.toString().toDoubleOrNull() ?: 0.0
            val net = kotlin.math.round(g - tare())
            tvCalc.text = "Tare: ${fmt(tare())}    Net: ${String.format(Locale.getDefault(), "%.0f", net)}"
            tvCalc.setTextColor(if (net < 0.0) Color.parseColor("#C62828") else Color.parseColor("#1E1E1E"))
        }

        // Trolly No: debounced lookup, same as the entry screen
        var debounce: Runnable? = null
        fun lookup(v: String) {
            RetrofitClient.getApiService(this).validateDoffTrolly(
                trollyNo = v, branchId = branchId.takeIf { it > 0 }
            ).enqueue(object : Callback<DoffTrollyValidateResponse> {
                override fun onResponse(call: Call<DoffTrollyValidateResponse>, response: Response<DoffTrollyValidateResponse>) {
                    val body = response.body()
                    if (response.isSuccessful && body?.status == "success" && body.trollyId != null) {
                        trollyId   = body.trollyId
                        trollyTare = (body.trollyWeight ?: 0.0) + (body.bucketWeight ?: 0.0)
                        etTrolly.setBackgroundResource(R.drawable.bg_input_valid)
                        tvTrollyInfo.text = (body.trollyName ?: "Valid") +
                            if (trollyTare > 0) "  (Tare: ${fmt(trollyTare)})" else ""
                        tvTrollyInfo.setTextColor(Color.parseColor("#2E7D32"))
                    } else {
                        etTrolly.setBackgroundResource(R.drawable.bg_input_invalid)
                        tvTrollyInfo.text = body?.message ?: "Trolly not found"
                        tvTrollyInfo.setTextColor(Color.parseColor("#C62828"))
                    }
                    recalc()
                }
                override fun onFailure(call: Call<DoffTrollyValidateResponse>, t2: Throwable) {
                    etTrolly.setBackgroundResource(R.drawable.bg_input_invalid)
                    tvTrollyInfo.text = "Lookup failed"
                    tvTrollyInfo.setTextColor(Color.parseColor("#C62828"))
                }
            })
        }
        etTrolly.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                trollyId = null
                debounce?.let { mainHandler.removeCallbacks(it) }
                val v = s?.toString()?.trim().orEmpty()
                if (v.isEmpty()) return
                val r = Runnable { lookup(v) }
                debounce = r
                mainHandler.postDelayed(r, 500)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        etGross.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { recalc() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        etTrolly.setText(t.trollyName ?: "")   // triggers lookup → re-validates
        recalc()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (10 * dp).toInt(), (20 * dp).toInt(), 0)
            addView(etTrolly)
            addView(tvTrollyInfo)
            addView(etGross)
            addView(tvCalc)
        }

        val dlg = AlertDialog.Builder(this)
            .setTitle("Edit Entry — MC ${t.mcCode?.takeIf { it.isNotBlank() } ?: mcId}")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Update", null)
            .create()
        dlg.show()
        // Manual click handler so failed validation keeps the dialog open
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val tid = trollyId
            if (tid == null) {
                Toast.makeText(this, "Please enter a valid Trolly No", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val gross = etGross.text.toString().toDoubleOrNull()
            if (gross == null || gross <= 0.0) {
                Toast.makeText(this, "Please enter Gross Weight", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val net = kotlin.math.round(gross - tare())
            if (net < 0.0) {
                Toast.makeText(this, "Net Weight is negative. Cannot update.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            dlg.dismiss()
            val req = DoffSaveRequest(
                id = recId, doffDate = t.doffDate ?: entryDate, spellId = spell, mcId = mcId,
                qualityId = t.qualityId, trollyId = tid, ebId = t.ebId ?: 0,
                grossWeight = gross, tareWeight = tare(), netWeight = net,
                weightType = "SPG1", branchId = branchId.takeIf { it > 0 }, userId = userId
            )
            RetrofitClient.getApiService(this).saveDoffTransaction(req)
                .enqueue(object : Callback<DoffSaveResponse> {
                    override fun onResponse(call: Call<DoffSaveResponse>, response: Response<DoffSaveResponse>) {
                        if (response.isSuccessful && response.body()?.status == "success") {
                            ResultDialog.success(this@SpgDoffEntry1Activity, message = "Updated")
                            onSaved()
                        } else {
                            ResultDialog.error(this@SpgDoffEntry1Activity, title = "Update Failed",
                                message = response.body()?.message ?: "Update failed")
                        }
                    }
                    override fun onFailure(call: Call<DoffSaveResponse>, t2: Throwable) {
                        ResultDialog.error(this@SpgDoffEntry1Activity, title = "Error",
                            message = t2.localizedMessage ?: "Network error")
                    }
                })
        }
    }

    private fun confirmDelete(t: DoffTransaction, onDeleted: () -> Unit) {
        val recId = t.id ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete Entry")
            .setMessage("Delete trolly ${t.trollyName ?: ""} (Net: ${t.netWeight?.let { Math.round(it) } ?: "-"})?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                RetrofitClient.getApiService(this).deleteDoffTransaction(recId)
                    .enqueue(object : Callback<DoffSaveResponse> {
                        override fun onResponse(call: Call<DoffSaveResponse>, response: Response<DoffSaveResponse>) {
                            if (response.isSuccessful && response.body()?.status == "success") {
                                ResultDialog.success(this@SpgDoffEntry1Activity, message = "Deleted")
                                onDeleted()
                            } else {
                                ResultDialog.error(this@SpgDoffEntry1Activity, title = "Delete Failed",
                                    message = response.body()?.message ?: "Delete failed")
                            }
                        }
                        override fun onFailure(call: Call<DoffSaveResponse>, t2: Throwable) {
                            ResultDialog.error(this@SpgDoffEntry1Activity, title = "Error",
                                message = t2.localizedMessage ?: "Network error")
                        }
                    })
            }
            .show()
    }

    // ── Inner adapter for detail dialog ───────────────────────────────────────
    inner class DetailRowAdapter(
        private val onEdit: (DoffTransaction) -> Unit,
        private val onDelete: (DoffTransaction) -> Unit
    ) : RecyclerView.Adapter<DetailRowAdapter.DVH>() {
        private val items = mutableListOf<DoffTransaction>()
        fun update(list: List<DoffTransaction>) { items.clear(); items.addAll(list); notifyDataSetChanged() }
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, vt: Int) =
            DVH(LayoutInflater.from(parent.context).inflate(R.layout.item_spg1_detail, parent, false))
        override fun onBindViewHolder(h: DVH, pos: Int) = h.bind(pos + 1, items[pos])

        inner class DVH(v: View) : RecyclerView.ViewHolder(v) {
            private val tvSeq     = v.findViewById<TextView>(R.id.tvSeq)
            private val tvTrolly  = v.findViewById<TextView>(R.id.tvTrollyNo)
            private val tvGross   = v.findViewById<TextView>(R.id.tvGrossWt)
            private val tvTare    = v.findViewById<TextView>(R.id.tvTareWt)
            private val tvNet     = v.findViewById<TextView>(R.id.tvNetWt)
            private val tvDoffTime = v.findViewById<TextView>(R.id.tvDoffTime)
            private val tvEdit    = v.findViewById<TextView>(R.id.tvEdit)
            private val tvDelete  = v.findViewById<TextView>(R.id.tvDelete)
            fun bind(seq: Int, t: DoffTransaction) {
                tvEdit.setOnClickListener { onEdit(t) }
                tvDelete.setOnClickListener { onDelete(t) }
                tvSeq.text    = seq.toString()
                tvTrolly.text = t.trollyName ?: "-"
                tvGross.text  = t.grossWeight?.let { fmt(it) } ?: "-"
                tvTare.text   = t.tareWeight?.let { fmt(it) } ?: "-"
                tvNet.text    = t.netWeight?.let { Math.round(it).toString() } ?: "-"
                // updated_date_time arrives as "YYYY-MM-DD HH:MM" — show only HH:MM
                tvDoffTime.text = t.updatedDateTime?.substringAfter(' ', "")
                    ?.takeIf { it.isNotBlank() } ?: "-"
            }
        }
    }

    // ── Quality-wise Shift-wise Report ────────────────────────────────────────
    private fun showQualityShiftReport() {
        if (branchId <= 0) {
            Toast.makeText(this, "Invalid branch", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_quality_shift_report, null)
        val tvReportTitle = dialogView.findViewById<TextView>(R.id.tvReportTitle)
        val rvReport = dialogView.findViewById<RecyclerView>(R.id.rvQualityShiftReport)
        val tvGrandTotalA = dialogView.findViewById<TextView>(R.id.tvGrandTotalA)
        val tvGrandTotalB = dialogView.findViewById<TextView>(R.id.tvGrandTotalB)
        val tvGrandTotalC = dialogView.findViewById<TextView>(R.id.tvGrandTotalC)
        val tvGrandTotal = dialogView.findViewById<TextView>(R.id.tvGrandTotal)
        val pbReport = dialogView.findViewById<ProgressBar>(R.id.pbReport)
        val tvReportEmpty = dialogView.findViewById<TextView>(R.id.tvReportEmpty)

        tvReportTitle.text = "SPG Doff Entry (1) - Quality Shift Report"

        val adapter = QualityShiftReportAdapter()
        rvReport.layoutManager = LinearLayoutManager(this)
        rvReport.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setNegativeButton("Close", null)
            .setPositiveButton("Print PDF") { _, _ ->
                // Export to PDF will be implemented here
                exportReportToPdf(adapter.getReportData(), tvGrandTotalA.text.toString(),
                    tvGrandTotalB.text.toString(), tvGrandTotalC.text.toString(), tvGrandTotal.text.toString())
            }
            .create()

        dialog.show()

        // Fetch report data
        pbReport.visibility = View.VISIBLE
        rvReport.visibility = View.GONE
        tvReportEmpty.visibility = View.GONE

        RetrofitClient.getApiService(this).getSpg1QualityShiftReport(
            date = entryDate,
            branchId = branchId
        ).enqueue(object : Callback<QualityShiftReportResponse> {
            override fun onResponse(call: Call<QualityShiftReportResponse>, response: Response<QualityShiftReportResponse>) {
                pbReport.visibility = View.GONE
                if (response.isSuccessful && response.body()?.status == "success") {
                    val reportData = response.body()?.report.orEmpty()
                    val grandTotal = response.body()?.grandTotal
                    
                    if (reportData.isEmpty()) {
                        tvReportEmpty.visibility = View.VISIBLE
                        tvReportEmpty.text = "No data for selected date"
                    } else {
                        rvReport.visibility = View.VISIBLE
                        adapter.update(reportData)
                        
                        // Update grand totals
                        tvGrandTotalA.text = fmtReport(grandTotal?.shiftA ?: 0.0)
                        tvGrandTotalB.text = fmtReport(grandTotal?.shiftB ?: 0.0)
                        tvGrandTotalC.text = fmtReport(grandTotal?.shiftC ?: 0.0)
                        tvGrandTotal.text = fmtReport(grandTotal?.total ?: 0.0)
                    }
                } else {
                    tvReportEmpty.visibility = View.VISIBLE
                    tvReportEmpty.text = response.body()?.message ?: "Failed to load report"
                }
            }

            override fun onFailure(call: Call<QualityShiftReportResponse>, t: Throwable) {
                pbReport.visibility = View.GONE
                tvReportEmpty.visibility = View.VISIBLE
                tvReportEmpty.text = "Error: ${t.localizedMessage}"
            }
        })
    }

    // ── Export Report to PDF ──────────────────────────────────────────────────
    private fun exportReportToPdf(reportData: List<QualityShiftReportRow>, 
                                  totalA: String, totalB: String, totalC: String, grandTotal: String) {
        try {
            // Create a simple text file for now (can be enhanced to actual PDF later)
            val fileName = "SPG1_Report_${entryDate.replace("-", "_")}.txt"
            val fileContent = buildString {
                appendLine("SPG DOFF ENTRY (1) - QUALITY SHIFT REPORT")
                appendLine("Date: $entryDate")
                appendLine("=" .repeat(60))
                appendLine()
                appendLine(String.format("%-20s %10s %10s %10s %10s", 
                    "Quality", "Shift A", "Shift B", "Shift C", "Total"))
                appendLine("-".repeat(60))
                
                reportData.forEach { row ->
                    appendLine(String.format("%-20s %10s %10s %10s %10s",
                        row.qualityName ?: "Unknown",
                        fmtReport(row.shiftA ?: 0.0),
                        fmtReport(row.shiftB ?: 0.0),
                        fmtReport(row.shiftC ?: 0.0),
                        fmtReport(row.total ?: 0.0)
                    ))
                }
                
                appendLine("-".repeat(60))
                appendLine(String.format("%-20s %10s %10s %10s %10s",
                    "GRAND TOTAL", totalA, totalB, totalC, grandTotal))
                appendLine("=" .repeat(60))
            }

            // Save to Downloads folder
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val file = java.io.File(downloadsDir, fileName)
            file.writeText(fileContent)

            Toast.makeText(this, "Report saved to Downloads/$fileName", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error exporting: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // ── Inner adapter for quality-shift report ────────────────────────────────
    inner class QualityShiftReportAdapter : RecyclerView.Adapter<QualityShiftReportAdapter.QSRVH>() {
        private val items = mutableListOf<QualityShiftReportRow>()
        
        fun update(list: List<QualityShiftReportRow>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }
        
        fun getReportData(): List<QualityShiftReportRow> = items.toList()
        
        override fun getItemCount() = items.size
        
        override fun onCreateViewHolder(parent: ViewGroup, vt: Int) =
            QSRVH(LayoutInflater.from(parent.context).inflate(R.layout.item_quality_shift_report, parent, false))
        
        override fun onBindViewHolder(h: QSRVH, pos: Int) = h.bind(items[pos])

        inner class QSRVH(v: View) : RecyclerView.ViewHolder(v) {
            private val tvQuality = v.findViewById<TextView>(R.id.tvQuality)
            private val tvShiftA  = v.findViewById<TextView>(R.id.tvShiftA)
            private val tvShiftB  = v.findViewById<TextView>(R.id.tvShiftB)
            private val tvShiftC  = v.findViewById<TextView>(R.id.tvShiftC)
            private val tvTotal   = v.findViewById<TextView>(R.id.tvTotal)
            
            fun bind(row: QualityShiftReportRow) {
                tvQuality.text = row.qualityName ?: "Unknown"
                tvShiftA.text  = fmtReport(row.shiftA ?: 0.0)
                tvShiftB.text  = fmtReport(row.shiftB ?: 0.0)
                tvShiftC.text  = fmtReport(row.shiftC ?: 0.0)
                tvTotal.text   = fmtReport(row.total ?: 0.0)
            }
        }
    }
}
