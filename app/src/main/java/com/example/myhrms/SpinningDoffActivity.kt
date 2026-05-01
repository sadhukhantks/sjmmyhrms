package com.example.myhrms

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myhrms.adapter.DoffTransactionAdapter
import com.example.myhrms.api.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SpinningDoffActivity : AppCompatActivity() {

    private val apiDate  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dispDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    private var branchId = 0
    private var userId   = 0
    private var filterDate = ""

    private lateinit var adapter: DoffTransactionAdapter
    private lateinit var rvDoff: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var tvFilterDate: TextView
    private lateinit var spFilterSpell: Spinner

    private val spellList    = mutableListOf<Spell>()
    private val filterSpells = mutableListOf<Spell>()
    private val machineList  = mutableListOf<DoffMachine>()
    private val qualityList  = mutableListOf<SpinningQuality>()
    private val trollyList   = mutableListOf<Trolly>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spinning_doff_entry)

        branchId = intent.getIntExtra("BRANCH_ID", 0)
        userId   = getSharedPreferences("LoginPrefs", MODE_PRIVATE).getInt("user_id", 0)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        rvDoff        = findViewById(R.id.rvDoff)
        progressBar   = findViewById(R.id.progressBar)
        tvEmpty       = findViewById(R.id.tvEmpty)
        tvFilterDate  = findViewById(R.id.tvFilterDate)
        spFilterSpell = findViewById(R.id.spFilterSpell)

        val cal = Calendar.getInstance()
        filterDate = apiDate.format(cal.time)
        tvFilterDate.text = dispDate.format(cal.time)

        findViewById<View>(R.id.btnFilterDate).setOnClickListener { pickFilterDate() }

        adapter = DoffTransactionAdapter(emptyList(),
            onEdit   = { showDoffDialog(it) },
            onDelete = { confirmDelete(it) })
        rvDoff.layoutManager = LinearLayoutManager(this)
        rvDoff.adapter = adapter

        findViewById<Button>(R.id.btnSearch).setOnClickListener { reloadList() }
        findViewById<Button>(R.id.btnAddNew).setOnClickListener { showDoffDialog(null) }

        loadSpells { loadMasters { reloadList() } }
    }

    private fun pickFilterDate() {
        val cal = Calendar.getInstance()
        try { apiDate.parse(filterDate)?.let { cal.time = it } } catch (_: Exception) {}
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            filterDate = apiDate.format(cal.time)
            tvFilterDate.text = dispDate.format(cal.time)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun reloadList() {
        val pos = spFilterSpell.selectedItemPosition
        val spellId = if (pos <= 0) null else filterSpells.getOrNull(pos)?.spellId
        loadDoffs(filterDate, spellId)
    }

    private fun loadDoffs(date: String, spellId: Int?) {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility     = View.GONE
        rvDoff.visibility      = View.GONE

        RetrofitClient.getApiService(this).getDoffTransactions(
            date     = date,
            spellId  = spellId,
            branchId = if (branchId > 0) branchId else null
        ).enqueue(object : Callback<DoffListResponse> {
            override fun onResponse(call: Call<DoffListResponse>, response: Response<DoffListResponse>) {
                progressBar.visibility = View.GONE
                val list = response.body()?.transactions.orEmpty()
                if (list.isEmpty()) tvEmpty.visibility = View.VISIBLE
                else { rvDoff.visibility = View.VISIBLE; adapter.update(list) }
            }
            override fun onFailure(call: Call<DoffListResponse>, t: Throwable) {
                progressBar.visibility = View.GONE
                tvEmpty.visibility = View.VISIBLE
                Toast.makeText(this@SpinningDoffActivity,
                    "Error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun loadSpells(onDone: () -> Unit) {
        RetrofitClient.getApiService(this).getSpells(branchId.takeIf { it > 0 })
            .enqueue(object : Callback<SpellResponse> {
                override fun onResponse(call: Call<SpellResponse>, response: Response<SpellResponse>) {
                    response.body()?.spells?.let { spellList.clear(); spellList.addAll(it) }
                    filterSpells.clear()
                    filterSpells.add(Spell(0, "All Spells"))
                    filterSpells.addAll(spellList)
                    spFilterSpell.adapter = ArrayAdapter(
                        this@SpinningDoffActivity,
                        R.layout.spinner_item_black,
                        filterSpells.map { it.spellName ?: "" }
                    ).also { it.setDropDownViewResource(R.layout.spinner_dropdown_item_black) }
                    onDone()
                }
                override fun onFailure(call: Call<SpellResponse>, t: Throwable) {
                    Toast.makeText(this@SpinningDoffActivity,
                        "Error loading spells: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                    onDone()
                }
            })
    }

    private fun loadMasters(onAllDone: () -> Unit) {
        var done = 0
        fun finish() { done++; if (done == 3) onAllDone() }

        RetrofitClient.getApiService(this).getDoffMachines(
            branchId = if (branchId > 0) branchId else null
        ).enqueue(object : Callback<DoffMachineResponse> {
            override fun onResponse(call: Call<DoffMachineResponse>, response: Response<DoffMachineResponse>) {
                response.body()?.machines?.let { machineList.clear(); machineList.addAll(it) }
                finish()
            }
            override fun onFailure(call: Call<DoffMachineResponse>, t: Throwable) { finish() }
        })

        RetrofitClient.getApiService(this).getDoffQualities(
            branchId = if (branchId > 0) branchId else null
        ).enqueue(object : Callback<SpinningQualityResponse> {
            override fun onResponse(call: Call<SpinningQualityResponse>, response: Response<SpinningQualityResponse>) {
                response.body()?.qualities?.let { qualityList.clear(); qualityList.addAll(it) }
                finish()
            }
            override fun onFailure(call: Call<SpinningQualityResponse>, t: Throwable) { finish() }
        })

        RetrofitClient.getApiService(this).getDoffTrollies(
            branchId = if (branchId > 0) branchId else null
        ).enqueue(object : Callback<TrollyResponse> {
            override fun onResponse(call: Call<TrollyResponse>, response: Response<TrollyResponse>) {
                response.body()?.trollies?.let { trollyList.clear(); trollyList.addAll(it) }
                finish()
            }
            override fun onFailure(call: Call<TrollyResponse>, t: Throwable) { finish() }
        })
    }

    private fun showDoffDialog(existing: DoffTransaction?) {
        if (spellList.isEmpty() || machineList.isEmpty()) {
            Toast.makeText(this, "Masters still loading… try again in a moment",
                Toast.LENGTH_SHORT).show()
            return
        }
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_spinning_doff_entry, null)

        val tvDate    = view.findViewById<TextView>(R.id.tvDialogDate)
        val btnDate   = view.findViewById<View>(R.id.btnDialogDate)
        val spSpell   = view.findViewById<Spinner>(R.id.spDialogSpell)
        val spMachine = view.findViewById<Spinner>(R.id.spDialogMachine)
        val spQuality = view.findViewById<Spinner>(R.id.spDialogQuality)
        val spTrolly  = view.findViewById<Spinner>(R.id.spDialogTrolly)
        val etEmpCode = view.findViewById<EditText>(R.id.etDialogEmpCode)
        val tvEmpName = view.findViewById<TextView>(R.id.tvDialogEmpName)
        val btnSearchEmp = view.findViewById<ImageView>(R.id.btnSearchEmp)
        val etGross   = view.findViewById<EditText>(R.id.etGrossWeight)
        val etTare    = view.findViewById<EditText>(R.id.etTareWeight)
        val etNet     = view.findViewById<EditText>(R.id.etNetWeight)

        spSpell.adapter = ArrayAdapter(this, R.layout.spinner_item_black,
            spellList.map { it.spellName ?: "" })
            .also { it.setDropDownViewResource(R.layout.spinner_dropdown_item_black) }
        spMachine.adapter = ArrayAdapter(this, R.layout.spinner_item_black,
            machineList.map { it.toString() })
            .also { it.setDropDownViewResource(R.layout.spinner_dropdown_item_black) }
        spQuality.adapter = ArrayAdapter(this, R.layout.spinner_item_black,
            qualityList.map { it.qualityName ?: "" })
            .also { it.setDropDownViewResource(R.layout.spinner_dropdown_item_black) }
        spTrolly.adapter = ArrayAdapter(this, R.layout.spinner_item_black,
            trollyList.map { it.toString() })
            .also { it.setDropDownViewResource(R.layout.spinner_dropdown_item_black) }

        var dialogDate = existing?.doffDate ?: filterDate
        tvDate.text = safeReformat(dialogDate)

        val initSpellId = existing?.spellId
            ?: (if (spFilterSpell.selectedItemPosition > 0)
                    filterSpells.getOrNull(spFilterSpell.selectedItemPosition)?.spellId
                else spellList.firstOrNull()?.spellId)
        initSpellId?.let { id ->
            val idx = spellList.indexOfFirst { it.spellId == id }
            if (idx >= 0) spSpell.setSelection(idx)
        }

        existing?.mcId?.let { id ->
            val idx = machineList.indexOfFirst { it.mcId == id }
            if (idx >= 0) spMachine.setSelection(idx)
        }

        var dialogEbId = existing?.ebId ?: 0

        fun selectedTrolly(): Trolly? = trollyList.getOrNull(spTrolly.selectedItemPosition)

        fun recalcTareAndNet() {
            val t = selectedTrolly()
            val tare = (t?.trollyWeight ?: 0.0) + (t?.bucketWeight ?: 0.0)
            etTare.setText(String.format(Locale.getDefault(), "%.3f", tare))
            val gross = etGross.text.toString().toDoubleOrNull() ?: 0.0
            val net = gross - tare
            etNet.setText(String.format(Locale.getDefault(), "%.3f", net))
        }

        fun applyQualityById(qid: Int?) {
            qid?.let {
                val idx = qualityList.indexOfFirst { q -> q.qualityId == it }
                if (idx >= 0) spQuality.setSelection(idx)
            }
        }
        fun applyTrollyById(tid: Int?) {
            tid?.let {
                val idx = trollyList.indexOfFirst { tr -> tr.trollyId == it }
                if (idx >= 0) spTrolly.setSelection(idx)
            }
            recalcTareAndNet()
        }

        fun loadDefaultsForMachine() {
            val mc = machineList.getOrNull(spMachine.selectedItemPosition) ?: return
            RetrofitClient.getApiService(this).getDoffLastByMachine(mc.mcId)
                .enqueue(object : Callback<DoffDefaultsResponse> {
                    override fun onResponse(call: Call<DoffDefaultsResponse>, response: Response<DoffDefaultsResponse>) {
                        val body = response.body() ?: return
                        applyQualityById(body.qualityId)
                        applyTrollyById(body.trollyId)
                    }
                    override fun onFailure(call: Call<DoffDefaultsResponse>, t: Throwable) {}
                })
        }

        fun loadLastEmpForDateSpell() {
            if (existing != null) return
            val sId = spellList.getOrNull(spSpell.selectedItemPosition)?.spellId ?: return
            RetrofitClient.getApiService(this).getDoffLastEmp(dialogDate, sId)
                .enqueue(object : Callback<DoffDefaultsResponse> {
                    override fun onResponse(call: Call<DoffDefaultsResponse>, response: Response<DoffDefaultsResponse>) {
                        val body = response.body() ?: return
                        if (etEmpCode.text.isNullOrBlank() && body.empCode != null) {
                            dialogEbId = body.ebId ?: 0
                            etEmpCode.setText(body.empCode)
                            tvEmpName.text = body.empName ?: ""
                        }
                    }
                    override fun onFailure(call: Call<DoffDefaultsResponse>, t: Throwable) {}
                })
        }

        existing?.let { t ->
            etEmpCode.setText(t.empCode ?: "")
            tvEmpName.text = t.empName ?: ""
            t.qualityId?.let { applyQualityById(it) }
            t.trollyId?.let  { applyTrollyById(it) }
            etGross.setText(t.grossWeight?.let { String.format(Locale.getDefault(), "%.3f", it) } ?: "")
            etTare.setText(t.tareWeight?.let   { String.format(Locale.getDefault(), "%.3f", it) } ?: "")
            etNet.setText(t.netWeight?.let     { String.format(Locale.getDefault(), "%.3f", it) } ?: "")
        }

        btnDate.setOnClickListener {
            val cal = Calendar.getInstance()
            try { apiDate.parse(dialogDate)?.let { cal.time = it } } catch (_: Exception) {}
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                dialogDate = apiDate.format(cal.time)
                tvDate.text = dispDate.format(cal.time)
                loadLastEmpForDateSpell()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        spMachine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (existing == null) loadDefaultsForMachine()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        spSpell.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                loadLastEmpForDateSpell()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        spTrolly.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                recalcTareAndNet()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        etGross.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { recalcTareAndNet() }
        })

        fun lookupEmployee() {
            val code = etEmpCode.text.toString().trim()
            if (code.isEmpty()) { tvEmpName.text = ""; dialogEbId = 0; return }
            tvEmpName.text = "Searching…"
            RetrofitClient.getApiService(this).searchEmployees(code, if (branchId > 0) branchId else null)
                .enqueue(object : Callback<EmployeeResponse> {
                    override fun onResponse(call: Call<EmployeeResponse>, response: Response<EmployeeResponse>) {
                        val emp = response.body()?.employees?.firstOrNull {
                            it.empCode.equals(code, ignoreCase = true)
                        } ?: response.body()?.employees?.firstOrNull()
                        if (emp != null) {
                            dialogEbId = emp.id
                            tvEmpName.text = emp.name
                        } else {
                            dialogEbId = 0
                            tvEmpName.text = "Not found"
                            Toast.makeText(this@SpinningDoffActivity,
                                "Employee not found", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<EmployeeResponse>, t: Throwable) {
                        dialogEbId = 0; tvEmpName.text = "Lookup failed"
                    }
                })
        }
        btnSearchEmp.setOnClickListener { lookupEmployee() }
        etEmpCode.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) lookupEmployee() }
        etEmpCode.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                lookupEmployee(); true
            } else false
        }

        val title = if (existing == null) "Add Doff Entry" else "Edit Doff Entry"
        AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle(title)
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val spellSel = spellList.getOrNull(spSpell.selectedItemPosition)
                val mcSel    = machineList.getOrNull(spMachine.selectedItemPosition)
                val qSel     = qualityList.getOrNull(spQuality.selectedItemPosition)
                val tSel     = trollyList.getOrNull(spTrolly.selectedItemPosition)

                if (dialogEbId <= 0) {
                    Toast.makeText(this, "Please lookup a valid employee", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (spellSel == null || mcSel == null) {
                    Toast.makeText(this, "Please select spell and machine", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val gross = etGross.text.toString().toDoubleOrNull()
                if (gross == null || gross <= 0.0) {
                    Toast.makeText(this, "Please enter gross weight", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val tare = etTare.text.toString().toDoubleOrNull() ?: 0.0
                val net  = etNet.text.toString().toDoubleOrNull() ?: (gross - tare)

                val req = DoffSaveRequest(
                    id           = existing?.id,
                    doffDate     = dialogDate,
                    spellId      = spellSel.spellId,
                    mcId         = mcSel.mcId,
                    qualityId    = qSel?.qualityId,
                    trollyId     = tSel?.trollyId,
                    ebId         = dialogEbId,
                    grossWeight  = gross,
                    tareWeight   = tare,
                    netWeight    = net,
                    weightType   = null,
                    branchId     = if (branchId > 0) branchId else null,
                    userId       = userId
                )
                saveDoff(req)
            }
            .setNegativeButton("Cancel", null)
            .show()

        if (existing == null) {
            view.post {
                loadDefaultsForMachine()
                loadLastEmpForDateSpell()
            }
        } else {
            recalcTareAndNet()
        }
    }

    private fun safeReformat(apiDateStr: String): String =
        try { dispDate.format(apiDate.parse(apiDateStr)!!) } catch (_: Exception) { apiDateStr }

    private fun saveDoff(req: DoffSaveRequest) {
        RetrofitClient.getApiService(this).saveDoffTransaction(req)
            .enqueue(object : Callback<DoffSaveResponse> {
                override fun onResponse(call: Call<DoffSaveResponse>, response: Response<DoffSaveResponse>) {
                    val msg = response.body()?.message
                        ?: if (response.isSuccessful) "Saved" else "Failed"
                    Toast.makeText(this@SpinningDoffActivity, msg, Toast.LENGTH_SHORT).show()
                    if (response.isSuccessful && response.body()?.status == "success") reloadList()
                }
                override fun onFailure(call: Call<DoffSaveResponse>, t: Throwable) {
                    Toast.makeText(this@SpinningDoffActivity,
                        "Error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun confirmDelete(item: DoffTransaction) {
        AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle("Delete Doff Entry")
            .setMessage("Delete doff entry for ${item.empName ?: item.empCode}?")
            .setPositiveButton("Delete") { _, _ ->
                val id = item.id ?: return@setPositiveButton
                RetrofitClient.getApiService(this).deleteDoffTransaction(id)
                    .enqueue(object : Callback<DoffSaveResponse> {
                        override fun onResponse(call: Call<DoffSaveResponse>, response: Response<DoffSaveResponse>) {
                            Toast.makeText(this@SpinningDoffActivity,
                                response.body()?.message ?: "Deleted",
                                Toast.LENGTH_SHORT).show()
                            reloadList()
                        }
                        override fun onFailure(call: Call<DoffSaveResponse>, t: Throwable) {
                            Toast.makeText(this@SpinningDoffActivity,
                                "Error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

