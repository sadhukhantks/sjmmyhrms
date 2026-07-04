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
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myhrms.adapter.QualityShiftReportAdapter
import com.example.myhrms.adapter.We2DetailAdapter
import com.example.myhrms.adapter.We2SummaryAdapter
import com.example.myhrms.api.*
import com.example.myhrms.ui.ResultDialog
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class WindingEntryActivity : AppCompatActivity() {

    private val apiDate  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dispDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    private var branchId  = 0
    private var userId    = 0
    private var entryDate = ""

    private lateinit var rootLayout   : LinearLayout
    private lateinit var cardEntry    : androidx.cardview.widget.CardView
    private lateinit var cardSummary  : androidx.cardview.widget.CardView
    private lateinit var tvEntryDate  : TextView
    private lateinit var spEntrySpell : Spinner
    private lateinit var llEmpButtons : FlowLayout
    private lateinit var tvEmpStatus  : TextView
    private lateinit var llEmpSpools  : LinearLayout
    private lateinit var llQualityButtons : FlowLayout
    private lateinit var tvQualityStatus  : TextView
    private lateinit var btnTypeS     : Button
    private lateinit var btnTypeC     : Button
    private lateinit var etTrollyNo   : EditText
    private lateinit var etNoOfSpools : EditText
    private lateinit var tvTrollyInfo : TextView
    private lateinit var llSpoolButtons : FlowLayout
    private lateinit var tvSpoolStatus  : TextView
    private lateinit var etGrossWt    : EditText
    private lateinit var etTareWt     : EditText
    private lateinit var etNetWt      : EditText
    private lateinit var btnSave      : Button
    private lateinit var rvSummary    : RecyclerView
    private lateinit var tvSummaryEmpty : TextView
    private lateinit var pbSummary    : ProgressBar
    private lateinit var ivQualityShiftReport : ImageView
    private lateinit var ivPrintSummary : ImageView
    private lateinit var btnSpinning : Button

    private val spellList = mutableListOf<Spell>()
    private lateinit var summaryAdapter: We2SummaryAdapter

    // Latest summary rows (quality-wise ordered) — used for printing.
    private val currentSummary = mutableListOf<We2GroupedRow>()

    // Selected employees (multi-select). Net weight is split equally across these on save.
    private val selectedEbIds = linkedSetOf<Long>()

    // Per-employee spool count (eb_id -> no of spools). Each selected employee
    // gets an editable spool input below the employee buttons; their sum is
    // shown in "No of Spools" and each value is saved on its own row.
    private val empSpoolCounts = linkedMapOf<Long, Int>()
    private val defaultSpoolsPerEmp = 8

    // S or C toggle — driven automatically by the selected qualities' spool_cop.
    private var selectedType: String = "S"

    // All employees loaded for the date/spell (full list before quality filter).
    private val allEmployees = mutableListOf<We2Employee>()

    // Winding qualities available for the date/spell, and the multi-selected set.
    // All selected qualities must share the same spool_cop (cannot mix S and C).
    private val qualityList = mutableListOf<We2Quality>()
    private val selectedQualityIds = linkedSetOf<Int>()

    // Validated trolly
    private var validatedTrollyId: Int?    = null
    private var validatedTareWt  : Double  = 0.0

    // Selected spool types (multi-select): trollyId -> per-spool weight.
    // Single selection → tare = trolly + spools × weight (tare read-only).
    // Multiple selection → tare = trolly weight only and the tare field is editable.
    private val selectedSpools = linkedMapOf<Int, Double>()

    private val mainHandler  = Handler(Looper.getMainLooper())
    private var trollyDebounce: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_winding_entry)

        branchId = intent.getIntExtra("BRANCH_ID", 0)
        userId   = getSharedPreferences("LoginPrefs", MODE_PRIVATE).getInt("user_id", 0)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        rootLayout     = findViewById(R.id.rootLayout)
        cardEntry      = findViewById(R.id.cardEntry)
        cardSummary    = findViewById(R.id.cardSummary)
        tvEntryDate    = findViewById(R.id.tvEntryDate)
        spEntrySpell   = findViewById(R.id.spEntrySpell)
        llEmpButtons   = findViewById(R.id.llEmpButtons)
        tvEmpStatus    = findViewById(R.id.tvEmpStatus)
        llEmpSpools    = findViewById(R.id.llEmpSpools)
        llQualityButtons = findViewById(R.id.llQualityButtons)
        tvQualityStatus  = findViewById(R.id.tvQualityStatus)
        btnTypeS       = findViewById(R.id.btnTypeS)
        btnTypeC       = findViewById(R.id.btnTypeC)
        etTrollyNo     = findViewById(R.id.etTrollyNo)
        etNoOfSpools   = findViewById(R.id.etNoOfSpools)
        tvTrollyInfo   = findViewById(R.id.tvTrollyInfo)
        llSpoolButtons = findViewById(R.id.llSpoolButtons)
        tvSpoolStatus  = findViewById(R.id.tvSpoolStatus)
        etGrossWt      = findViewById(R.id.etGrossWt)
        etTareWt       = findViewById(R.id.etTareWt)
        etNetWt        = findViewById(R.id.etNetWt)
        btnSave        = findViewById(R.id.btnSave)
        rvSummary      = findViewById(R.id.rvSummary)
        tvSummaryEmpty = findViewById(R.id.tvSummaryEmpty)
        pbSummary      = findViewById(R.id.pbSummary)
        ivQualityShiftReport = findViewById(R.id.ivQualityShiftReport)
        ivPrintSummary       = findViewById(R.id.ivPrintSummary)
        btnSpinning          = findViewById(R.id.btnSpinning)

        summaryAdapter = We2SummaryAdapter { row -> showDetailDialog(row) }
        rvSummary.layoutManager = LinearLayoutManager(this)
        rvSummary.adapter = summaryAdapter
        rvSummary.isNestedScrollingEnabled = false

        // Init date
        val cal = Calendar.getInstance()
        entryDate        = apiDate.format(cal.time)
        tvEntryDate.text = dispDate.format(cal.time)

        findViewById<View>(R.id.btnEntryDate).setOnClickListener { pickDate() }

        // S/C is auto-selected from the chosen qualities' spool_cop — no manual taps.
        btnTypeS.isClickable = false
        btnTypeC.isClickable = false
        selectType("S")

        // Trolly No debounced lookup (active for both S and C)
        etTrollyNo.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                resetTrolly()
                trollyDebounce?.let { mainHandler.removeCallbacks(it) }
                if (selectedType == "C") return   // (C): no trolly validation
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
            if (!hasFocus && selectedType != "C") {   // (C): no trolly validation
                val v = etTrollyNo.text.toString().trim()
                if (v.isNotEmpty() && validatedTrollyId == null) lookupTrolly(v)
            }
        }

        // Gross weight → recalc net
        etGrossWt.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { recalcNet() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // No of spools → recalc tare (trolly tare + spools × spool weight)
        etNoOfSpools.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { recalcTare() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // Manual tare edits (multiple spool types selected) → recalc net
        etTareWt.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { recalcNet() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // Spell change → reload employees + summary
        spEntrySpell.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                applySpellBackground()
                loadEmpButtons()
                loadQualityButtons()
                loadSummary()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        btnSave.setOnClickListener { saveEntry() }

        ivQualityShiftReport.setOnClickListener {
            startActivity(android.content.Intent(this, HandsReportActivity::class.java).apply {
                putExtra("BRANCH_ID", branchId)
                putExtra("DATE", entryDate)
                putExtra("DATE_DISP", tvEntryDate.text.toString())
                putExtra("REPORT_TYPE", "we2")
            })
        }

        ivPrintSummary.setOnClickListener { printSummaryPdf() }

        btnSpinning.setOnClickListener {
            val intent = android.content.Intent(this, SpgDoffEntry1Activity::class.java)
            intent.putExtra("BRANCH_ID", branchId)
            startActivity(intent)
        }

        loadSpoolTypes()

        loadSpells {
            loadEmpButtons()
            loadQualityButtons()
            loadSummary()
        }
    }

    // ── Date picker ──────────────────────────────────────────────────────────
    private fun pickDate() {
        val cal = Calendar.getInstance()
        try { apiDate.parse(entryDate)?.let { cal.time = it } } catch (_: Exception) {}
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            entryDate        = apiDate.format(cal.time)
            tvEntryDate.text = dispDate.format(cal.time)
            loadEmpButtons()
            loadQualityButtons()
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
                        this@WindingEntryActivity,
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

    // ── Employee buttons (mirrors loadMechCodes in SpgDoffEntry1Activity) ───
    private fun loadEmpButtons() {
        val spellId = selectedSpellId() ?: return
        if (branchId <= 0) return

        llEmpButtons.removeAllViews()
        allEmployees.clear()
        selectedEbIds.clear()
        tvEmpStatus.visibility = View.VISIBLE
        tvEmpStatus.text       = "Loading…"

        RetrofitClient.getApiService(this).getWe2Employees(
            date     = entryDate,
            spellId  = spellId,
            branchId = branchId
        ).enqueue(object : Callback<We2EmployeesResponse> {
            override fun onResponse(call: Call<We2EmployeesResponse>, response: Response<We2EmployeesResponse>) {
                allEmployees.clear()
                allEmployees.addAll(response.body()?.employees.orEmpty())
                renderEmployeeButtons()
            }
            override fun onFailure(call: Call<We2EmployeesResponse>, t: Throwable) {
                tvEmpStatus.text       = "Failed to load employees"
                tvEmpStatus.visibility = View.VISIBLE
            }
        })
    }

    /** Build the employee buttons, filtered to those mapped to the selected
     *  qualities. With no quality selected, all employees are shown. */
    private fun renderEmployeeButtons() {
        llEmpButtons.removeAllViews()
        val visible =
            if (selectedQualityIds.isEmpty()) allEmployees
            else allEmployees.filter { emp ->
                emp.qualityIds?.any { selectedQualityIds.contains(it) } == true
            }
        // Drop any selections that are no longer visible
        val visibleIds = visible.mapNotNull { it.ebId }.toSet()
        selectedEbIds.retainAll(visibleIds)

        if (visible.isEmpty()) {
            tvEmpStatus.text =
                if (allEmployees.isEmpty()) "No employees found for selected date/spell"
                else "No employees mapped to selected quality"
            tvEmpStatus.visibility = View.VISIBLE
        } else {
            tvEmpStatus.visibility = View.GONE
            val dp = resources.displayMetrics.density
            for (emp in visible) {
                val label = (emp.empName ?: emp.empCode ?: "?").take(7)
                val btn   = Button(this)
                btn.text     = label
                btn.textSize = 11f
                btn.setTypeface(null, Typeface.BOLD)
                btn.setTextColor(Color.WHITE)
                val on = emp.ebId != null && selectedEbIds.contains(emp.ebId)
                btn.setBackgroundColor(Color.parseColor(if (on) "#2E7D32" else "#1565C0"))
                val lp = LinearLayout.LayoutParams((52 * dp).toInt(), (36 * dp).toInt())
                lp.setMargins(0, 0, (6 * dp).toInt(), (6 * dp).toInt())
                btn.layoutParams = lp
                btn.setPadding(0, 0, 0, 0)
                btn.tag = emp
                btn.setOnClickListener { onEmpSelected(btn, emp) }
                llEmpButtons.addView(btn)
            }
        }
        recalcSpools()
    }

    private fun onEmpSelected(btn: Button, emp: We2Employee) {
        val ebId = emp.ebId ?: 0L
        if (ebId <= 0L) return
        // Toggle this employee; multiple can be selected at once
        if (selectedEbIds.contains(ebId)) {
            selectedEbIds.remove(ebId)
            empSpoolCounts.remove(ebId)
            btn.setBackgroundColor(Color.parseColor("#1565C0"))
        } else {
            selectedEbIds.add(ebId)
            empSpoolCounts.getOrPut(ebId) { defaultSpoolsPerEmp }
            btn.setBackgroundColor(Color.parseColor("#2E7D32"))
        }
        // Employee selection drives the per-employee spool inputs and the tare
        recalcSpools()
    }

    // Rebuild the per-employee spool inputs from the current selection, then
    // push their sum into "No of Spools" (which drives recalcTare and, for (C),
    // the gross weight).
    private fun recalcSpools() {
        rebuildEmpSpoolInputs()
        etNoOfSpools.setText(currentSpoolSum().toString())
        recalcTare()
    }

    private fun currentSpoolSum(): Int =
        selectedEbIds.sumOf { empSpoolCounts[it] ?: defaultSpoolsPerEmp }

    /** Build one labelled spool input per selected employee. Editing a value
     *  updates that employee's count and re-sums "No of Spools". */
    private fun rebuildEmpSpoolInputs() {
        // Drop any stored counts for employees no longer selected
        empSpoolCounts.keys.retainAll(selectedEbIds)

        llEmpSpools.removeAllViews()
        if (selectedEbIds.isEmpty()) return

        val dp = resources.displayMetrics.density
        for (ebId in selectedEbIds) {
            val emp   = allEmployees.firstOrNull { it.ebId == ebId }
            val label = (emp?.empName ?: emp?.empCode ?: ebId.toString())
            val count = empSpoolCounts.getOrPut(ebId) { defaultSpoolsPerEmp }

            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity     = android.view.Gravity.CENTER_VERTICAL
            val rowLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            rowLp.setMargins(0, 0, 0, (4 * dp).toInt())
            row.layoutParams = rowLp

            val tv = TextView(this)
            tv.text       = label
            tv.textSize   = 12f
            tv.setTextColor(Color.parseColor("#000000"))
            val tvLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            tv.layoutParams = tvLp

            val et = EditText(this)
            et.setText(count.toString())
            et.textSize  = 14f
            et.setTextColor(Color.parseColor("#1E1E1E"))
            et.inputType = android.text.InputType.TYPE_CLASS_NUMBER
            et.setBackgroundResource(R.drawable.bg_input_rounded_light)
            et.setPadding((10 * dp).toInt(), 0, (10 * dp).toInt(), 0)
            val etLp = LinearLayout.LayoutParams((80 * dp).toInt(), (40 * dp).toInt())
            et.layoutParams = etLp
            et.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    empSpoolCounts[ebId] = s?.toString()?.trim()?.toIntOrNull() ?: 0
                    etNoOfSpools.setText(currentSpoolSum().toString())
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })

            row.addView(tv)
            row.addView(et)
            llEmpSpools.addView(row)
        }
    }

    // ── Quality buttons (distinct winding qualities from the frame entry) ────
    private fun loadQualityButtons() {
        val spellId = selectedSpellId() ?: return
        if (branchId <= 0) return

        llQualityButtons.removeAllViews()
        qualityList.clear()
        selectedQualityIds.clear()
        tvQualityStatus.visibility = View.VISIBLE
        tvQualityStatus.setTextColor(Color.parseColor("#777777"))
        tvQualityStatus.text       = "Loading…"

        RetrofitClient.getApiService(this).getWe2Qualities(
            date     = entryDate,
            spellId  = spellId,
            branchId = branchId
        ).enqueue(object : Callback<We2QualitiesResponse> {
            override fun onResponse(call: Call<We2QualitiesResponse>, response: Response<We2QualitiesResponse>) {
                val qualities = response.body()?.qualities.orEmpty()
                qualityList.clear()
                qualityList.addAll(qualities)
                if (qualities.isEmpty()) {
                    tvQualityStatus.text       = "No qualities found for selected date/spell"
                    tvQualityStatus.visibility = View.VISIBLE
                } else {
                    tvQualityStatus.visibility = View.GONE
                    llQualityButtons.removeAllViews()
                    val dp = resources.displayMetrics.density
                    for (q in qualities) {
                        val btn = Button(this@WindingEntryActivity)
                        btn.text     = (q.qualityName ?: "?").take(14)
                        btn.textSize = 11f
                        btn.setTypeface(null, Typeface.BOLD)
                        btn.setTextColor(Color.WHITE)
                        btn.setBackgroundColor(Color.parseColor("#1565C0"))
                        val lp = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            (36 * dp).toInt()
                        )
                        lp.setMargins(0, 0, (6 * dp).toInt(), (6 * dp).toInt())
                        btn.layoutParams = lp
                        btn.setPadding((10 * dp).toInt(), 0, (10 * dp).toInt(), 0)
                        btn.tag = q
                        btn.setOnClickListener { onQualitySelected(q) }
                        llQualityButtons.addView(btn)
                    }
                }
            }
            override fun onFailure(call: Call<We2QualitiesResponse>, t: Throwable) {
                tvQualityStatus.text       = "Failed to load qualities"
                tvQualityStatus.visibility = View.VISIBLE
            }
        })
    }

    private fun spoolCopOf(qualityId: Int): String? =
        qualityList.firstOrNull { it.qualityId == qualityId }
            ?.spoolCop?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }

    /** The spool_cop ('S'/'C') of the currently selected quality. */
    private fun selectedSpoolCop(): String? =
        selectedQualityIds.firstOrNull()?.let { spoolCopOf(it) }

    // Single-select: tapping a quality selects only that one (replacing any
    // prior selection); tapping the selected one again clears it.
    // Selecting drives the auto S/C toggle and filters the employee buttons.
    private fun onQualitySelected(q: We2Quality) {
        if (selectedQualityIds.contains(q.qualityId)) {
            selectedQualityIds.clear()
        } else {
            selectedQualityIds.clear()
            selectedQualityIds.add(q.qualityId)
        }
        refreshQualityButtonColors()
        // Auto S/C from the selected spool_cop (default S when nothing selected)
        selectType(selectedSpoolCop() ?: "S", focusTrolly = false)
        // Show only employees mapped to the selected qualities
        renderEmployeeButtons()
        // Quality changed → refresh the summary (filtered by selected qualities)
        loadSummary()
    }

    private fun refreshQualityButtonColors() {
        for (i in 0 until llQualityButtons.childCount) {
            val child = llQualityButtons.getChildAt(i) as? Button ?: continue
            val tag   = child.tag as? We2Quality
            val on    = tag != null && selectedQualityIds.contains(tag.qualityId)
            child.setBackgroundColor(Color.parseColor(if (on) "#2E7D32" else "#1565C0"))
        }
    }

    // ── S/C toggle (auto-driven by quality spool_cop) ────────────────────────
    private fun selectType(type: String, focusTrolly: Boolean = true) {
        selectedType = type
        // No of Spools is the sum of the per-employee spool inputs → read-only.
        etNoOfSpools.isFocusable = false
        etNoOfSpools.isFocusableInTouchMode = false
        etNoOfSpools.isClickable = false
        if (type == "S") {
            btnTypeS.setBackgroundColor(Color.parseColor("#2E7D32"))
            btnTypeC.setBackgroundColor(Color.parseColor("#1565C0"))
            // (S): gross is weighed and entered manually
            setGrossEditable(true)
        } else {
            btnTypeS.setBackgroundColor(Color.parseColor("#1565C0"))
            btnTypeC.setBackgroundColor(Color.parseColor("#2E7D32"))
            // (C): gross = spools × 16 (auto), tare = 0, no trolly validation
            setGrossEditable(false)
            resetTrolly()
        }
        // Trolly No stays active for both S and C so the user can enter it
        etTrollyNo.isEnabled = true
        if (focusTrolly) etTrollyNo.requestFocus()
        // spools = employees × 8 (then recalcTare drives tare/gross)
        recalcSpools()
    }

    // ── Trolly lookup ────────────────────────────────────────────────────────
    private fun resetTrolly() {
        validatedTrollyId = null
        validatedTareWt   = 0.0
        tvTrollyInfo.text = ""
        etTrollyNo.setBackgroundResource(R.drawable.bg_input_rounded_light)
        recalcTare()
    }

    // ── Spool types (trolly_mst, trolly_type='P') ────────────────────────────
    private fun loadSpoolTypes() {
        if (branchId <= 0) return
        llSpoolButtons.removeAllViews()
        selectedSpools.clear()
        tvSpoolStatus.visibility = View.VISIBLE
        tvSpoolStatus.text       = "Loading…"

        RetrofitClient.getApiService(this).getWe2SpoolTypes(branchId.takeIf { it > 0 })
            .enqueue(object : Callback<We2SpoolTypesResponse> {
                override fun onResponse(call: Call<We2SpoolTypesResponse>, response: Response<We2SpoolTypesResponse>) {
                    val types = response.body()?.spoolTypes.orEmpty()
                    if (types.isEmpty()) {
                        tvSpoolStatus.text       = "No spool types found"
                        tvSpoolStatus.visibility = View.VISIBLE
                    } else {
                        tvSpoolStatus.visibility = View.GONE
                        llSpoolButtons.removeAllViews()
                        val dp = resources.displayMetrics.density
                        for (sp in types) {
                            val btn = Button(this@WindingEntryActivity)
                            btn.text     = (sp.trollyName ?: "?").take(14)
                            btn.textSize = 11f
                            btn.setTypeface(null, Typeface.BOLD)
                            btn.setTextColor(Color.WHITE)
                            btn.setBackgroundColor(Color.parseColor("#1565C0"))
                            val lp = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                (36 * dp).toInt()
                            )
                            lp.setMargins(0, 0, (6 * dp).toInt(), (6 * dp).toInt())
                            btn.layoutParams = lp
                            btn.setPadding((10 * dp).toInt(), 0, (10 * dp).toInt(), 0)
                            btn.tag = sp
                            btn.setOnClickListener { onSpoolSelected(sp) }
                            llSpoolButtons.addView(btn)
                        }
                    }
                }
                override fun onFailure(call: Call<We2SpoolTypesResponse>, t: Throwable) {
                    tvSpoolStatus.text       = "Failed to load spool types"
                    tvSpoolStatus.visibility = View.VISIBLE
                }
            })
    }

    private fun onSpoolSelected(sp: We2SpoolType) {
        val id = sp.trollyId ?: return
        // Multi-select toggle: tapping an active one clears it
        if (selectedSpools.containsKey(id)) selectedSpools.remove(id)
        else selectedSpools[id] = sp.spoolWeight ?: 0.0
        val selectedTypes = mutableListOf<We2SpoolType>()
        for (i in 0 until llSpoolButtons.childCount) {
            val b = llSpoolButtons.getChildAt(i) as? Button ?: continue
            val t = b.tag as? We2SpoolType
            val on = t?.trollyId != null && selectedSpools.containsKey(t.trollyId)
            b.setBackgroundColor(Color.parseColor(if (on) "#2E7D32" else "#1565C0"))
            if (on && t != null) selectedTypes.add(t)
        }

        // Show selected spool type weight(s), same style as the Trolly No info line
        if (selectedTypes.isEmpty()) {
            tvSpoolStatus.visibility = View.GONE
            tvSpoolStatus.text = ""
        } else {
            tvSpoolStatus.text = selectedTypes.joinToString("   ") {
                "${it.trollyName ?: "Spool"}  (Wt: ${fmt(it.spoolWeight ?: 0.0)})"
            }
            tvSpoolStatus.setTextColor(Color.parseColor("#2E7D32"))
            tvSpoolStatus.visibility = View.VISIBLE
        }

        recalcTare(seedMultiTare = true)
    }

    // Single spool → tare = validated trolly tare + (no of spools × spool weight), read-only.
    // Multiple spools → tare = trolly weight only and the field is editable for manual entry.
    private fun recalcTare(seedMultiTare: Boolean = false) {
        if (selectedType == "C") {
            // (C): No of Spools = employees × 8, Gross = spools × 16, Tare always 0.
            setTareEditable(false)
            val spools = etNoOfSpools.text.toString().trim().toIntOrNull() ?: 0
            etGrossWt.setText((spools * 16).toString())
            etTareWt.setText(fmt(0.0))
            recalcNet()
            return
        }
        if (selectedSpools.size >= 2) {
            setTareEditable(true)
            if (seedMultiTare) etTareWt.setText(fmt(validatedTareWt))
            recalcNet()
        } else {
            setTareEditable(false)
            val spools = etNoOfSpools.text.toString().trim().toIntOrNull() ?: 0
            val weight = selectedSpools.values.firstOrNull() ?: 0.0
            etTareWt.setText(fmt(validatedTareWt + (spools * weight)))
            recalcNet()
        }
    }

    private fun setTareEditable(editable: Boolean) {
        etTareWt.isEnabled              = editable
        etTareWt.isFocusable           = editable
        etTareWt.isFocusableInTouchMode = editable
    }

    private fun setGrossEditable(editable: Boolean) {
        etGrossWt.isEnabled              = editable
        etGrossWt.isFocusable           = editable
        etGrossWt.isFocusableInTouchMode = editable
    }

    private fun lookupTrolly(trollyNo: String) {
        RetrofitClient.getApiService(this).validateDoffTrollyWinding(
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
                    val info = (body.trollyName ?: "Valid") +
                        if (tare > 0) "  (Tare: ${fmt(tare)})" else ""
                    tvTrollyInfo.text = info
                    tvTrollyInfo.setTextColor(Color.parseColor("#2E7D32"))
                    recalcTare()
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

    // ── Net weight calculation ───────────────────────────────────────────────
    private fun recalcNet() {
        val gross = etGrossWt.text.toString().toDoubleOrNull() ?: 0.0
        val tare  = etTareWt.text.toString().toDoubleOrNull()  ?: 0.0
        val net   = kotlin.math.round(gross - tare)
        etNetWt.setText(String.format(Locale.getDefault(), "%.0f", net))
        etNetWt.setTextColor(
            if (net <= 0.0) Color.parseColor("#C62828") else Color.parseColor("#1E1E1E")
        )
        when {
            etGrossWt.text.toString().isBlank() ->
                etGrossWt.setBackgroundResource(R.drawable.bg_input_rounded_light)
            gross > 0.0 ->
                etGrossWt.setBackgroundResource(R.drawable.bg_input_valid)
            else ->
                etGrossWt.setBackgroundResource(R.drawable.bg_input_invalid)
        }
    }

    private fun fmt(v: Double) = String.format(Locale.getDefault(), "%.3f", v)

    // ── Save ─────────────────────────────────────────────────────────────────
    private fun saveEntry() {
        val spellId = selectedSpellId()
        if (spellId == null) {
            Toast.makeText(this, "Please select a spell", Toast.LENGTH_SHORT).show(); return
        }
        if (selectedEbIds.isEmpty()) {
            Toast.makeText(this, "Please select at least one Employee", Toast.LENGTH_SHORT).show(); return
        }
        val gross = etGrossWt.text.toString().toDoubleOrNull()
        if (gross == null || gross <= 0.0) {
            Toast.makeText(this, "Please enter Gross Weight", Toast.LENGTH_SHORT).show(); return
        }
        val tare = etTareWt.text.toString().toDoubleOrNull() ?: 0.0
        val net  = kotlin.math.round(gross - tare)
        if (net <= 0.0) {
            Toast.makeText(this, "Net Weight must be positive", Toast.LENGTH_LONG).show(); return
        }

        val ebIdList   = selectedEbIds.toList()
        // Per-employee spool counts, aligned to ebIdList; saved one per row.
        val spoolList  = ebIdList.map { empSpoolCounts[it] ?: defaultSpoolsPerEmp }
        val req = We2SaveRequest(
            date        = entryDate,
            spellId     = spellId,
            branchId    = branchId,
            ebId        = ebIdList.first(),
            ebIds       = ebIdList,
            ebSpools    = spoolList,
            scType      = selectedType,
            trollyId    = validatedTrollyId,
            // Store the quality only when one is selected; with several picked,
            // the per-employee mapping in the frame entry already resolves it.
            qualityId   = selectedQualityIds.singleOrNull(),
            grossWeight = gross,
            tareWeight  = tare,
            netWeight   = net,
            noOfSpools  = spoolList.sum(),
            noOfSpindle = if (selectedType == "S") selectedEbIds.size * 8 else null,
            userId      = userId
        )
        btnSave.isEnabled = false
        RetrofitClient.getApiService(this).saveWe2(req)
            .enqueue(object : Callback<We2SaveResponse> {
                override fun onResponse(call: Call<We2SaveResponse>, response: Response<We2SaveResponse>) {
                    btnSave.isEnabled = true
                    if (response.isSuccessful && response.body()?.status == "success") {
                        ResultDialog.success(
                            this@WindingEntryActivity,
                            message = response.body()?.message ?: "Saved successfully"
                        )
                        clearForm()
                        loadSummary()
                    } else {
                        ResultDialog.error(
                            this@WindingEntryActivity,
                            title   = "Save Failed",
                            message = response.body()?.message ?: "Could not save the entry"
                        )
                    }
                }
                override fun onFailure(call: Call<We2SaveResponse>, t: Throwable) {
                    btnSave.isEnabled = true
                    ResultDialog.error(
                        this@WindingEntryActivity,
                        title   = "Error",
                        message = t.localizedMessage ?: "Network error"
                    )
                }
            })
    }

    private fun clearForm() {
        selectedEbIds.clear()
        empSpoolCounts.clear()
        llEmpSpools.removeAllViews()
        etGrossWt.setText("")
        etTareWt.setText("")
        etNetWt.setText("")
        etGrossWt.setBackgroundResource(R.drawable.bg_input_rounded_light)
        // Clear spool selection + count
        etNoOfSpools.setText("")
        selectedSpools.clear()
        setTareEditable(false)
        for (i in 0 until llSpoolButtons.childCount) {
            (llSpoolButtons.getChildAt(i) as? Button)
                ?.setBackgroundColor(Color.parseColor("#1565C0"))
        }
        etTrollyNo.setText("")
        resetTrolly()
        // Deselect quality buttons → reset type to S and show all employees again
        selectedQualityIds.clear()
        refreshQualityButtonColors()
        selectType("S")
        renderEmployeeButtons()
    }

    // ── Summary list (grouped by employee) ──────────────────────────────────
    private fun loadSummary() {
        val spellId = selectedSpellId() ?: return
        if (branchId <= 0) return

        pbSummary.visibility      = View.VISIBLE
        tvSummaryEmpty.visibility = View.GONE
        rvSummary.visibility      = View.GONE

        // When qualities are selected, filter the summary by them; otherwise
        // the summary is based on date+spell only.
        val qualityCsv = selectedQualityIds.joinToString(",").ifEmpty { null }

        RetrofitClient.getApiService(this).getWe2GroupedSummary(
            date      = entryDate,
            spellId   = spellId,
            branchId  = branchId,
            qualityId = qualityCsv
        ).enqueue(object : Callback<We2GroupedResponse> {
            override fun onResponse(call: Call<We2GroupedResponse>, response: Response<We2GroupedResponse>) {
                pbSummary.visibility = View.GONE
                // Order the summary quality-wise (then by employee within a quality).
                val list = response.body()?.summary.orEmpty().sortedWith(
                    compareBy({ it.qualityName?.lowercase(Locale.getDefault()) ?: "" },
                              { (it.empName ?: it.empCode ?: "").lowercase(Locale.getDefault()) })
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
            override fun onFailure(call: Call<We2GroupedResponse>, t: Throwable) {
                pbSummary.visibility      = View.GONE
                tvSummaryEmpty.visibility = View.VISIBLE
                tvSummaryEmpty.text       = "Failed to load summary"
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

        val spellName = (spEntrySpell.selectedItem as? Spell)?.spellName ?: ""

        // Page geometry (A4 @ 72dpi ≈ 595 x 842)
        val pageW = 595; val pageH = 842
        val left = 30f; val right = pageW - 30f
        val rowH = 20f

        // Columns: Sl | Emp Name | EB No | Quality | Weight | Spools | Doffs
        val xSl    = left
        val xName  = left + 35f
        val xEb     = left + 175f
        val xQual  = left + 250f
        val xWt    = left + 370f
        val xSpool = left + 455f
        val xDoff  = left + 510f

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
            canvas.drawText("Winding Entry — Summary", left, y, title); y += 18f
            canvas.drawText("Date: ${tvEntryDate.text}    Spell: $spellName", left, y, cell); y += 16f
            // column headers
            canvas.drawText("Sl", xSl, y, head)
            canvas.drawText("Emp Name", xName, y, head)
            canvas.drawText("EB No", xEb, y, head)
            canvas.drawText("Quality", xQual, y, head)
            canvas.drawText("Weight", xWt, y, head)
            canvas.drawText("Spls", xSpool, y, head)
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
        var gWt = 0.0; var gSpools = 0; var gDoff = 0
        var i = 0
        while (i < currentSummary.size) {
            val quality = currentSummary[i].qualityName ?: "-"
            var qWt = 0.0; var qSpools = 0; var qDoff = 0

            // rows for this quality block
            while (i < currentSummary.size && (currentSummary[i].qualityName ?: "-") == quality) {
                val r = currentSummary[i]
                ensureSpace()
                canvas.drawText(sl.toString(), xSl, y, cell)
                canvas.drawText(r.empName?.takeIf { it.isNotBlank() } ?: r.empCode ?: "-", xName, y, cell)
                canvas.drawText(r.empCode ?: "-", xEb, y, cell)
                canvas.drawText(quality, xQual, y, cell)
                canvas.drawText(fmt(r.totalWt), xWt, y, cell)
                canvas.drawText(r.noOfSpools.toString(), xSpool, y, cell)
                canvas.drawText(r.noOfDoff.toString(), xDoff, y, cell)
                y += rowH
                qWt += r.totalWt; qSpools += r.noOfSpools; qDoff += r.noOfDoff
                sl++; i++
            }

            // quality subtotal
            ensureSpace()
            canvas.drawLine(xQual, y - rowH + 4f, right, y - rowH + 4f, line)
            canvas.drawText("Total — $quality", xQual, y, cellB)
            canvas.drawText(fmt(qWt), xWt, y, cellB)
            canvas.drawText(qSpools.toString(), xSpool, y, cellB)
            canvas.drawText(qDoff.toString(), xDoff, y, cellB)
            y += rowH + 6f

            gWt += qWt; gSpools += qSpools; gDoff += qDoff
        }

        // grand total
        ensureSpace()
        canvas.drawLine(left, y - rowH + 4f, right, y - rowH + 4f, line)
        canvas.drawText("GRAND TOTAL", xName, y, cellB)
        canvas.drawText(fmt(gWt), xWt, y, cellB)
        canvas.drawText(gSpools.toString(), xSpool, y, cellB)
        canvas.drawText(gDoff.toString(), xDoff, y, cellB)
        canvas.drawLine(left, y + 5f, right, y + 5f, line)

        pdf.finishPage(page)

        try {
            val dir = java.io.File(cacheDir, "pdfs").apply { mkdirs() }
            val fileName = "Winding_Summary_${entryDate.replace("-", "_")}.pdf"
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

    // ── Detail dialog (on row click) ─────────────────────────────────────────
    private fun showDetailDialog(row: We2GroupedRow) {
        val spellId = selectedSpellId() ?: return
        val ebId    = row.ebId ?: return

        val dialogView = layoutInflater.inflate(R.layout.dialog_we2_detail, null)
        val tvTitle    = dialogView.findViewById<TextView>(R.id.tvDetailTitle)
        val rv         = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvDetailList)

        tvTitle.text = "${row.empName ?: row.empCode}  –  ${row.noOfDoff} doffs  |  Total: ${
            String.format(Locale.getDefault(), "%.3f", row.totalWt)
        } kg"

        val detailAdapter = We2DetailAdapter()
        rv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rv.adapter       = detailAdapter
        rv.isNestedScrollingEnabled = false

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setNegativeButton("Close", null)
            .create()
        dialog.show()

        RetrofitClient.getApiService(this).getWe2Details(
            date     = entryDate,
            spellId  = spellId,
            branchId = branchId,
            ebId     = ebId
        ).enqueue(object : Callback<We2SummaryResponse> {
            override fun onResponse(call: Call<We2SummaryResponse>, response: Response<We2SummaryResponse>) {
                detailAdapter.update(response.body()?.summary.orEmpty())
            }
            override fun onFailure(call: Call<We2SummaryResponse>, t: Throwable) {}
        })
    }

    private fun confirmDelete(row: We2SummaryRow) {
        val id = row.id ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete entry?")
            .setMessage("Remove ${row.empName ?: row.empCode} (Net: ${row.netWeight?.let { Math.round(it) } ?: "-"} kg)?")
            .setPositiveButton("Delete") { _, _ ->
                RetrofitClient.getApiService(this).deleteWe2(id)
                    .enqueue(object : Callback<We2SaveResponse> {
                        override fun onResponse(call: Call<We2SaveResponse>, response: Response<We2SaveResponse>) {
                            Toast.makeText(this@WindingEntryActivity,
                                response.body()?.message ?: "Deleted", Toast.LENGTH_SHORT).show()
                            loadSummary()
                        }
                        override fun onFailure(call: Call<We2SaveResponse>, t: Throwable) {
                            Toast.makeText(this@WindingEntryActivity,
                                "Error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Quality-wise Shift-wise Report ──────────────────────────────────────
    private fun showQualityShiftReport() {
        if (branchId <= 0) {
            Toast.makeText(this, "Branch ID not available", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_quality_shift_report, null)
        val tvReportTitle = dialogView.findViewById<TextView>(R.id.tvReportTitle)
        val rvReport = dialogView.findViewById<RecyclerView>(R.id.rvQualityShiftReport)
        val tvGrandTotalA = dialogView.findViewById<TextView>(R.id.tvGrandTotalA)
        val tvGrandTotalB = dialogView.findViewById<TextView>(R.id.tvGrandTotalB)
        val tvGrandTotalC = dialogView.findViewById<TextView>(R.id.tvGrandTotalC)
        val tvGrandTotal = dialogView.findViewById<TextView>(R.id.tvGrandTotal)
        val pbReport = dialogView.findViewById<ProgressBar>(R.id.pbReport)
        val tvReportEmpty = dialogView.findViewById<TextView>(R.id.tvReportEmpty)

        tvReportTitle.text = "Quality-wise Shift-wise Production Report\n${tvEntryDate.text}"

        val reportAdapter = QualityShiftReportAdapter()
        rvReport.layoutManager = LinearLayoutManager(this)
        rvReport.adapter = reportAdapter
        rvReport.isNestedScrollingEnabled = false

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setNegativeButton("Close", null)
            .create()
        dialog.show()

        // Fetch report data
        pbReport.visibility = View.VISIBLE
        tvReportEmpty.visibility = View.GONE
        rvReport.visibility = View.GONE

        RetrofitClient.getApiService(this).getWe2QualityShiftReport(
            date = entryDate,
            branchId = branchId
        ).enqueue(object : Callback<QualityShiftReportResponse> {
            override fun onResponse(call: Call<QualityShiftReportResponse>, response: Response<QualityShiftReportResponse>) {
                pbReport.visibility = View.GONE
                val body = response.body()
                val report = body?.report.orEmpty()
                
                if (report.isEmpty()) {
                    tvReportEmpty.visibility = View.VISIBLE
                } else {
                    rvReport.visibility = View.VISIBLE
                    reportAdapter.update(report)
                    
                    // Update grand total
                    val grandTotal = body?.grandTotal
                    tvGrandTotalA.text = grandTotal?.shiftA?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "0"
                    tvGrandTotalB.text = grandTotal?.shiftB?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "0"
                    tvGrandTotalC.text = grandTotal?.shiftC?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "0"
                    tvGrandTotal.text = grandTotal?.total?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "0"
                }
            }
            override fun onFailure(call: Call<QualityShiftReportResponse>, t: Throwable) {
                pbReport.visibility = View.GONE
                tvReportEmpty.visibility = View.VISIBLE
                tvReportEmpty.text = "Failed to load report: ${t.localizedMessage}"
            }
        })
    }
}

