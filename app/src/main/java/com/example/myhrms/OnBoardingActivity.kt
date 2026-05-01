package com.example.myhrms

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.myhrms.api.OnBoardingRegisterRequest
import com.example.myhrms.api.OnBoardingRegisterResponse
import com.example.myhrms.api.OnBoardingEmployeeResponse
import com.example.myhrms.api.RetrofitClient
import com.example.myhrms.databinding.ActivityOnBoardingBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File

class OnBoardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnBoardingBinding
    private var currentEmpCode: String = ""
    private var currentEbId: Int = 0
    private var branchId: Int = 0
    private var photoFile: File? = null
    private var photoUri: Uri? = null
    private var capturedBase64: String? = null

    // ── Camera launcher ──────────────────────────────────────────
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoFile != null) {
            val bytes = photoFile!!.readBytes()
            capturedBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            // Show preview
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            binding.ivFacePreview.setImageBitmap(bmp)
            binding.ivFacePreview.visibility = View.VISIBLE
            binding.btnRegisterFace.isEnabled = true
        } else {
            Toast.makeText(this, "Photo capture cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openCamera() else Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnBoardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        branchId = intent.getIntExtra("BRANCH_ID", 0)

        setupToolbar()
        setupSearchButton()
        setupCaptureButton()
        setupRegisterButton()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    // ── Search employee by eb_id ─────────────────────────────────

    private fun setupSearchButton() {
        binding.btnSearch.setOnClickListener {
            val empCode = binding.etEbId.text.toString().trim()
            if (empCode.isEmpty()) {
                binding.etEbId.error = "Enter a valid Employee Code"
                return@setOnClickListener
            }
            loadEmployee(empCode)
        }
    }

    private fun loadEmployee(empCode: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.cardEmployee.visibility = View.GONE
        binding.cardCamera.visibility = View.GONE
        capturedBase64 = null
        binding.btnRegisterFace.isEnabled = false

        RetrofitClient.getApiService(this).getOnBoardingEmployee(empCode, if (branchId > 0) branchId else null)
            .enqueue(object : Callback<OnBoardingEmployeeResponse> {
                override fun onResponse(call: Call<OnBoardingEmployeeResponse>, response: Response<OnBoardingEmployeeResponse>) {
                    binding.progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body()?.status == "success") {
                        val emp = response.body()!!
                        currentEmpCode = emp.empCode ?: ""
                        currentEbId = emp.ebId ?: 0
                        binding.tvEmpName.text = emp.name ?: "-"
                        binding.tvEmpCode.text = "Code: ${emp.empCode ?: "-"}"
                        binding.tvDeptDesig.text = "${emp.departmentName ?: "-"} | ${emp.designationName ?: "-"}"
                        updateFaceCount(emp.faceCount ?: 0, emp.canRegister ?: false)
                        binding.cardEmployee.visibility = View.VISIBLE
                    } else {
                        val msg = response.body()?.message ?: "Employee not found"
                        Toast.makeText(this@OnBoardingActivity, msg, Toast.LENGTH_LONG).show()
                    }
                }
                override fun onFailure(call: Call<OnBoardingEmployeeResponse>, t: Throwable) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@OnBoardingActivity, "Network error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun updateFaceCount(count: Int, canRegister: Boolean) {
        binding.tvFaceCount.text = "Faces Registered: $count / 3"
        // Colour indicators
        val dot1 = binding.dotFace1
        val dot2 = binding.dotFace2
        val dot3 = binding.dotFace3
        val activeColor = getColor(R.color.toolbar_dark_blue)
        val inactiveColor = getColor(R.color.input_border)
        dot1.setColorFilter(if (count >= 1) activeColor else inactiveColor)
        dot2.setColorFilter(if (count >= 2) activeColor else inactiveColor)
        dot3.setColorFilter(if (count >= 3) activeColor else inactiveColor)

        binding.cardCamera.visibility = if (canRegister) View.VISIBLE else View.GONE
        if (!canRegister) {
            Toast.makeText(this, "Maximum 3 faces already registered for this employee.", Toast.LENGTH_LONG).show()
        }
    }

    // ── Camera ───────────────────────────────────────────────────

    private fun setupCaptureButton() {
        binding.btnCapture.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun openCamera() {
        val dir = File(filesDir, "face_photos").also { it.mkdirs() }
        photoFile = File(dir, "face_${System.currentTimeMillis()}.jpg")
        photoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile!!)
        cameraLauncher.launch(photoUri)
    }

    // ── Register face ────────────────────────────────────────────

    private fun setupRegisterButton() {
        binding.btnRegisterFace.setOnClickListener {
            val base64 = capturedBase64
            if (base64 == null) {
                Toast.makeText(this, "Please capture a face photo first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (currentEmpCode.isEmpty()) {
                Toast.makeText(this, "No employee selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            registerFace(currentEmpCode, base64)
        }
    }

    private fun registerFace(empCode: String, base64: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnRegisterFace.isEnabled = false

        val request = OnBoardingRegisterRequest(
            empCode = empCode,
            faceImage = base64,
            branchId = if (branchId > 0) branchId else null
        )
        RetrofitClient.getApiService(this).registerOnBoardingFace(request)
            .enqueue(object : Callback<OnBoardingRegisterResponse> {
                override fun onResponse(call: Call<OnBoardingRegisterResponse>, response: Response<OnBoardingRegisterResponse>) {
                    binding.progressBar.visibility = View.GONE
                    val body = response.body()
                    if (response.isSuccessful && body?.status == "success") {
                        Toast.makeText(this@OnBoardingActivity, body.message ?: "Face registered!", Toast.LENGTH_LONG).show()
                        capturedBase64 = null
                        binding.ivFacePreview.setImageResource(R.drawable.ic_person)
                        // Reload employee to refresh count
                        loadEmployee(empCode)
                    } else {
                        val msg = body?.message ?: "Failed to register face"
                        Toast.makeText(this@OnBoardingActivity, msg, Toast.LENGTH_LONG).show()
                        binding.btnRegisterFace.isEnabled = true
                    }
                }
                override fun onFailure(call: Call<OnBoardingRegisterResponse>, t: Throwable) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRegisterFace.isEnabled = true
                    Toast.makeText(this@OnBoardingActivity, "Network error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            })
    }
}

