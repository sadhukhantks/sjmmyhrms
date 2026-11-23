package com.example.myhrms

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.media.ExifInterface
import com.example.myhrms.api.*
import com.example.myhrms.ui.ResultDialog
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.util.Base64 as AndroidBase64

/**
 * Jute Mukam Received — single create/edit screen.
 * Ported from the vowerp3 web page (jutePurchase/juteMukamRecv). Entries stay
 * editable only within 30 minutes of creation (enforced server-side). Geo
 * location, place and photo are captured once and shown read-only afterwards.
 */
class JuteMukamReceivedActivity : AppCompatActivity() {

    private val apiDate  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dispDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    private var coId      = 0
    private var branchId  = 0
    private var userId    = 0
    private var entryDate = ""

    private lateinit var spRecvdNo        : Spinner
    private lateinit var tvEntryDate      : TextView
    private lateinit var acParty          : AutoCompleteTextView
    private lateinit var acMukam          : AutoCompleteTextView
    private lateinit var acQuality        : AutoCompleteTextView
    private lateinit var etGrossWt        : EditText
    private lateinit var etTareWt         : EditText
    private lateinit var etNetWt          : EditText
    private lateinit var etGeoLocation    : EditText
    private lateinit var etGeoPlace       : EditText
    private lateinit var btnRefreshLocation: Button
    private lateinit var etRemarks        : EditText
    private lateinit var ivPhoto          : ImageView
    private lateinit var tvPhotoEmpty     : TextView
    private lateinit var btnTakePhoto     : Button
    private lateinit var btnRemovePhoto   : Button
    private lateinit var btnSave          : Button

    private val partyField   = DropdownField()
    private val mukamField   = DropdownField()
    private val qualityField = DropdownField()

    /** null in slot 0 = "New"; recvd numbers follow. Parallel to the spinner. */
    private val recvdNoSlots = mutableListOf<Int?>()
    private var suppressRecvdSelect = false

    private var editingRecvdNo: Int? = null   // null = creating a new entry
    private var editable      = true          // for a loaded entry

    /** Raw base64 of the captured/loaded photo (no HTML wrapper); null = none. */
    private var capturedBase64: String? = null
    private var photoFile: File? = null
    private var photoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_jute_mukam_received)

        coId     = intent.getIntExtra("CO_ID", 0)
        branchId = intent.getIntExtra("BRANCH_ID", 0)
        userId   = getSharedPreferences("LoginPrefs", MODE_PRIVATE).getInt("user_id", 0)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        spRecvdNo         = findViewById(R.id.spRecvdNo)
        tvEntryDate       = findViewById(R.id.tvEntryDate)
        acParty           = findViewById(R.id.acParty)
        acMukam           = findViewById(R.id.acMukam)
        acQuality         = findViewById(R.id.acQuality)
        etGrossWt         = findViewById(R.id.etGrossWt)
        etTareWt          = findViewById(R.id.etTareWt)
        etNetWt           = findViewById(R.id.etNetWt)
        etGeoLocation     = findViewById(R.id.etGeoLocation)
        etGeoPlace        = findViewById(R.id.etGeoPlace)
        btnRefreshLocation= findViewById(R.id.btnRefreshLocation)
        etRemarks         = findViewById(R.id.etRemarks)
        ivPhoto           = findViewById(R.id.ivPhoto)
        tvPhotoEmpty      = findViewById(R.id.tvPhotoEmpty)
        btnTakePhoto      = findViewById(R.id.btnTakePhoto)
        btnRemovePhoto    = findViewById(R.id.btnRemovePhoto)
        btnSave           = findViewById(R.id.btnSave)

        partyField.attach(acParty)
        mukamField.attach(acMukam)
        qualityField.attach(acQuality)

        val cal = Calendar.getInstance()
        entryDate        = apiDate.format(cal.time)
        tvEntryDate.text = dispDate.format(cal.time)
        findViewById<View>(R.id.btnEntryDate).setOnClickListener { pickDate() }

        val netWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { recalcNet() }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        }
        etGrossWt.addTextChangedListener(netWatcher)
        etTareWt.addTextChangedListener(netWatcher)

        spRecvdNo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (suppressRecvdSelect) return
                onRecvdNoSelected(recvdNoSlots.getOrNull(pos))
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        btnRefreshLocation.setOnClickListener { captureLocation(silent = false) }
        btnTakePhoto.setOnClickListener { onTakePhotoClicked() }
        btnRemovePhoto.setOnClickListener { setPhotoBase64(null) }
        btnSave.setOnClickListener { saveEntry() }

        setPhotoBase64(null)
        loadSetup()
    }

    // ── Setup ──────────────────────────────────────────────────────────────────

    private fun loadSetup() {
        if (coId <= 0 && branchId <= 0) {
            Toast.makeText(this, "Branch not set", Toast.LENGTH_SHORT).show(); return
        }
        RetrofitClient.getApiService(this).getJuteMukamRecvdSetup(
            coId.takeIf { it > 0 }, branchId.takeIf { it > 0 })
            .enqueue(object : Callback<JuteMukamRecvdSetupResponse> {
                override fun onResponse(call: Call<JuteMukamRecvdSetupResponse>, response: Response<JuteMukamRecvdSetupResponse>) {
                    val body = response.body()
                    if (!response.isSuccessful || body?.status != "success") {
                        Toast.makeText(this@JuteMukamReceivedActivity,
                            body?.message ?: "Failed to load setup", Toast.LENGTH_SHORT).show()
                        return
                    }
                    partyField.setOptions(body.parties.orEmpty().map { Opt(it.partyId, it.partyName ?: "") })
                    mukamField.setOptions(body.mukams.orEmpty().map { Opt(it.mukamId, it.mukamName ?: "") })
                    qualityField.setOptions(body.qualities.orEmpty().map { Opt(it.qualityId, it.quality ?: "") })
                    buildRecvdNoSpinner(body.recvdList.orEmpty())
                    // New entry: auto-capture location silently on first load.
                    captureLocation(silent = true)
                }
                override fun onFailure(call: Call<JuteMukamRecvdSetupResponse>, t: Throwable) {
                    Toast.makeText(this@JuteMukamReceivedActivity,
                        "Failed to load setup: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun buildRecvdNoSpinner(list: List<JmrRecvdItem>) {
        recvdNoSlots.clear()
        val labels = mutableListOf<String>()
        recvdNoSlots.add(null); labels.add("New")
        for (r in list) {
            recvdNoSlots.add(r.recvdNo)
            labels.add(if (r.recvdDate.isNullOrBlank()) "${r.recvdNo}" else "${r.recvdNo} — ${r.recvdDate}")
        }
        suppressRecvdSelect = true
        spRecvdNo.adapter = ArrayAdapter(this, R.layout.spinner_item_black, labels)
            .also { it.setDropDownViewResource(R.layout.spinner_dropdown_item_black) }
        spRecvdNo.setSelection(0)
        spRecvdNo.post { suppressRecvdSelect = false }
    }

    private fun onRecvdNoSelected(recvdNo: Int?) {
        if (recvdNo == null) { resetToNew(); return }
        RetrofitClient.getApiService(this).getJuteMukamRecvdEntry(recvdNo)
            .enqueue(object : Callback<JuteMukamRecvdEntryResponse> {
                override fun onResponse(call: Call<JuteMukamRecvdEntryResponse>, response: Response<JuteMukamRecvdEntryResponse>) {
                    val body = response.body()
                    val d = body?.data
                    if (!response.isSuccessful || body?.status != "success" || d == null) {
                        Toast.makeText(this@JuteMukamReceivedActivity,
                            body?.message ?: "Failed to load entry", Toast.LENGTH_SHORT).show()
                        return
                    }
                    populateForm(d, body.editable)
                }
                override fun onFailure(call: Call<JuteMukamRecvdEntryResponse>, t: Throwable) {
                    Toast.makeText(this@JuteMukamReceivedActivity,
                        "Failed to load entry: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun populateForm(d: JuteMukamRecvdEntry, isEditable: Boolean) {
        editingRecvdNo = d.recvdNo
        editable = isEditable

        d.recvdDate?.takeIf { it.isNotBlank() }?.let {
            entryDate = it
            try { apiDate.parse(it)?.let { dt -> tvEntryDate.text = dispDate.format(dt) } }
            catch (_: Exception) { tvEntryDate.text = it }
        }
        partyField.select(d.partyId)
        mukamField.select(d.mukamId)
        qualityField.select(d.qualityId)
        etGrossWt.setText(d.grossWeight?.let { fmt(it) } ?: "")
        etTareWt.setText(d.tareWeight?.let { fmt(it) } ?: "")
        recalcNet()
        etGeoLocation.setText(d.geoLocation ?: "")
        etGeoPlace.setText(d.geoPlace ?: "")
        etRemarks.setText(d.remarks ?: "")
        setPhotoBase64(extractBase64(d.mukamPhoto))

        btnSave.text = "Update"
        applyEditable(editable)
        if (!editable) {
            ResultDialog.error(this, title = "View only",
                message = "This entry is older than 30 minutes and can no longer be edited.")
        }
    }

    private fun resetToNew() {
        editingRecvdNo = null
        editable = true
        val cal = Calendar.getInstance()
        entryDate = apiDate.format(cal.time)
        tvEntryDate.text = dispDate.format(cal.time)
        partyField.select(null)
        mukamField.select(null)
        qualityField.select(null)
        etGrossWt.setText("")
        etTareWt.setText("")
        etNetWt.setText("")
        etRemarks.setText("")
        setPhotoBase64(null)
        btnSave.text = "Save"
        applyEditable(true)
        captureLocation(silent = true)
    }

    /** Enable/disable the editable fields for a read-only (expired) entry. */
    private fun applyEditable(enabled: Boolean) {
        acParty.isEnabled = enabled
        acMukam.isEnabled = enabled
        acQuality.isEnabled = enabled
        etGrossWt.isEnabled = enabled
        etTareWt.isEnabled = enabled
        etRemarks.isEnabled = enabled
        btnTakePhoto.isEnabled = enabled
        btnRemovePhoto.isEnabled = enabled
        btnRefreshLocation.isEnabled = enabled
        findViewById<View>(R.id.btnEntryDate).isEnabled = enabled
        btnSave.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    // ── Date ───────────────────────────────────────────────────────────────────

    private fun pickDate() {
        val cal = Calendar.getInstance()
        try { apiDate.parse(entryDate)?.let { cal.time = it } } catch (_: Exception) {}
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            entryDate        = apiDate.format(cal.time)
            tvEntryDate.text = dispDate.format(cal.time)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    // ── Net weight ─────────────────────────────────────────────────────────────

    private fun recalcNet() {
        val g = etGrossWt.text.toString().toDoubleOrNull() ?: 0.0
        val t = etTareWt.text.toString().toDoubleOrNull() ?: 0.0
        etNetWt.setText(fmt(g - t))
    }

    private fun fmt(v: Double?) = String.format(Locale.getDefault(), "%.3f", v ?: 0.0)

    // ── Geo location ───────────────────────────────────────────────────────────

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) captureLocation(silent = false)
        else Toast.makeText(this, "Location permission is required", Toast.LENGTH_SHORT).show()
    }

    private fun captureLocation(silent: Boolean) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            if (silent) return
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        val lm = getSystemService(LOCATION_SERVICE) as? LocationManager ?: return
        val loc = lastKnownLocation(lm)
        if (loc != null) {
            applyLocation(loc.latitude, loc.longitude)
            return
        }
        // No cached fix — request a single fresh update. Override every
        // LocationListener callback: on minSdk 24 they are all abstract.
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(l: Location) { applyLocation(l.latitude, l.longitude) }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try {
            val provider = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
                LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            lm.requestSingleUpdate(provider, listener, mainLooper)
        } catch (_: Exception) {
            if (!silent) Toast.makeText(this, "Could not get location", Toast.LENGTH_SHORT).show()
        }
    }

    private fun lastKnownLocation(lm: LocationManager): Location? {
        return try {
            val g = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val n = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            when {
                g != null && n != null -> if (g.time >= n.time) g else n
                else -> g ?: n
            }
        } catch (_: SecurityException) { null }
    }

    private fun applyLocation(lat: Double, lng: Double) {
        etGeoLocation.setText(String.format(Locale.US, "%.6f,%.6f", lat, lng))
        // Reverse-geocode off the UI thread; Geocoder is blocking.
        Thread {
            val place = try {
                @Suppress("DEPRECATION")
                Geocoder(this, Locale.getDefault())
                    .getFromLocation(lat, lng, 1)?.firstOrNull()?.getAddressLine(0)
            } catch (_: Exception) { null }
            if (!place.isNullOrBlank()) runOnUiThread { etGeoPlace.setText(place) }
        }.start()
    }

    // ── Photo capture ──────────────────────────────────────────────────────────

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openCamera()
        else Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = photoFile
        if (success && file != null) {
            Thread {
                val b64 = try {
                    loadResizedJpegBase64(file)
                } catch (_: Exception) {
                    AndroidBase64.encodeToString(file.readBytes(), AndroidBase64.NO_WRAP)
                }
                runOnUiThread { setPhotoBase64(b64) }
            }.start()
        } else {
            Toast.makeText(this, "Photo capture cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onTakePhotoClicked() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() {
        val photosDir = File(cacheDir, "photos")
        photosDir.mkdirs()
        photoFile = File(photosDir, "mukam_${System.currentTimeMillis()}.jpg")
        photoUri = FileProvider.getUriForFile(
            this, "${applicationContext.packageName}.fileprovider", photoFile!!)
        cameraLauncher.launch(photoUri)
    }

    /** Decode → downscale → fix EXIF rotation → re-encode JPEG → base64. */
    private fun loadResizedJpegBase64(file: File, maxDim: Int = 1280, quality: Int = 70): String {
        val path = file.absolutePath
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / (sample * 2) >= maxDim) sample *= 2

        var bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalStateException("decode failed")

        val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (scale < 1f) {
            val scaled = Bitmap.createScaledBitmap(
                bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            if (scaled != bitmap) bitmap.recycle()
            bitmap = scaled
        }

        val rotation = when (ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90  -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation != 0f) {
            val m = Matrix().apply { postRotate(rotation) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            if (rotated != bitmap) bitmap.recycle()
            bitmap = rotated
        }

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        bitmap.recycle()
        return AndroidBase64.encodeToString(baos.toByteArray(), AndroidBase64.NO_WRAP)
    }

    /** Single source of truth for the photo UI + state. */
    private fun setPhotoBase64(b64: String?) {
        capturedBase64 = b64
        if (b64.isNullOrBlank()) {
            ivPhoto.visibility = View.GONE
            tvPhotoEmpty.visibility = View.VISIBLE
            btnRemovePhoto.visibility = View.GONE
            btnTakePhoto.text = "Take Photo"
        } else {
            try {
                val bytes = AndroidBase64.decode(b64, AndroidBase64.DEFAULT)
                ivPhoto.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                ivPhoto.visibility = View.VISIBLE
                tvPhotoEmpty.visibility = View.GONE
                btnRemovePhoto.visibility = View.VISIBLE
                btnTakePhoto.text = "Retake"
            } catch (_: Exception) {
                ivPhoto.visibility = View.GONE
                tvPhotoEmpty.visibility = View.VISIBLE
            }
        }
    }

    /** Pull the base64 payload out of a stored `<img src="data:...;base64,XXX"/>`. */
    private fun extractBase64(html: String?): String? {
        if (html.isNullOrBlank()) return null
        val m = Regex("base64,([^\"')\\s]+)").find(html)
        return m?.groupValues?.getOrNull(1) ?: html.takeIf { !it.contains("<") }
    }

    /** Wrap raw base64 as the same HTML `<img>` snippet the web screen stores. */
    private fun toPhotoHtml(b64: String?): String? =
        if (b64.isNullOrBlank()) null
        else "<img src=\"data:image/jpeg;base64,$b64\" alt=\"mukam photo\" style=\"max-width:100%;height:auto;\" />"

    // ── Save / Update ──────────────────────────────────────────────────────────

    private fun saveEntry() {
        val partyId = partyField.selectedId
        if (partyId == null) {
            Toast.makeText(this, "Please select a Party", Toast.LENGTH_SHORT).show(); return
        }
        val gross = etGrossWt.text.toString().toDoubleOrNull()
        val tare  = etTareWt.text.toString().toDoubleOrNull()

        btnSave.isEnabled = false
        val req = JuteMukamRecvdSaveRequest(
            coId        = coId,
            recvdDate   = entryDate.ifBlank { null },
            partyId     = partyId,
            mukamId     = mukamField.selectedId,
            qualityId   = qualityField.selectedId,
            grossWeight = gross,
            tareWeight  = tare,
            geoLocation = etGeoLocation.text.toString().ifBlank { null },
            geoPlace    = etGeoPlace.text.toString().ifBlank { null },
            remarks     = etRemarks.text.toString().ifBlank { null },
            mukamPhoto  = toPhotoHtml(capturedBase64),
            userId      = userId
        )

        val editId = editingRecvdNo
        val callback = object : Callback<JuteMukamRecvdSaveResponse> {
            override fun onResponse(call: Call<JuteMukamRecvdSaveResponse>, response: Response<JuteMukamRecvdSaveResponse>) {
                btnSave.isEnabled = true
                val body = response.body()
                if (response.isSuccessful && body?.status == "success") {
                    ResultDialog.success(this@JuteMukamReceivedActivity,
                        message = body.message ?: if (editId != null) "Updated" else "Saved") {
                        // Reload list + reset to a fresh New entry after dismissal.
                        loadSetup()
                        resetToNew()
                    }
                } else {
                    val msg = body?.message ?: "Could not save the entry"
                    val title = when {
                        response.code() == 403 -> "Edit window closed"
                        editId != null         -> "Update Failed"
                        else                   -> "Save Failed"
                    }
                    if (response.code() == 403) editable = false
                    ResultDialog.error(this@JuteMukamReceivedActivity, title = title, message = msg)
                }
            }
            override fun onFailure(call: Call<JuteMukamRecvdSaveResponse>, t: Throwable) {
                btnSave.isEnabled = true
                ResultDialog.error(this@JuteMukamReceivedActivity, title = "Error",
                    message = t.localizedMessage ?: "Network error")
            }
        }
        if (editId != null) {
            RetrofitClient.getApiService(this).updateJuteMukamRecvdEntry(editId, req).enqueue(callback)
        } else {
            RetrofitClient.getApiService(this).saveJuteMukamRecvdEntry(req).enqueue(callback)
        }
    }

    // ── Dropdown helper ────────────────────────────────────────────────────────

    private data class Opt(val id: Int, val label: String)

    /**
     * Wraps an AutoCompleteTextView as a searchable single-select that resolves
     * the typed/clicked text back to an option id.
     */
    private inner class DropdownField {
        private lateinit var view: AutoCompleteTextView
        private val options = mutableListOf<Opt>()
        var selectedId: Int? = null
            private set

        fun attach(v: AutoCompleteTextView) {
            view = v
            v.setOnItemClickListener { _, _, _, _ -> resolveFromText() }
            v.setOnFocusChangeListener { _, hasFocus -> if (hasFocus && options.isNotEmpty()) v.showDropDown() }
            v.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { resolveFromText() }
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
        }

        fun setOptions(opts: List<Opt>) {
            options.clear(); options.addAll(opts)
            view.setAdapter(ArrayAdapter(this@JuteMukamReceivedActivity,
                android.R.layout.simple_dropdown_item_1line, options.map { it.label }))
            resolveFromText()
        }

        fun select(id: Int?) {
            selectedId = id
            val label = options.firstOrNull { it.id == id }?.label ?: ""
            view.setText(label, false)
        }

        private fun resolveFromText() {
            val txt = view.text?.toString()?.trim().orEmpty()
            selectedId = options.firstOrNull { it.label.equals(txt, ignoreCase = true) }?.id
        }
    }
}
