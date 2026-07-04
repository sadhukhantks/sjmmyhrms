package com.example.myhrms

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.myhrms.api.Department
import com.example.myhrms.api.DepartmentResponse
import com.example.myhrms.api.DesignationResponse
import com.example.myhrms.api.Employee
import com.example.myhrms.api.EmployeeResponse
import com.example.myhrms.api.FaceRecognitionRequest
import com.example.myhrms.api.FaceRecognitionResponse
import com.example.myhrms.api.Occupation
import com.example.myhrms.api.RetrofitClient
import com.example.myhrms.api.Shift
import com.example.myhrms.api.ShiftResponse
import com.example.myhrms.databinding.ActivityAttendanceBinding
import android.util.Base64 as AndroidBase64
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AttendanceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAttendanceBinding
    private val calendar = Calendar.getInstance()

    private var departments = mutableListOf<Department>()
    private var shifts = mutableListOf<Shift>()
    private var occupations = mutableListOf<Occupation>()
    private var selectedTab = "Regular"
    private var isEmployeeVerified = false
    private var selectedCompanyId: Int = 0
    private var selectedBranchId: Int = 0

    // Camera photo file
    private var photoFile: File? = null
    private var photoUri: Uri? = null
    private var capturedBase64: String? = null  // held in memory until Submit

    // Machine selection
    private var machines = mutableListOf<com.example.myhrms.api.Machine>()
    private var selectedMachineIds = mutableSetOf<Int>()

    // Pending defaults from face match (auto-fill after spinners load)
    private var pendingDesignationId: Int? = null
    private var pendingMachineIds: List<Int>? = null

    // ── Camera launcher ──────────────────────────────────────────
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoFile != null) {
            // Show progress immediately — decoding happens off the UI thread
            // and the network call follows.
            binding.progressBar.visibility = View.VISIBLE
            binding.btnCamera.isEnabled = false

            Thread {
                val b64 = try {
                    loadResizedJpegBase64(photoFile!!)
                } catch (e: Exception) {
                    android.util.Log.e("PHOTO_DEBUG", "resize failed, falling back to raw", e)
                    AndroidBase64.encodeToString(photoFile!!.readBytes(), AndroidBase64.NO_WRAP)
                }
                runOnUiThread {
                    capturedBase64 = b64
                    identifyFace(capturedBase64!!)
                }
            }.start()
        } else {
            Toast.makeText(this, "Photo capture cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Decode the camera JPEG into a bitmap no larger than [maxDim] on its
     * longest side, fix orientation from EXIF, re-encode as JPEG quality
     * [quality], and return the base64 string. A typical 4032×3024 / 4-MB
     * camera frame becomes ~80-120 KB after this — round-trip drops from
     * ~15 s to ~1 s on a slow LAN, and `face_encodings` on the server
     * finishes in ~0.3 s instead of ~3 s.
     */
    private fun loadResizedJpegBase64(file: File, maxDim: Int = 720, quality: Int = 80): String {
        val path = file.absolutePath

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)

        var sample = 1
        while (longest / (sample * 2) >= maxDim) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = BitmapFactory.decodeFile(path, opts)
            ?: throw IllegalStateException("Bitmap decode failed for $path")

        val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (scale < 1f) {
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
            if (scaled != bitmap) bitmap.recycle()
            bitmap = scaled
        }

        val orientation = ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val rotationDeg = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90  -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotationDeg != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDeg) }
            val rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            if (rotated != bitmap) bitmap.recycle()
            bitmap = rotated
        }

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        bitmap.recycle()
        return AndroidBase64.encodeToString(baos.toByteArray(), AndroidBase64.NO_WRAP)
    }

    // ── Permission launcher ──────────────────────────────────────
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            openCamera()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAttendanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedCompanyId = intent.getIntExtra("CO_ID", 0)
        selectedBranchId  = intent.getIntExtra("BRANCH_ID", 0)

        setupToolbar()
        setupDate()
        setupTabs()
        setupOccupationSpinner()
        setupMachineSelector()
        loadDepartments()
        loadShifts()
        setupCameraButton()
        setupCheckButton()
        setupSearchButton()
        setupSubmitButton()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    // ── Date Picker ──────────────────────────────────────────────

    private fun setupDate() {
        updateDateDisplay()

        binding.tvDate.setOnClickListener {
            showDatePicker()
        }
        // Also allow click on the parent FrameLayout
        (binding.tvDate.parent as? View)?.setOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        val dialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                calendar.set(year, month, day)
                updateDateDisplay()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        // Disable all future dates
        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
    }

    private fun updateDateDisplay() {
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val suffix = getDaySuffix(day)
        val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
        val yearFormat = SimpleDateFormat("yy", Locale.getDefault())
        val month = monthFormat.format(calendar.time)
        val year = yearFormat.format(calendar.time)
        binding.tvDate.text = "${day}${suffix} ${month}' ${year}"
    }

    private fun getDaySuffix(day: Int): String {
        return when {
            day in 11..13 -> "th"
            day % 10 == 1 -> "st"
            day % 10 == 2 -> "nd"
            day % 10 == 3 -> "rd"
            else -> "th"
        }
    }

    // ── Tabs (Regular / OT / Cash) ───────────────────────────────

    private fun setupTabs() {
        val tabs = listOf(binding.tabRegular, binding.tabOT, binding.tabCash)
        val tabNames = listOf("Regular", "OT", "Cash")

        tabs.forEachIndexed { index, tab ->
            tab.setOnClickListener {
                selectedTab = tabNames[index]
                tabs.forEach { t ->
                    t.setBackgroundResource(R.drawable.bg_tab_unselected)
                    t.setTextColor(resources.getColor(R.color.hint_text, theme))
                    t.setTypeface(null, android.graphics.Typeface.NORMAL)
                }
                tab.setBackgroundResource(R.drawable.bg_tab_selected)
                tab.setTextColor(resources.getColor(R.color.label_text, theme))
                tab.setTypeface(null, android.graphics.Typeface.BOLD)
            }
        }
    }

    // ── Departments from API ─────────────────────────────────────

    private fun loadDepartments() {
        RetrofitClient.getApiService(this).getDepartments(
            coId = if (selectedCompanyId > 0) selectedCompanyId else null,
            branchId = if (selectedBranchId > 0) selectedBranchId else null
        ).enqueue(object : Callback<DepartmentResponse> {
            override fun onResponse(call: Call<DepartmentResponse>, response: Response<DepartmentResponse>) {
                if (response.isSuccessful) {
                    val data = response.body()?.departments ?: emptyList()
                    departments.clear()
                    departments.addAll(data)

                    val items = mutableListOf("Select Department")
                    items.addAll(data.map { it.name })

                    val adapter = ArrayAdapter(
                        this@AttendanceActivity,
                        R.layout.spinner_item_black,
                        items
                    )
                    adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_black)
                    binding.spinnerDepartment.adapter = adapter

                    // When department changes, reload designations for that dept+branch
                    binding.spinnerDepartment.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                            val subDeptId = if (position > 0) departments[position - 1].id else null
                            if (selectedBranchId > 0) {
                                loadDesignations(selectedBranchId, subDeptId)
                            }
                        }
                        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                    }

                    // Load all designations for branch initially
                    if (selectedBranchId > 0) loadDesignations(selectedBranchId, null)

                } else {
                    Toast.makeText(this@AttendanceActivity, "Failed to load departments", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<DepartmentResponse>, t: Throwable) {
                Toast.makeText(this@AttendanceActivity, "Network error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // ── Shifts from API ──────────────────────────────────────────

    private fun loadShifts() {
        RetrofitClient.getApiService(this).getShifts(
            branchId = if (selectedBranchId > 0) selectedBranchId else null
        ).enqueue(object : Callback<ShiftResponse> {
            override fun onResponse(call: Call<ShiftResponse>, response: Response<ShiftResponse>) {
                if (response.isSuccessful) {
                    val data = response.body()?.shifts ?: emptyList()
                    shifts.clear()
                    shifts.addAll(data)

                    val items = data.map { it.name }.toMutableList()
                    if (items.isEmpty()) items.add("No shifts")

                    val adapter = ArrayAdapter(
                        this@AttendanceActivity,
                        R.layout.spinner_item_black,
                        items
                    )
                    adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_black)
                    binding.spinnerShift.adapter = adapter
                    
                    // Set up listener to update shift hours and working hours when shift is selected
                    binding.spinnerShift.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                            if (position >= 0 && position < shifts.size) {
                                val selectedShift = shifts[position]
                                val shiftHours = selectedShift.shiftHours ?: 0.0
                                binding.etShiftHours.setText(String.format(Locale.getDefault(), "%.0f", shiftHours))
                                // Auto-fill working hours to match shift hours
                                binding.etWorkingHours.setText(String.format(Locale.getDefault(), "%.0f", shiftHours))
                            }
                        }
                        
                        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                            // Do nothing
                        }
                    }
                    
                    // Automatically populate shift hours and working hours for the first shift (default selection)
                    if (shifts.isNotEmpty()) {
                        val firstShift = shifts[0]
                        val shiftHours = firstShift.shiftHours ?: 0.0
                        binding.etShiftHours.setText(String.format(Locale.getDefault(), "%.0f", shiftHours))
                        // Auto-fill working hours to match shift hours
                        binding.etWorkingHours.setText(String.format(Locale.getDefault(), "%.0f", shiftHours))
                    }
                } else {
                    Toast.makeText(this@AttendanceActivity, "Failed to load shifts", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ShiftResponse>, t: Throwable) {
                Toast.makeText(this@AttendanceActivity, "Network error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // ── Occupation (default placeholder) ─────────────────────────

    private fun setupOccupationSpinner() {
        val items = listOf("Select Designation")
        val adapter = ArrayAdapter(this, R.layout.spinner_item_black, items)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_black)
        binding.spinnerOccupation.adapter = adapter
    }

    // ── Designations from designation_mst ───────────────────────

    private fun loadDesignations(branchId: Int, subDeptId: Int?) {
        RetrofitClient.getApiService(this).getDesignations(
            branchId = branchId,
            subDeptId = subDeptId
        ).enqueue(object : Callback<DesignationResponse> {
            override fun onResponse(call: Call<DesignationResponse>, response: Response<DesignationResponse>) {
                if (response.isSuccessful) {
                    val data = response.body()?.designations ?: emptyList()
                    occupations.clear()
                    occupations.addAll(data.map { Occupation(it.id, it.name) })

                    val items = mutableListOf("Select Designation")
                    items.addAll(data.map { it.name })

                    val adapter = ArrayAdapter(
                        this@AttendanceActivity,
                        R.layout.spinner_item_black,
                        items
                    )
                    adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_black)
                    binding.spinnerOccupation.adapter = adapter

                    // ── Apply pending designation default from face-match ──
                    pendingDesignationId?.let { desigId ->
                        val idx = occupations.indexOfFirst { it.id == desigId }
                        if (idx >= 0) {
                            binding.spinnerOccupation.setSelection(idx + 1)
                            // Now load machines for that designation and auto-pick the saved ones
                            applyPendingMachines(desigId)
                        }
                        pendingDesignationId = null
                    }
                } else {
                    Toast.makeText(this@AttendanceActivity, "Failed to load designations", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<DesignationResponse>, t: Throwable) {
                Toast.makeText(this@AttendanceActivity, "Network error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    @Suppress("unused")
    private fun loadOccupations() { /* replaced by loadDesignations */ }

    // ── Check Button (tick) — lookup employee by code ──────────

    private fun setupCheckButton() {
        // Reset verification when employee code is manually changed
        binding.etEmployeeCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                isEmployeeVerified = false
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Auto-lookup when employee code field loses focus
        binding.etEmployeeCode.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val empCode = binding.etEmployeeCode.text.toString().trim()
                if (empCode.isNotEmpty() && !isEmployeeVerified) {
                    lookupEmployeeByCode(empCode)
                }
            }
        }

        // Manual lookup via tick button
        binding.btnCheck.setOnClickListener {
            val empCode = binding.etEmployeeCode.text.toString().trim()
            if (empCode.isEmpty()) {
                binding.etEmployeeCode.error = "Enter employee code first"
                binding.etEmployeeCode.requestFocus()
                return@setOnClickListener
            }
            lookupEmployeeByCode(empCode)
        }
    }

    private fun lookupEmployeeByCode(empCode: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnCheck.isEnabled = false

        RetrofitClient.getApiService(this).getEmployeeByCode(
            empCode = empCode,
            branchId = if (selectedBranchId > 0) selectedBranchId else null
        )
            .enqueue(object : Callback<FaceRecognitionResponse> {
                override fun onResponse(
                    call: Call<FaceRecognitionResponse>,
                    response: Response<FaceRecognitionResponse>
                ) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCheck.isEnabled = true

                    if (response.isSuccessful) {
                        val result = response.body()
                        android.util.Log.d("PHOTO_DEBUG", "employee lookup response: status=${result?.status}, empName=${result?.empName}, photoHtml=${if (result?.photoHtml.isNullOrEmpty()) "NULL/EMPTY" else "${result?.photoHtml?.length} chars"}")

                        if (result != null && result.status == "success") {
                            isEmployeeVerified = true
                            val name = result.empName ?: ""
                            if (name.isNotEmpty()) {
                                binding.tvEmployeeName.text = "Employee: $name"
                                binding.cardEmployeeInfo.visibility = View.VISIBLE
                            }
                            // Show photo from photo_html
                            showEmployeePhoto(result.photoHtml)

                            Toast.makeText(
                                this@AttendanceActivity,
                                "Employee found: $name",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            isEmployeeVerified = false
                            binding.tvEmployeeName.text = "⚠ Employee not found"
                            binding.cardEmployeeInfo.visibility = View.VISIBLE
                            binding.ivEmployeePhoto.setImageResource(R.drawable.ic_person)
                            Toast.makeText(
                                this@AttendanceActivity,
                                result?.message ?: "Employee not found",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        isEmployeeVerified = false
                        binding.tvEmployeeName.text = "⚠ Employee not found"
                        binding.cardEmployeeInfo.visibility = View.VISIBLE
                        binding.ivEmployeePhoto.setImageResource(R.drawable.ic_person)
                        Toast.makeText(
                            this@AttendanceActivity,
                            "Employee code not found",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<FaceRecognitionResponse>, t: Throwable) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCheck.isEnabled = true
                    binding.tvEmployeeName.text = "⚠ Network error"
                    binding.cardEmployeeInfo.visibility = View.VISIBLE
                    binding.ivEmployeePhoto.setImageResource(R.drawable.ic_person)
                    Toast.makeText(
                        this@AttendanceActivity,
                        "Network error: ${t.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }

    // ── Search Button — search employee by name ──────────────

    private fun setupSearchButton() {
        binding.btnSearch.setOnClickListener {
            showEmployeeSearchDialog()
        }
    }

    private fun showEmployeeSearchDialog() {
        val editText = android.widget.EditText(this).apply {
            hint = "Type employee name..."
            setPadding(48, 32, 48, 32)
            setTextColor(android.graphics.Color.BLACK)
        }

        val listView = android.widget.ListView(this)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(editText)
            addView(listView)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Search Employee")
            .setView(layout)
            .setNegativeButton("Cancel", null)
            .create()

        var allEmployees = listOf<Employee>()
        val displayList = mutableListOf<String>()
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayList)
        listView.adapter = adapter

        // Load all employees
        RetrofitClient.getApiService(this).getEmployees()
            .enqueue(object : Callback<EmployeeResponse> {
                override fun onResponse(call: Call<EmployeeResponse>, response: Response<EmployeeResponse>) {
                    if (response.isSuccessful) {
                        allEmployees = response.body()?.employees ?: emptyList()
                        displayList.clear()
                        displayList.addAll(allEmployees.map { "${it.empCode} - ${it.name}" })
                        adapter.notifyDataSetChanged()
                    }
                }
                override fun onFailure(call: Call<EmployeeResponse>, t: Throwable) {
                    Toast.makeText(this@AttendanceActivity, "Failed to load employees", Toast.LENGTH_SHORT).show()
                }
            })

        // Filter as user types
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase()
                displayList.clear()
                displayList.addAll(
                    allEmployees.filter {
                        it.name.lowercase().contains(query) || it.empCode.lowercase().contains(query)
                    }.map { "${it.empCode} - ${it.name}" }
                )
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // When user taps an employee
        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedText = displayList[position]
            val empCode = selectedText.split(" - ").firstOrNull()?.trim() ?: ""
            binding.etEmployeeCode.setText(empCode)
            dialog.dismiss()
            // Auto-verify the selected employee
            lookupEmployeeByCode(empCode)
        }

        dialog.show()
    }

    // ── Camera Button ────────────────────────────────────────────

    private fun setupCameraButton() {
        binding.btnCamera.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                openCamera()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun openCamera() {
        val photosDir = File(cacheDir, "photos")
        photosDir.mkdirs()
        photoFile = File(photosDir, "face_${System.currentTimeMillis()}.jpg")

        photoUri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            photoFile!!
        )

        cameraLauncher.launch(photoUri)
    }

    // ── Identify Face via /check-face (no attendance saved) ─────

    private fun identifyFace(base64Image: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnCamera.isEnabled = false

        val request = FaceRecognitionRequest(image = base64Image)

        RetrofitClient.getApiService(this).checkFace(request)
            .enqueue(object : Callback<FaceRecognitionResponse> {
                override fun onResponse(
                    call: Call<FaceRecognitionResponse>,
                    response: Response<FaceRecognitionResponse>
                ) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCamera.isEnabled = true

                    if (response.isSuccessful) {
                        val result = response.body()
                        android.util.Log.d("PHOTO_DEBUG", "check-face response: status=${result?.status}, empName=${result?.empName}, photoHtml=${if (result?.photoHtml.isNullOrEmpty()) "NULL/EMPTY" else "${result?.photoHtml?.length} chars"}")

                        if (result != null && result.status == "success") {
                            isEmployeeVerified = true
                            binding.etEmployeeCode.setText(result.empCode ?: "")

                            val name = result.empName ?: ""
                            if (name.isNotEmpty()) {
                                binding.tvEmployeeName.text = "Employee: $name"
                                binding.cardEmployeeInfo.visibility = View.VISIBLE
                            }

                            // Decode photo from photo_html and show in ImageView
                            showEmployeePhoto(result.photoHtml)

                            // Auto-fill department / designation / machines from last attendance
                            applyFaceMatchDefaults(result)

                            Toast.makeText(
                                this@AttendanceActivity,
                                "Face matched: ${result.empName}. Press Submit to mark attendance.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            isEmployeeVerified = false
                            binding.tvEmployeeName.text = "⚠ Face not recognized"
                            binding.cardEmployeeInfo.visibility = View.VISIBLE
                            binding.ivEmployeePhoto.setImageResource(R.drawable.ic_person)
                            Toast.makeText(
                                this@AttendanceActivity,
                                result?.message ?: "Face not recognized",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        isEmployeeVerified = false
                        binding.tvEmployeeName.text = "⚠ Recognition failed"
                        binding.cardEmployeeInfo.visibility = View.VISIBLE
                        binding.ivEmployeePhoto.setImageResource(R.drawable.ic_person)
                        Toast.makeText(
                            this@AttendanceActivity,
                            "Recognition failed: ${response.message()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<FaceRecognitionResponse>, t: Throwable) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCamera.isEnabled = true
                    binding.tvEmployeeName.text = "⚠ Network error"
                    binding.cardEmployeeInfo.visibility = View.VISIBLE
                    binding.ivEmployeePhoto.setImageResource(R.drawable.ic_person)
                    Toast.makeText(
                        this@AttendanceActivity,
                        "Network error: ${t.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }

    // ── Decode photo from HTML and show in ImageView ─────────────

    private fun showEmployeePhoto(photoHtml: String?) {
        android.util.Log.d("PHOTO_DEBUG", "showEmployeePhoto called, photoHtml is ${if (photoHtml.isNullOrEmpty()) "NULL/EMPTY" else "${photoHtml.length} chars"}")

        if (photoHtml.isNullOrEmpty()) {
            binding.ivEmployeePhoto.setImageResource(R.drawable.ic_person)
            return
        }

        try {
            // photo_html may be either:
            //   1) HTML wrapper: <img src="data:image/jpeg;base64,XXXX" />
            //   2) Raw base64 string: /9j/4AAQSk...
            val trimmed = photoHtml.trim()
            val base64Data: String? = when {
                trimmed.contains("base64,") -> {
                    val regex = Regex("""base64,([A-Za-z0-9+/=]+)""")
                    regex.find(trimmed)?.groupValues?.get(1)?.trim()
                }
                // looks like a raw base64 (no HTML tags)
                !trimmed.contains('<') -> trimmed.replace("\\s".toRegex(), "")
                else -> null
            }

            android.util.Log.d("PHOTO_DEBUG", "base64Data length: ${base64Data?.length ?: 0}")

            if (!base64Data.isNullOrEmpty()) {
                val imageBytes = AndroidBase64.decode(base64Data, AndroidBase64.DEFAULT)
                android.util.Log.d("PHOTO_DEBUG", "Decoded bytes: ${imageBytes.size}")
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                android.util.Log.d("PHOTO_DEBUG", "Bitmap: ${bitmap != null}, size: ${bitmap?.width}x${bitmap?.height}")
                if (bitmap != null) {
                    binding.ivEmployeePhoto.setImageBitmap(bitmap)
                } else {
                    binding.ivEmployeePhoto.setImageResource(R.drawable.ic_person)
                }
            } else {
                android.util.Log.d("PHOTO_DEBUG", "No base64 data extracted. First 200 chars: ${photoHtml.take(200)}")
                binding.ivEmployeePhoto.setImageResource(R.drawable.ic_person)
            }
        } catch (e: Exception) {
            android.util.Log.e("PHOTO_DEBUG", "Error decoding photo", e)
            binding.ivEmployeePhoto.setImageResource(R.drawable.ic_person)
        }
    }

    private fun selectedShedType() =
        if (binding.toggleShed.checkedButtonId == R.id.btnShedNew) "New Shed" else "Old Shed"

    // ── Mark Attendance via /attendance (called on Submit) ───────

    private fun markAttendance(
        base64Image: String, attType: String,
        deptId: Int?, shiftId: Int?, desigId: Int?,
        attendanceDate: String, shiftHours: Double, workingHours: Double, idleHours: Double
    ) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false

        val request = FaceRecognitionRequest(
            image = base64Image,
            attType = attType,
            departmentId = deptId,
            shiftId = shiftId,
            designationId = desigId,
            attendanceDate = attendanceDate,
            shiftHours = shiftHours,
            workingHours = workingHours,
            idleHours = idleHours,
            machineIds = if (selectedMachineIds.isNotEmpty()) selectedMachineIds.toList() else null,
            branchId = if (selectedBranchId > 0) selectedBranchId else null,
            shedType = selectedShedType()
        )

        RetrofitClient.getApiService(this).recognizeFace(request)
            .enqueue(object : Callback<FaceRecognitionResponse> {
                override fun onResponse(
                    call: Call<FaceRecognitionResponse>,
                    response: Response<FaceRecognitionResponse>
                ) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSubmit.isEnabled = true

                    android.util.Log.d("ATTENDANCE_DEBUG", "<<< Response code=${response.code()}, success=${response.isSuccessful}")

                    if (response.isSuccessful) {
                        val result = response.body()
                        if (result != null && result.status == "success") {
                            Toast.makeText(
                                this@AttendanceActivity,
                                "✅ Attendance marked for ${result.empName}!",
                                Toast.LENGTH_LONG
                            ).show()
                            // Clear form for next entry
                            capturedBase64 = null
                            isEmployeeVerified = false
                            binding.etEmployeeCode.setText("")
                            binding.cardEmployeeInfo.visibility = View.GONE
                            binding.ivEmployeePhoto.setImageResource(R.drawable.ic_person)
                            binding.tvEmployeeName.text = ""
                        } else {
                            Toast.makeText(
                                this@AttendanceActivity,
                                result?.message ?: "Failed to mark attendance",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "no body"
                        android.util.Log.e("ATTENDANCE_DEBUG", "<<< Error body: $errorBody")
                        Toast.makeText(
                            this@AttendanceActivity,
                            "Failed [${response.code()}]: $errorBody",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(call: Call<FaceRecognitionResponse>, t: Throwable) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSubmit.isEnabled = true
                    android.util.Log.e("ATTENDANCE_DEBUG", "<<< Network failure", t)
                    Toast.makeText(
                        this@AttendanceActivity,
                        "Network error: ${t.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }

    // ── Submit ───────────────────────────────────────────────────

    private fun setupSubmitButton() {
        binding.btnSubmit.setOnClickListener {
            val empCode = binding.etEmployeeCode.text.toString().trim()
            val deptPos = binding.spinnerDepartment.selectedItemPosition
            val shiftPos = binding.spinnerShift.selectedItemPosition
            val occPos = binding.spinnerOccupation.selectedItemPosition

            val shiftHoursStr = binding.etShiftHours.text.toString().trim()
            val workingHoursStr = binding.etWorkingHours.text.toString().trim()
            val idleHoursStr = binding.etIdleHours.text.toString().trim()

            // 1. Valid employee code (not needed for face mode)
            if (capturedBase64 == null) {
                // Manual mode — employee code required and must be verified
                if (empCode.isEmpty()) {
                    binding.etEmployeeCode.error = "Please enter employee code"
                    binding.etEmployeeCode.requestFocus()
                    return@setOnClickListener
                }
                if (!isEmployeeVerified) {
                    Toast.makeText(this, "Please verify employee code first (use ✓)", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            // 2. Department must be selected
            if (deptPos == 0) {
                Toast.makeText(this, "Please select a department", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 3. Shift must be selected
            if (shifts.isEmpty() || shiftPos < 0) {
                Toast.makeText(this, "Please select a shift", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 4. Occupation must be selected (position 0 is "Select Occupation")
            if (occPos == 0) {
                Toast.makeText(this, "Please select an occupation", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 5. Shift hours > 0
            val shiftHours = shiftHoursStr.toDoubleOrNull() ?: 0.0
            if (shiftHours <= 0) {
                binding.etShiftHours.error = "Shift hours must be greater than 0"
                binding.etShiftHours.requestFocus()
                return@setOnClickListener
            }

            // 6. Working hours > 0
            val workingHours = workingHoursStr.toDoubleOrNull() ?: 0.0
            if (workingHours <= 0) {
                binding.etWorkingHours.error = "Working hours must be greater than 0"
                binding.etWorkingHours.requestFocus()
                return@setOnClickListener
            }

            // 7. working_hours - idle_hours > 0
            val idleHours = idleHoursStr.toDoubleOrNull() ?: 0.0
            if ((workingHours - idleHours) <= 0) {
                Toast.makeText(this, "Working hours minus idle hours must be greater than 0", Toast.LENGTH_SHORT).show()
                binding.etIdleHours.requestFocus()
                return@setOnClickListener
            }

            // Determine att_type from selected tab
            val attType = when (selectedTab) {
                "OT" -> "O"
                "Cash" -> "C"
                else -> "R"  // Regular
            }

            // Extract actual IDs from spinners
            val deptId = if (deptPos > 0 && departments.size >= deptPos) departments[deptPos - 1].id else null
            val shiftId = if (shifts.isNotEmpty() && shiftPos < shifts.size) shifts[shiftPos].id else null
            val occId = if (occPos > 0 && occupations.size >= occPos) occupations[occPos - 1].id else null

            // Gather all form data for debug display
            val mode = if (capturedBase64 != null) "Face" else "Manual"
            val deptName = if (deptPos > 0 && departments.size >= deptPos) departments[deptPos - 1].name else "N/A"
            val shiftName = if (shifts.isNotEmpty() && shiftPos < shifts.size) shifts[shiftPos].name else "N/A"
            val occName = if (occPos > 0 && occupations.size >= occPos) occupations[occPos - 1].name else "N/A"
            val dateStr = binding.tvDate.text.toString()
            val baseUrl = com.example.myhrms.api.ApiConfig.getBaseUrl(this)
            val apiUrl = if (mode == "Face") "${baseUrl}attendance" else "${baseUrl}mark-attendance"

            val debugMsg = """
                |API: POST $apiUrl
                |Mode: $mode | Tab: $selectedTab | att_type: $attType
                |EmpCode: $empCode | Date: $dateStr
                |Dept: $deptName (id=$deptId) | Shift: $shiftName (id=$shiftId) | Occ: $occName (id=$occId)
                |ShiftHrs: $shiftHoursStr | WorkHrs: $workingHoursStr | IdleHrs: $idleHoursStr
                |Image: ${if (capturedBase64 != null) "${capturedBase64!!.length} chars" else "none"}
            """.trimMargin()

            android.util.Log.d("ATTENDANCE_DEBUG", debugMsg)
            Toast.makeText(this, debugMsg, Toast.LENGTH_LONG).show()

            // Format attendance date as yyyy-MM-dd for the API
            val attendanceDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

            // All validations passed — determine Face vs Manual
            if (capturedBase64 != null) {
                // Face attendance — send image to /attendance
                markAttendance(capturedBase64!!, attType, deptId, shiftId, occId,
                    attendanceDate, shiftHours, workingHours, idleHours)
            } else {
                // Manual attendance — send emp_code to /mark-attendance
                markAttendanceManual(empCode, attType, deptId, shiftId, occId,
                    attendanceDate, shiftHours, workingHours, idleHours)
            }
        }
    }

    // ── Manual Attendance via /mark-attendance ───────────────────

    private fun markAttendanceManual(
        empCode: String, attType: String,
        deptId: Int?, shiftId: Int?, desigId: Int?,
        attendanceDate: String, shiftHours: Double, workingHours: Double, idleHours: Double
    ) {

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false

        val request = com.example.myhrms.api.MarkAttendanceRequest(
            empCode = empCode,
            status = "Manual",
            attType = attType,
            departmentId = deptId,
            shiftId = shiftId,
            designationId = desigId,
            attendanceDate = attendanceDate,
            shiftHours = shiftHours,
            workingHours = workingHours,
            idleHours = idleHours,
            machineIds = if (selectedMachineIds.isNotEmpty()) selectedMachineIds.toList() else null,
            branchId = if (selectedBranchId > 0) selectedBranchId else null,
            shedType = selectedShedType()
        )

        RetrofitClient.getApiService(this).markAttendanceManual(request)
            .enqueue(object : Callback<FaceRecognitionResponse> {
                override fun onResponse(
                    call: Call<FaceRecognitionResponse>,
                    response: Response<FaceRecognitionResponse>
                ) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSubmit.isEnabled = true

                    android.util.Log.d("ATTENDANCE_DEBUG", "<<< Response code=${response.code()}, success=${response.isSuccessful}")

                    if (response.isSuccessful) {
                        val result = response.body()
                        if (result != null && result.status == "success") {
                            Toast.makeText(
                                this@AttendanceActivity,
                                "✅ Attendance marked (Manual) for ${result.empName}!",
                                Toast.LENGTH_LONG
                            ).show()
                            // Clear form for next entry
                            capturedBase64 = null
                            isEmployeeVerified = false
                            binding.etEmployeeCode.setText("")
                            binding.cardEmployeeInfo.visibility = View.GONE
                            binding.ivEmployeePhoto.setImageResource(R.drawable.ic_person)
                            binding.tvEmployeeName.text = ""
                        } else {
                            Toast.makeText(
                                this@AttendanceActivity,
                                result?.message ?: "Failed to mark attendance",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "no body"
                        android.util.Log.e("ATTENDANCE_DEBUG", "<<< Error body: $errorBody")
                        Toast.makeText(
                            this@AttendanceActivity,
                            "Failed [${response.code()}]: $errorBody",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(call: Call<FaceRecognitionResponse>, t: Throwable) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSubmit.isEnabled = true
                    android.util.Log.e("ATTENDANCE_DEBUG", "<<< Network failure", t)
                    Toast.makeText(
                        this@AttendanceActivity,
                        "Network error: ${t.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }

    // ── Machine Selector ─────────────────────────────────────────
    private fun setupMachineSelector() {
        binding.layoutMachineNumbers.setOnClickListener {
            val occupationPos = binding.spinnerOccupation.selectedItemPosition
            if (occupationPos == 0) {
                Toast.makeText(this, "Please select occupation first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val designationId = occupations[occupationPos - 1].id
            showMachineSelectorDialog(designationId)
        }
    }

    private fun showMachineSelectorDialog(designationId: Int) {
        val dialog = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_machine_selector, null)
        dialog.setView(dialogView)
        
        val alertDialog = dialog.create()
        
        val etSearch = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSearchMachine)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerViewMachines)
        val tvSelectedCount = dialogView.findViewById<TextView>(R.id.tvSelectedCount)
        val btnOk = dialogView.findViewById<android.widget.Button>(R.id.btnOk)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)
        
        // Setup RecyclerView
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        val adapter = com.example.myhrms.adapter.MachineSelectionAdapter(
            machines,
            selectedMachineIds
        ) { count ->
            tvSelectedCount.text = "$count machine(s) selected"
        }
        recyclerView.adapter = adapter
        
        // Load machines
        loadMachines(designationId, adapter)
        
        // Search functionality
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        
        // Update initial count
        tvSelectedCount.text = "${selectedMachineIds.size} machine(s) selected"
        
        btnOk.setOnClickListener {
            updateMachineDisplay()
            alertDialog.dismiss()
        }
        
        btnCancel.setOnClickListener {
            alertDialog.dismiss()
        }
        
        alertDialog.show()
    }

    private fun loadMachines(designationId: Int, adapter: com.example.myhrms.adapter.MachineSelectionAdapter? = null) {
        RetrofitClient.getApiService(this).getMachines(designationId).enqueue(
            object : Callback<com.example.myhrms.api.MachineResponse> {
                override fun onResponse(
                    call: Call<com.example.myhrms.api.MachineResponse>,
                    response: Response<com.example.myhrms.api.MachineResponse>
                ) {
                    android.util.Log.d("MACHINE_DEBUG", "Response code: ${response.code()}")
                    android.util.Log.d("MACHINE_DEBUG", "Response successful: ${response.isSuccessful}")
                    android.util.Log.d("MACHINE_DEBUG", "Response body: ${response.body()}")
                    
                    if (response.isSuccessful && response.body()?.status == "success") {
                        val data = response.body()?.data ?: emptyList()
                        
                        // Filter valid machines (id must be non-null and > 0)
                        val validMachines = data.filter { it.id != null && it.id > 0 }
                        
                        // Log for debugging
                        android.util.Log.d("MACHINE_DEBUG", "Total machines: ${data.size}, Valid: ${validMachines.size}")
                        
                        // Load valid machines
                        machines.clear()
                        machines.addAll(validMachines)
                        
                        // Update adapter if provided
                        adapter?.updateMachines(machines)
                            
                    } else {
                        android.util.Log.e("MACHINE_DEBUG", "API call failed: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<com.example.myhrms.api.MachineResponse>, t: Throwable) {
                    android.util.Log.e("MACHINE_DEBUG", "API call failed", t)
                }
            }
        )
    }

    private fun updateMachineDisplay() {
        if (selectedMachineIds.isEmpty()) {
            binding.tvSelectedMachines.text = "Tap to select machines"
            binding.tvSelectedMachines.setTextColor(getColor(R.color.hint_text))
            binding.tvMachineCount.visibility = View.GONE
        } else {
            val selectedMachines = machines.filter { 
                val machineId = it.id ?: 0
                machineId > 0 && selectedMachineIds.contains(machineId) 
            }
            val machineDisplayNames = selectedMachines.map { it.getDisplayName() }.joinToString(", ")
            binding.tvSelectedMachines.text = machineDisplayNames
            binding.tvSelectedMachines.setTextColor(getColor(R.color.label_text))
            binding.tvMachineCount.text = "${selectedMachineIds.size} machine(s) selected"
            binding.tvMachineCount.visibility = View.VISIBLE
        }
    }

    // ── Auto-fill defaults after face match ──────────────────────
    // Backend's /check-face response now carries default_department_id,
    // default_designation_id and default_machine_ids derived from the
    // employee's last daily_attendance + daily_ebmc_attendance rows.
    private fun applyFaceMatchDefaults(result: FaceRecognitionResponse) {
        // Reset any previous machine selection
        selectedMachineIds.clear()
        machines.clear()
        updateMachineDisplay()

        pendingDesignationId = result.defaultDesignationId
        pendingMachineIds    = result.defaultMachineIds

        val defaultDeptId = result.defaultDepartmentId
        if (defaultDeptId != null && defaultDeptId > 0 && departments.isNotEmpty()) {
            val deptIdx = departments.indexOfFirst { it.id == defaultDeptId }
            if (deptIdx >= 0) {
                // Triggers onItemSelected -> loadDesignations(...) -> applies pendingDesignationId
                binding.spinnerDepartment.setSelection(deptIdx + 1)
            } else if (selectedBranchId > 0) {
                // Department not in current list -> still try to load designations directly
                loadDesignations(selectedBranchId, defaultDeptId)
            }
        } else if (pendingDesignationId != null && selectedBranchId > 0) {
            // No dept, but we have a designation default -> reload all designations
            loadDesignations(selectedBranchId, null)
        }
    }

    // Called from loadDesignations() once the designations list is ready
    // and we have a pending designationId to preselect.
    private fun applyPendingMachines(designationId: Int) {
        val pending = pendingMachineIds ?: emptyList()
        pendingMachineIds = null
        RetrofitClient.getApiService(this).getMachines(designationId).enqueue(
            object : Callback<com.example.myhrms.api.MachineResponse> {
                override fun onResponse(
                    call: Call<com.example.myhrms.api.MachineResponse>,
                    response: Response<com.example.myhrms.api.MachineResponse>
                ) {
                    if (response.isSuccessful && response.body()?.status == "success") {
                        val data = response.body()?.data ?: emptyList()
                        val valid = data.filter { (it.id ?: 0) > 0 }
                        machines.clear()
                        machines.addAll(valid)

                        if (pending.isNotEmpty()) {
                            selectedMachineIds.clear()
                            valid.forEach { m ->
                                val mid = m.id ?: 0
                                if (mid > 0 && pending.contains(mid)) {
                                    selectedMachineIds.add(mid)
                                }
                            }
                        }
                        updateMachineDisplay()
                    }
                }
                override fun onFailure(
                    call: Call<com.example.myhrms.api.MachineResponse>,
                    t: Throwable
                ) {
                    android.util.Log.e("MACHINE_DEBUG", "applyPendingMachines failed", t)
                }
            }
        )
    }
}
