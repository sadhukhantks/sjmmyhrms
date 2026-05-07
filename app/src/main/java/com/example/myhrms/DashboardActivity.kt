package com.example.myhrms

import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myhrms.api.BranchData
import com.example.myhrms.api.BranchResponse
import com.example.myhrms.api.CompanyData
import com.example.myhrms.api.CompanyResponse
import com.example.myhrms.api.ApiConfig
import com.example.myhrms.adapter.DeptWiseAdapter
import com.example.myhrms.api.DashboardStatsResponse
import com.example.myhrms.api.RetrofitClient
import com.example.myhrms.api.Shift
import com.example.myhrms.api.ShiftResponse
import com.example.myhrms.databinding.ActivityDashboardBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayInputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var deptWiseAdapter: DeptWiseAdapter
    private val calendar = Calendar.getInstance()
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    private var selectedDateApi: String = ""   // yyyy-MM-dd for API
    private var selectedDateDisplay: String = "" // dd-MM-yyyy for UI
    private lateinit var companyAdapter: ArrayAdapter<CompanyData>
    private lateinit var branchAdapter: ArrayAdapter<BranchData>
    private lateinit var spellAdapter: ArrayAdapter<Shift>
    private var selectedCompanyId: Int = 0
    private var selectedBranchId: Int = 0
    private var selectedSpellId: Int? = null
    private var allDepartments: List<com.example.myhrms.api.DeptWiseStat> = emptyList() // Store all departments
    private var showOnlyPresentDepts: Boolean = false // Toggle filter state
    private var isInitializing: Boolean = true // Track if spinners are being initialized

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedDateApi = apiDateFormat.format(calendar.time)
        selectedDateDisplay = displayDateFormat.format(calendar.time)

        setupWelcome()
        setupLogout()
        setupDatePicker()
        setupSpellSpinner()
        setupCompanyAndBranchSpinners()
        setupAttendanceMenu()
        setupProductionMenu()
        setupDeptWiseRecyclerView()
        setupCardClicks()

        binding.root.post {
            isInitializing = false
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh stats when returning from other activities (only if branch already selected)
        if (selectedBranchId > 0) {
            loadDashboardStats()
        }
    }

    // ── Welcome Section ──────────────────────────────────────────

    private fun setupWelcome() {
        val userName = intent.getStringExtra("USER_NAME") ?: "User"
        binding.tvUserName.text = userName
        binding.tvWelcome.text = "Welcome to MIS System"
    }

    private fun setupCompanyAndBranchSpinners() {
        companyAdapter = ArrayAdapter(this, R.layout.spinner_item_black, mutableListOf())
        companyAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_black)
        binding.spCompany.adapter = companyAdapter

        branchAdapter = ArrayAdapter(this, R.layout.spinner_item_black, mutableListOf())
        branchAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_black)
        binding.spBranch.adapter = branchAdapter

        binding.spCompany.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val company = companyAdapter.getItem(position)
                selectedCompanyId = company?.id ?: 0
                updateCompanyLogo(company?.logo)
                if (selectedCompanyId > 0) {
                    loadBranches(selectedCompanyId)
                } else {
                    setBranchList(emptyList())
                }
                // Only reload if not during initial setup
                if (!isInitializing) {
                    // Hide department section when company changes
                    binding.layoutDeptWise.visibility = View.GONE
                    // Reload dashboard with new company
                    loadDashboardStats()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.spBranch.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedBranchId = branchAdapter.getItem(position)?.id ?: 0
                // Only reload if not during initial setup
                if (!isInitializing) {
                    // Hide department section when branch changes
                    binding.layoutDeptWise.visibility = View.GONE
                    // Reload dashboard with new branch
                    loadDashboardStats()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        setCompanyList(emptyList())
        setBranchList(emptyList())
        loadCompanies()
    }

    private fun loadCompanies() {
        RetrofitClient.getApiService(this).getCompanies().enqueue(object : Callback<CompanyResponse> {
            override fun onResponse(call: Call<CompanyResponse>, response: Response<CompanyResponse>) {
                if (!response.isSuccessful) {
                    loadCompaniesLegacy()
                    return
                }

                val companies = response.body()?.companyList().orEmpty().filter { it.id != null }
                setCompanyList(companies)
                if (companies.isNotEmpty()) {
                    updateCompanyLogo(companies.first().logo)
                    companies.first().id?.let { loadBranches(it) }
                }
            }

            override fun onFailure(call: Call<CompanyResponse>, t: Throwable) {
                loadCompaniesLegacy()
            }
        })
    }

    private fun loadCompaniesLegacy() {
        RetrofitClient.getApiService(this).getCompaniesLegacy().enqueue(object : Callback<CompanyResponse> {
            override fun onResponse(call: Call<CompanyResponse>, response: Response<CompanyResponse>) {
                if (!response.isSuccessful) {
                    Toast.makeText(this@DashboardActivity, "Failed to load companies", Toast.LENGTH_SHORT).show()
                    return
                }

                val companies = response.body()?.companyList().orEmpty().filter { it.id != null }
                setCompanyList(companies)
                if (companies.isNotEmpty()) {
                    updateCompanyLogo(companies.first().logo)
                    companies.first().id?.let { loadBranches(it) }
                }
            }

            override fun onFailure(call: Call<CompanyResponse>, t: Throwable) {
                Toast.makeText(this@DashboardActivity, "Company load error", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun loadBranches(companyId: Int) {
        RetrofitClient.getApiService(this).getBranches(companyId).enqueue(object : Callback<BranchResponse> {
            override fun onResponse(call: Call<BranchResponse>, response: Response<BranchResponse>) {
                if (!response.isSuccessful) {
                    setBranchList(emptyList())
                    return
                }
                val branches = response.body()?.branchList().orEmpty().filter { it.id != null }
                if (branches.isNotEmpty()) {
                    setBranchList(branches)
                } else {
                    loadBranchesByCoId(companyId)
                }
            }

            override fun onFailure(call: Call<BranchResponse>, t: Throwable) {
                loadBranchesByCoId(companyId)
            }
        })
    }

    private fun loadBranchesByCoId(companyId: Int) {
        RetrofitClient.getApiService(this).getBranchesByCoId(companyId).enqueue(object : Callback<BranchResponse> {
            override fun onResponse(call: Call<BranchResponse>, response: Response<BranchResponse>) {
                if (!response.isSuccessful) {
                    loadBranchesLegacy(companyId)
                    return
                }
                val branches = response.body()?.branchList().orEmpty().filter { it.id != null }
                if (branches.isNotEmpty()) {
                    setBranchList(branches)
                } else {
                    loadBranchesLegacy(companyId)
                }
            }

            override fun onFailure(call: Call<BranchResponse>, t: Throwable) {
                loadBranchesLegacy(companyId)
            }
        })
    }

    private fun loadBranchesLegacy(companyId: Int) {
        RetrofitClient.getApiService(this).getBranchesLegacy(companyId).enqueue(object : Callback<BranchResponse> {
            override fun onResponse(call: Call<BranchResponse>, response: Response<BranchResponse>) {
                if (!response.isSuccessful) {
                    setBranchList(emptyList())
                    return
                }
                val branches = response.body()?.branchList().orEmpty().filter { it.id != null }
                setBranchList(branches)
            }

            override fun onFailure(call: Call<BranchResponse>, t: Throwable) {
                setBranchList(emptyList())
            }
        })
    }

    private fun setCompanyList(companies: List<CompanyData>) {
        val finalList = if (companies.isNotEmpty()) companies else listOf(CompanyData(0, "No Company", null))
        companyAdapter.clear()
        companyAdapter.addAll(finalList)
        companyAdapter.notifyDataSetChanged()
    }

    private fun setBranchList(branches: List<BranchData>) {
        val finalList = if (branches.isNotEmpty()) branches else listOf(BranchData(0, null, "No Branch"))
        branchAdapter.clear()
        branchAdapter.addAll(finalList)
        branchAdapter.notifyDataSetChanged()
        // Immediately capture the first branch ID so it's ready before any user interaction
        val newBranchId = finalList.firstOrNull()?.id ?: 0
        val branchChanged = newBranchId != selectedBranchId
        selectedBranchId = newBranchId
        if (selectedBranchId > 0) {
            loadSpells(selectedBranchId)
        } else {
            setSpellList(emptyList())
        }
        // Reload dashboard with branch filter once a real branch is available
        if (selectedBranchId > 0 && branchChanged) {
            binding.layoutDeptWise.visibility = View.GONE
            loadDashboardStats()
        }
    }

    private fun setupSpellSpinner() {
        spellAdapter = ArrayAdapter(this, R.layout.spinner_item_black, mutableListOf())
        spellAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_black)
        binding.spSpell.adapter = spellAdapter

        binding.spSpell.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val spell = spellAdapter.getItem(position)
                selectedSpellId = if ((spell?.id ?: 0) > 0) spell?.id else null
                if (!isInitializing) {
                    binding.layoutDeptWise.visibility = View.GONE
                    loadDashboardStats()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        setSpellList(emptyList())
    }

    private fun loadSpells(branchId: Int) {
        RetrofitClient.getApiService(this).getShifts(branchId).enqueue(object : Callback<ShiftResponse> {
            override fun onResponse(call: Call<ShiftResponse>, response: Response<ShiftResponse>) {
                val shifts = if (response.isSuccessful) response.body()?.shifts.orEmpty() else emptyList()
                setSpellList(shifts)
            }
            override fun onFailure(call: Call<ShiftResponse>, t: Throwable) {
                setSpellList(emptyList())
            }
        })
    }

    private fun setSpellList(spells: List<Shift>) {
        val allSpell = Shift(id = 0, name = "All Spells")
        val finalList = listOf(allSpell) + spells
        spellAdapter.clear()
        spellAdapter.addAll(finalList)
        spellAdapter.notifyDataSetChanged()
        selectedSpellId = null
    }

    private fun updateCompanyLogo(logoValue: String?) {
        if (logoValue.isNullOrBlank()) {
            binding.ivCompanyLogo.setImageResource(R.drawable.logo_vow)
            return
        }

        val logo = logoValue.trim()
        when {
            logo.startsWith("http://") || logo.startsWith("https://") -> loadLogoFromUrl(logo)
            logo.startsWith("/") || logo.startsWith("images/") || logo.startsWith("uploads/") -> {
                val baseUrl = ApiConfig.getBaseUrl(this)
                val fullUrl = baseUrl.trimEnd('/') + "/" + logo.trimStart('/')
                loadLogoFromUrl(fullUrl)
            }
            else -> {
                val base64Part = if (logo.contains(",")) logo.substringAfter(",") else logo
                try {
                    val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeStream(ByteArrayInputStream(bytes))
                    if (bitmap != null) {
                        binding.ivCompanyLogo.setImageBitmap(bitmap)
                    } else {
                        binding.ivCompanyLogo.setImageResource(R.drawable.logo_vow)
                    }
                } catch (_: Exception) {
                    binding.ivCompanyLogo.setImageResource(R.drawable.logo_vow)
                }
            }
        }
    }

    private fun loadLogoFromUrl(url: String) {
        Thread {
            try {
                val stream = URL(url).openStream()
                val bitmap = BitmapFactory.decodeStream(stream)
                runOnUiThread {
                    if (bitmap != null) {
                        binding.ivCompanyLogo.setImageBitmap(bitmap)
                    } else {
                        binding.ivCompanyLogo.setImageResource(R.drawable.logo_vow)
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    binding.ivCompanyLogo.setImageResource(R.drawable.logo_vow)
                }
            }
        }.start()
    }

    // ── Logout ───────────────────────────────────────────────────

    private fun setupLogout() {
        binding.btnLogout.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    // ── Card Click Handlers ──────────────────────────────────────

    private fun setupCardClicks() {
        // Departments card → Department Master
        binding.cardDepartments.setOnClickListener {
            val intent = Intent(this, DepartmentMasterActivity::class.java)
            intent.putExtra("CO_ID", selectedCompanyId)
            intent.putExtra("BRANCH_ID", selectedBranchId)
            startActivity(intent)
        }

        // Designations card → Occupation Master
        binding.cardDesignations.setOnClickListener {
            val intent = Intent(this, OccupationMasterActivity::class.java)
            intent.putExtra("BRANCH_ID", selectedBranchId)
            startActivity(intent)
        }


        // Employees card → Employee Master
        binding.cardEmployees.setOnClickListener {
            val intent = Intent(this, EmployeeMasterActivity::class.java)
            intent.putExtra("CO_ID", selectedCompanyId)
            intent.putExtra("BRANCH_ID", selectedBranchId)
            startActivity(intent)
        }

        // Present card → Show department-wise attendance (departments where employees are present)
        binding.cardPresent.setOnClickListener {
            showDepartmentsWithPresent()
        }

        // Absent card → Attendance Report for that date
        binding.cardAbsent.setOnClickListener {
            openAttendanceReport()
        }
    }

    private fun openAttendanceReport(departmentId: Int = -1) {
        val intent = Intent(this, AttendanceReportActivity::class.java)
        intent.putExtra("FROM_DATE", selectedDateDisplay)
        intent.putExtra("TO_DATE", selectedDateDisplay)
        intent.putExtra("AUTO_SEARCH", true)
        intent.putExtra("CO_ID", selectedCompanyId)
        intent.putExtra("BRANCH_ID", selectedBranchId)
        if (departmentId > 0) {
            intent.putExtra("DEPARTMENT_ID", departmentId)
        }
        startActivity(intent)
    }

    // ── Show / Hide Departments Where Employees Are Present (toggle) ──

    private fun showDepartmentsWithPresent() {
        // Toggle: if section is already visible, hide it
        if (binding.layoutDeptWise.visibility == View.VISIBLE) {
            binding.layoutDeptWise.visibility = View.GONE
            return
        }

        val filteredDepts = allDepartments.filter { it.present > 0 }
        android.util.Log.d("DashboardActivity",
            "showDepartmentsWithPresent: total=${allDepartments.size}, withPresent=${filteredDepts.size}")

        binding.layoutDeptWise.visibility = View.VISIBLE

        if (filteredDepts.isNotEmpty()) {
            binding.tvDeptWiseTitle.text =
                "Departments with Present Attendance (${filteredDepts.size})"
            deptWiseAdapter.updateList(filteredDepts)
            binding.rvDeptWise.visibility = View.VISIBLE
            binding.tvDeptWiseEmpty.visibility = View.GONE
        } else {
            binding.tvDeptWiseTitle.text = "Departments with Present Attendance"
            binding.rvDeptWise.visibility = View.GONE
            binding.tvDeptWiseEmpty.visibility = View.VISIBLE
            Toast.makeText(this, "No departments with present attendance", Toast.LENGTH_SHORT).show()
        }

        // Scroll the section into view
        binding.layoutDeptWise.post {
            binding.layoutDeptWise.parent?.requestChildFocus(binding.layoutDeptWise, binding.layoutDeptWise)
        }
    }

    // ── Date Picker ──────────────────────────────────────────────

    private fun setupDatePicker() {
        binding.tvSelectedDate.text = selectedDateDisplay
        binding.btnDatePicker.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    selectedDateApi = apiDateFormat.format(calendar.time)
                    selectedDateDisplay = displayDateFormat.format(calendar.time)
                    binding.tvSelectedDate.text = selectedDateDisplay
                    loadDashboardStats()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    // ── Load Dashboard Stats ─────────────────────────────────────

    private fun loadDashboardStats() {
        binding.progressStats.visibility = View.VISIBLE

        RetrofitClient.getApiService(this).getDashboardStats(
            selectedDateApi,
            if (selectedBranchId > 0) selectedBranchId else null,
            if (selectedCompanyId > 0) selectedCompanyId else null,
            selectedSpellId
        )
            .enqueue(object : Callback<DashboardStatsResponse> {
                override fun onResponse(
                    call: Call<DashboardStatsResponse>,
                    response: Response<DashboardStatsResponse>
                ) {
                    binding.progressStats.visibility = View.GONE

                    if (response.isSuccessful) {
                        val stats = response.body() ?: return
                        android.util.Log.d("DashboardActivity", "API Response received: $stats")
                        
                        binding.tvStatDepartments.text = stats.totalDepartments.toString()
                        binding.tvStatDesignations.text = stats.totalDesignations.toString()
                        binding.tvStatEmployees.text = stats.totalEmployees.toString()
                        binding.tvStatPresent.text = stats.totalPresent.toString()
                        binding.tvStatFace.text = stats.presentFace.toString()
                        binding.tvStatManual.text = stats.presentManual.toString()
                        binding.tvStatAbsent.text = stats.totalAbsent.toString()

                        // Department-wise breakdown - use department_wise (all departments)
                        // and filter to present > 0 client-side. department_master for Master card.
                        val deptWiseList = stats.departmentWise ?: emptyList()
                        val deptPresentList = (stats.departmentPresent ?: emptyList())
                            .ifEmpty { deptWiseList.filter { it.present > 0 } }
                        val deptMasterList = stats.departmentMaster ?: emptyList()
                        
                        android.util.Log.d("DashboardActivity", "departmentWise size: ${deptWiseList.size}")
                        android.util.Log.d("DashboardActivity", "departmentPresent size: ${deptPresentList.size}")
                        android.util.Log.d("DashboardActivity", "departmentPresent: $deptPresentList")
                        android.util.Log.d("DashboardActivity", "departmentMaster size: ${deptMasterList.size}")

                        // Prefer the full department_wise list so all departments with present>0
                        // are available when user clicks the Present card.
                        allDepartments = if (deptWiseList.isNotEmpty()) deptWiseList else deptPresentList
                        showOnlyPresentDepts = false // Reset filter state

                        val initialList = allDepartments.filter { it.present > 0 }
                        if (initialList.isNotEmpty()) {
                            binding.rvDeptWise.visibility = View.VISIBLE
                            binding.tvDeptWiseEmpty.visibility = View.GONE
                            deptWiseAdapter.updateList(initialList)
                        } else {
                            binding.rvDeptWise.visibility = View.GONE
                            binding.tvDeptWiseEmpty.visibility = View.VISIBLE
                        }
                        // The parent layout (layoutDeptWise) remains hidden until user clicks Present card
                        binding.layoutDeptWise.visibility = View.GONE
                        android.util.Log.d("DashboardActivity", "Dashboard stats loaded successfully")
                    } else {
                        Toast.makeText(
                            this@DashboardActivity,
                            "Failed to load stats",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<DashboardStatsResponse>, t: Throwable) {
                    binding.progressStats.visibility = View.GONE
                    Toast.makeText(
                        this@DashboardActivity,
                        "Network error: ${t.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    // ── Department-Wise RecyclerView ─────────────────────────────

    private fun setupDeptWiseRecyclerView() {
        deptWiseAdapter = DeptWiseAdapter(emptyList()) { dept ->
            openAttendanceReport(dept.departmentId)
        }
        binding.rvDeptWise.layoutManager = LinearLayoutManager(this)
        binding.rvDeptWise.adapter = deptWiseAdapter
    }

    // ── Attendance Menu (expand/collapse) ────────────────────────

    private fun setupAttendanceMenu() {
        binding.headerAttendance.setOnClickListener {
            toggleSubMenu(binding.subMenuAttendance, binding.arrowAttendance)
        }

        binding.menuAttendanceDashboard.setOnClickListener {
            val intent = Intent(this, AttendanceDashboardActivity::class.java)
            intent.putExtra("USER_NAME", binding.tvUserName.text?.toString() ?: "")
            intent.putExtra("CO_ID", selectedCompanyId)
            intent.putExtra("BRANCH_ID", selectedBranchId)
            startActivity(intent)
        }

        binding.menuOnBoarding.setOnClickListener {
            val intent = Intent(this, OnBoardingActivity::class.java)
            intent.putExtra("CO_ID", selectedCompanyId)
            intent.putExtra("BRANCH_ID", selectedBranchId)
            startActivity(intent)
        }

        binding.menuAttendanceEntry.setOnClickListener {
            val intent = Intent(this, AttendanceActivity::class.java)
            intent.putExtra("CO_ID", selectedCompanyId)
            intent.putExtra("BRANCH_ID", selectedBranchId)
            startActivity(intent)
        }

        binding.menuAttendanceReports.setOnClickListener {
            val intent = Intent(this, AttendanceReportsActivity::class.java)
            intent.putExtra("CO_ID", selectedCompanyId)
            intent.putExtra("BRANCH_ID", selectedBranchId)
            startActivity(intent)
        }

        binding.headerOtherEntries.setOnClickListener {
            toggleSubMenu(binding.subMenuOtherEntries, binding.arrowOtherEntries)
        }

        binding.menuLeaveEntries.setOnClickListener {
            val intent = Intent(this, LeaveEntryActivity::class.java)
            intent.putExtra("CO_ID", selectedCompanyId)
            intent.putExtra("BRANCH_ID", selectedBranchId)
            startActivity(intent)
        }
    }

    private fun setupProductionMenu() {
        binding.headerProduction.setOnClickListener {
            toggleSubMenu(binding.subMenuProduction, binding.arrowProduction)
        }

        // Spreader Entry expandable group
        binding.headerSpreaderEntry.setOnClickListener {
            toggleSubMenu(binding.subMenuSpreaderEntry, binding.arrowSpreaderEntry)
        }
        binding.menuProductionEntry.setOnClickListener {
            openProductionScreen("Production Entry")
        }
        binding.menuIssueEntry.setOnClickListener {
            openProductionScreen("Issue Entry")
        }

        binding.menuDrawingMeterEntry.setOnClickListener {
            val i = Intent(this, DrawingMeterEntryActivity::class.java)
            i.putExtra("CO_ID", selectedCompanyId)
            i.putExtra("BRANCH_ID", selectedBranchId)
            startActivity(i)
        }
        binding.menuSpinningDoffEntry.setOnClickListener {
            val i = Intent(this, SpinningDoffActivity::class.java)
            i.putExtra("CO_ID", selectedCompanyId)
            i.putExtra("BRANCH_ID", selectedBranchId)
            startActivity(i)
        }

        // Doff Entry expandable group
        binding.headerDoffEntry.setOnClickListener {
            toggleSubMenu(binding.subMenuDoffEntry, binding.arrowDoffEntry)
        }
        binding.menuSpellWiseFrameEntry.setOnClickListener {
            val i = Intent(this, SpellWiseFrameEntryActivity::class.java)
            i.putExtra("CO_ID", selectedCompanyId)
            i.putExtra("BRANCH_ID", selectedBranchId)
            startActivity(i)
        }
        binding.menuSpgDoffEntry.setOnClickListener {
            val i = Intent(this, NewDoffEntryActivity::class.java)
            i.putExtra("CO_ID", selectedCompanyId)
            i.putExtra("BRANCH_ID", selectedBranchId)
            startActivity(i)
        }
        binding.menuSpgDoffEntry1.setOnClickListener {
            val i = Intent(this, SpgDoffEntry1Activity::class.java)
            i.putExtra("CO_ID", selectedCompanyId)
            i.putExtra("BRANCH_ID", selectedBranchId)
            startActivity(i)
        }
        binding.menuWindingEntry.setOnClickListener {
            val i = Intent(this, WindingEntryActivity::class.java)
            i.putExtra("CO_ID", selectedCompanyId)
            i.putExtra("BRANCH_ID", selectedBranchId)
            startActivity(i)
        }
        binding.menuWeavingEntry.setOnClickListener {
            openProductionScreen("Weaving Entry")
        }
        binding.menuFinishingEntry.setOnClickListener {
            openProductionScreen("Finishing Entry")
        }
        binding.menuWeightEntry.setOnClickListener {
            val i = Intent(this, WeightEntryActivity::class.java)
            i.putExtra("CO_ID", selectedCompanyId)
            i.putExtra("BRANCH_ID", selectedBranchId)
            startActivity(i)
        }
    }

    private fun openProductionScreen(title: String) {
        startActivity(Intent(this, MenuPlaceholderActivity::class.java).putExtra("SCREEN_TITLE", title))
    }

    // ── Toggle Helper ────────────────────────────────────────────

    private fun toggleSubMenu(subMenu: View, arrow: View) {
        if (subMenu.visibility == View.VISIBLE) {
            subMenu.visibility = View.GONE
            (arrow as? ImageView)?.setImageResource(R.drawable.ic_expand_more)
        } else {
            subMenu.visibility = View.VISIBLE
            (arrow as? ImageView)?.setImageResource(R.drawable.ic_expand_less)
        }
    }
}
