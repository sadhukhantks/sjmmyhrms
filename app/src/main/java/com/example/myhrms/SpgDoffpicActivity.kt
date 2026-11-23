package com.example.myhrms

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale

/**
 * Same entry screen as SpgDoffEntry1Activity (menu id 52), plus a camera
 * scanner: aim the yellow guide box at the digital weight scale display and
 * the recognised reading is filled into Gross Wt automatically. Only the
 * pixels inside the guide box are OCR'd, so surrounding text can't pollute
 * the reading.
 */
class SpgDoffpicActivity : SpgDoffEntry1Activity() {

    private lateinit var previewFrame: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var guideBox: View
    private lateinit var btnScan: Button
    private lateinit var tvScanStatus: TextView

    private var cameraProvider: ProcessCameraProvider? = null
    private var scanning = false
    private val scanHandler = Handler(Looper.getMainLooper())
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    // A reading is applied only after it repeats on consecutive scans, so a
    // half-lit display or an OCR flicker doesn't land in Gross Wt.
    private var lastCandidate = ""
    private var candidateStreak = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<TextView>(R.id.tvScreenTitle).text = "Spg Doff with Pic"
        findViewById<TextView>(R.id.tvFormTitle).text = "Spg Doff with Pic"
        insertCameraSection()
    }

    /** Adds the "Scan Scale" button + camera preview just above the weights row. */
    private fun insertCameraSection() {
        val dp = resources.displayMetrics.density
        val form = findViewById<LinearLayout>(R.id.llEntryForm)

        btnScan = Button(this).apply {
            text = "📷 Scan Scale"
            textSize = 12f
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1565C0"))
            setOnClickListener { if (scanning) stopScan() else ensurePermissionAndScan() }
        }
        tvScanStatus = TextView(this).apply {
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#2E7D32"))
            setPadding((14 * dp).toInt(), 0, 0, 0)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(btnScan, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, (42 * dp).toInt()))
            addView(tvScanStatus)
        }

        previewView = PreviewView(this)
        guideBox = View(this).apply {
            background = GradientDrawable().apply {
                setStroke((2 * dp).toInt(), Color.parseColor("#FFD600"))
                setColor(Color.TRANSPARENT)
            }
        }
        previewFrame = FrameLayout(this).apply {
            visibility = View.GONE
            addView(previewView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(guideBox, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, (80 * dp).toInt(), Gravity.CENTER
            ).apply {
                marginStart = (30 * dp).toInt()
                marginEnd = (30 * dp).toInt()
            })
        }

        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            addView(previewFrame, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (220 * dp).toInt()
            ).apply { topMargin = (6 * dp).toInt() })
        }
        // Last child of the form is the Gross/Tare/Net/Save row — insert before it.
        form.addView(section, form.childCount - 1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (10 * dp).toInt() })
    }

    // ── Camera ───────────────────────────────────────────────────────────────
    private fun ensurePermissionAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startScan()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 4101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 4101) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startScan()
            else Toast.makeText(this, "Camera permission is required to scan the scale", Toast.LENGTH_LONG).show()
        }
    }

    private fun startScan() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            cameraProvider = provider

            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            try {
                provider.unbindAll()
                val cam = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                // Under-expose: lit LED segments stay readable, dim ghost
                // segments (the phantom leading "8") drop out of the image.
                val expState = cam.cameraInfo.exposureState
                if (expState.isExposureCompensationSupported) {
                    cam.cameraControl.setExposureCompensationIndex(
                        expState.exposureCompensationRange.lower / 2)
                }
                scanning = true
                previewFrame.visibility = View.VISIBLE
                btnScan.text = "⏹ Stop Scan"
                tvScanStatus.text = "…"
                lastCandidate = ""
                candidateStreak = 0
                scanHandler.postDelayed(scanTick, 500)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopScan() {
        scanning = false
        scanHandler.removeCallbacks(scanTick)
        cameraProvider?.unbindAll()
        previewFrame.visibility = View.GONE
        btnScan.text = "📷 Scan Scale"
        tvScanStatus.text = ""
    }

    /** Every ~300ms: crop the guide-box area out of the on-screen preview and
     *  OCR just that. The crop is in view coordinates, so what the user frames
     *  inside the yellow box is exactly what gets read. */
    private val scanTick = object : Runnable {
        override fun run() {
            if (!scanning) return
            val bmp = previewView.bitmap
            if (bmp == null || previewFrame.width == 0) {
                scanHandler.postDelayed(this, 300); return
            }
            val sx = bmp.width.toFloat() / previewFrame.width
            val sy = bmp.height.toFloat() / previewFrame.height
            val l = (guideBox.left * sx).toInt().coerceIn(0, bmp.width - 2)
            val t = (guideBox.top * sy).toInt().coerceIn(0, bmp.height - 2)
            val w = (guideBox.width * sx).toInt().coerceIn(1, bmp.width - l)
            val h = (guideBox.height * sy).toInt().coerceIn(1, bmp.height - t)
            val crop = binarize(Bitmap.createBitmap(bmp, l, t, w, h))
            recognizer.process(InputImage.fromBitmap(crop, 0))
                .addOnSuccessListener { onTextFound(it) }
                .addOnCompleteListener { if (scanning) scanHandler.postDelayed(this, 300) }
        }
    }

    /** Keep only glowing segments: bright pixels become black digits on a
     *  white background, everything else washes out. The unlit ghost segments
     *  (the faint "8888.8" the display shows) are far dimmer than lit ones,
     *  so they disappear — and an idle scale reads as nothing at all. */
    private fun binarize(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val lum = IntArray(px.size)
        var maxLum = 0
        for (i in px.indices) {
            val p = px[i]
            val l = maxOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
            lum[i] = l
            if (l > maxLum) maxLum = l
        }
        // ponytail: fixed 60%-of-max threshold with a 110 floor; make it a
        // seek-bar calibration knob if some scale/lighting combo defeats it.
        val threshold = maxOf((maxLum * 6) / 10, 110)
        val black = 0xFF000000.toInt(); val white = 0xFFFFFFFF.toInt()
        for (i in px.indices) px[i] = if (lum[i] >= threshold) black else white
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** 7-segment displays often OCR as several broken fragments — join them
     *  left-to-right, normalise, and sanity-check before accepting. */
    private fun onTextFound(result: Text) {
        val frags = mutableListOf<Pair<Int, String>>()
        for (block in result.textBlocks) for (line in block.lines) {
            if (!line.text.any { it.isDigit() }) continue
            frags += (line.boundingBox?.left ?: 0) to line.text
        }
        val raw = frags.sortedBy { it.first }.joinToString("") { it.second }
        val fill = parseReading(raw)

        if (fill == null) {
            candidateStreak = 0
            runOnUiThread { if (scanning) tvScanStatus.text = if (raw.isBlank()) "…" else "? ${raw.trim()}" }
            return
        }
        if (fill == lastCandidate) candidateStreak++ else { lastCandidate = fill; candidateStreak = 1 }

        runOnUiThread {
            if (!scanning) return@runOnUiThread
            tvScanStatus.text = fill
            if (candidateStreak >= 3 && etGrossWt.text.toString() != fill) {
                etGrossWt.setText(fill)   // Gross Wt watcher recalculates Net
            }
        }
    }

    companion object {
        /**
         * Normalise a joined OCR string into a plausible scale reading, or
         * null if it doesn't look like one. Keeps only the last dot as the
         * decimal point ("8.452.5" → "8452.5"); rejects non-positive, ≥ 10 t,
         * and lone-digit fragments (needs a decimal point or ≥ 3 digits).
         */
        fun parseReading(rawJoined: String): String? {
            // trim('.'): glowing indicators (kg lamp, unit dot) OCR as stray
            // edge dots — "452.5." must not collapse into 4525.
            val joined = rawJoined.uppercase(Locale.ROOT)
                .replace("KG", "")
                .replace(',', '.')
                .replace(Regex("[^0-9.]"), "")
                .trim('.')
            val lastDot = joined.lastIndexOf('.')
            val normal = if (lastDot >= 0)
                joined.substring(0, lastDot).replace(".", "") + joined.substring(lastDot)
            else joined
            val v = normal.toDoubleOrNull() ?: return null
            if (v <= 0 || v >= 10000) return null
            if (!normal.contains('.') && normal.length < 3) return null
            // All-8s is the display's idle/ghost pattern ("8888.8"), not a weight.
            if (normal.replace(".", "").all { it == '8' }) return null
            return normal
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scanning = false
        scanHandler.removeCallbacks(scanTick)
        cameraProvider?.unbindAll()
        recognizer.close()
    }
}
