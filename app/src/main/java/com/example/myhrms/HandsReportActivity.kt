package com.example.myhrms

import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.webkit.WebView
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myhrms.api.AssortingReportResponse
import com.example.myhrms.api.GrandTotalRow
import com.example.myhrms.api.HandsReportResponse
import com.example.myhrms.api.HandsReportRow
import com.example.myhrms.api.HandsStdReportResponse
import com.example.myhrms.api.HandsStdReportRow
import com.example.myhrms.api.QualityShiftReportResponse
import com.example.myhrms.api.QualityShiftReportRow
import com.example.myhrms.api.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

/**
 * On-screen report viewer for the New Machine Entry hands reports, rendered as
 * an HTML table in a WebView (which also drives Print-as-PDF). The report shown
 * is chosen by the "REPORT_TYPE" extra:
 *   "hands" (default) — Hands Report from vw_hands_report (Shift A/B1/B2/C x
 *                       Old/New shed; M/H + Hands, with totals + B-difference).
 *   "std"             — Actual-vs-Std Hands from vw_hands_std_report (Shift A/B/C
 *                       Act/Std/Excess-Short + Total group).
 * The toolbar Excel icon exports the currently-shown report to .xlsx.
 */
class HandsReportActivity : AppCompatActivity() {

    private lateinit var webReport    : WebView
    private lateinit var pbReport      : ProgressBar
    private lateinit var tvReportEmpty : TextView

    private var branchId  = 0
    private var entryDate = ""      // yyyy-MM-dd
    private var dispDate  = ""      // dd-MM-yyyy
    private var reportReady = false
    private var reportType = "hands"

    private val reportName get() = when (reportType) {
        "std"       -> "Actual vs Std Hands"
        "spg1"      -> "SPG Doff (1) Quality Report"
        "we2"       -> "Winding (2) Quality Report"
        "assorting" -> "Assorting Report"
        else        -> "Hands Report"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hands_report)

        branchId  = intent.getIntExtra("BRANCH_ID", 0)
        entryDate = intent.getStringExtra("DATE").orEmpty()
        dispDate  = intent.getStringExtra("DATE_DISP").orEmpty().ifEmpty { entryDate }
        reportType = intent.getStringExtra("REPORT_TYPE") ?: "hands"

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }
        findViewById<TextView>(R.id.tvReportTitle).text = reportName

        webReport     = findViewById(R.id.webReport)
        pbReport      = findViewById(R.id.pbReport)
        tvReportEmpty = findViewById(R.id.tvReportEmpty)

        val onPrint = View.OnClickListener {
            if (reportReady) doPrint()
            else Toast.makeText(this, "Report is still loading", Toast.LENGTH_SHORT).show()
        }
        findViewById<ImageView>(R.id.ivPrint).setOnClickListener(onPrint)
        findViewById<android.widget.Button>(R.id.btnPrintPdf).setOnClickListener(onPrint)

        findViewById<ImageView>(R.id.ivHandsExcel).setOnClickListener {
            when (reportType) {
                "std"       -> exportHandsStdReportXlsx(entryDate, branchId)
                "spg1"      -> exportSpg1QualityXlsx(entryDate, branchId)
                "we2"       -> exportWe2QualityXlsx(entryDate, branchId)
                "assorting" -> exportAssortingReportXlsx(entryDate, branchId)
                else        -> exportHandsReportXlsx(entryDate, branchId)
            }
        }

        loadReport()
    }

    // ── Loading ─────────────────────────────────────────────────────────────────
    private fun loadReport() {
        pbReport.visibility      = View.VISIBLE
        tvReportEmpty.visibility = View.GONE
        webReport.visibility     = View.GONE
        when (reportType) {
            "std"       -> loadStd()
            "spg1"      -> loadQuality(we2 = false)
            "we2"       -> loadQuality(we2 = true)
            "assorting" -> loadAssorting()
            else        -> loadHands()
        }
    }

    private fun render(html: String) {
        pbReport.visibility  = View.GONE
        webReport.visibility = View.VISIBLE
        webReport.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        reportReady = true
    }

    private fun showEmpty(msg: String) {
        pbReport.visibility      = View.GONE
        tvReportEmpty.visibility = View.VISIBLE
        tvReportEmpty.text       = msg
    }

    private fun loadHands() {
        RetrofitClient.getApiService(this).getHandsReport(entryDate, branchId.takeIf { it > 0 })
            .enqueue(object : Callback<HandsReportResponse> {
                override fun onResponse(call: Call<HandsReportResponse>, response: Response<HandsReportResponse>) {
                    val rows = response.body()?.rows.orEmpty()
                    if (rows.isEmpty()) showEmpty("No hands data for this date")
                    else render(buildHtml(rows))
                }
                override fun onFailure(call: Call<HandsReportResponse>, t: Throwable) {
                    showEmpty("Failed to load report")
                }
            })
    }

    private fun loadStd() {
        RetrofitClient.getApiService(this).getHandsStdReport(entryDate, branchId.takeIf { it > 0 })
            .enqueue(object : Callback<HandsStdReportResponse> {
                override fun onResponse(call: Call<HandsStdReportResponse>, response: Response<HandsStdReportResponse>) {
                    val rows = response.body()?.rows.orEmpty()
                    if (rows.isEmpty()) showEmpty("No data for this date")
                    else render(buildStdHtml(rows))
                }
                override fun onFailure(call: Call<HandsStdReportResponse>, t: Throwable) {
                    showEmpty("Failed to load report")
                }
            })
    }

    private fun loadQuality(we2: Boolean) {
        val api = RetrofitClient.getApiService(this)
        val call = if (we2) api.getWe2QualityShiftReport(entryDate, branchId)
                   else     api.getSpg1QualityShiftReport(entryDate, branchId)
        call.enqueue(object : Callback<QualityShiftReportResponse> {
            override fun onResponse(call: Call<QualityShiftReportResponse>, response: Response<QualityShiftReportResponse>) {
                val rows = response.body()?.report.orEmpty()
                if (rows.isEmpty()) showEmpty("No data for this date")
                else render(buildQualityHtml(rows, response.body()?.grandTotal))
            }
            override fun onFailure(call: Call<QualityShiftReportResponse>, t: Throwable) {
                showEmpty("Failed to load report")
            }
        })
    }

    private fun loadAssorting() {
        RetrofitClient.getApiService(this).getAssortingReport(entryDate, branchId)
            .enqueue(object : Callback<AssortingReportResponse> {
                override fun onResponse(call: Call<AssortingReportResponse>, response: Response<AssortingReportResponse>) {
                    val body = response.body()
                    val sections = listOfNotNull(body?.bySelector, body?.byMachineType, body?.byQuality)
                    if (sections.all { it.rows.isNullOrEmpty() }) showEmpty("No data for this date")
                    else render(buildAssortingHtml(body))
                }
                override fun onFailure(call: Call<AssortingReportResponse>, t: Throwable) {
                    showEmpty("Failed to load report")
                }
            })
    }

    // ── Print as PDF ───────────────────────────────────────────────────────────
    private fun doPrint() {
        val printManager = getSystemService(PRINT_SERVICE) as PrintManager
        val jobName = "$reportName $dispDate"
        val adapter = webReport.createPrintDocumentAdapter(jobName)
        printManager.print(
            jobName, adapter,
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
                .build()
        )
    }

    // ── Shared formatting / head ────────────────────────────────────────────────
    // Blank a zero cell to match the paper form (empty rather than "0").
    private fun num(v: Double?): String {
        val d = v ?: 0.0
        if (d == 0.0) return ""
        return if (d % 1.0 == 0.0) d.toLong().toString()
               else String.format(Locale.US, "%.2f", d).trimEnd('0').trimEnd('.')
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun reportHead(title: String): String = """
        <html><head><meta name='viewport' content='width=device-width, initial-scale=1'>
        <style>
          @page { size: A4 landscape; margin: 10px; }
          body { font-family: sans-serif; margin: 8px; color: #1E1E1E; }
          .co { text-align:center; font-size:16px; font-weight:bold; }
          .ti { text-align:center; font-size:13px; font-weight:bold; margin-top:2px; }
          .dt { text-align:center; font-size:12px; margin-bottom:8px; }
          table { border-collapse: collapse; width: 100%; font-size: 10px; }
          th, td { border: 1px solid #999; padding: 2px 4px; text-align: center; }
          td.lbl, th.lbl { text-align: left; white-space: nowrap; }
          thead th { background: #1565C0; color: #FFFFFF; }
          tr.dept td { background: #BBDEFB; font-weight: bold; text-align: left; }
          tr.dtot td { background: #E3F2FD; font-weight: bold; }
          tr.gtot td { background: #1565C0; color: #FFFFFF; font-weight: bold; }
          tr.gsum td { background: #E3F2FD; font-weight: bold; }
        </style></head><body>
        <div class='co'>SARADA JUTE MILLS PRIVATE LIMITED</div>
        <div class='ti'>$title</div>
        <div class='dt'>Date: ${esc(dispDate)}</div>
    """.trimIndent()

    // ── Hands Report (vw_hands_report, 16 measures) ─────────────────────────────
    /** Column sums (16 measures) + honest head-count for a dept / grand total. */
    private class Sum {
        val c = DoubleArray(16)
        var totalHands = 0.0
        fun add(r: HandsReportRow) {
            val cells = r.cells()
            for (i in 0 until 16) c[i] += cells[i] ?: 0.0
            totalHands += r.totalHands ?: 0.0
        }
        /** Sum of the 8 Hands columns (odd indices). */
        fun handsSum(): Double = (1..15 step 2).sumOf { c[it] }
    }

    private fun StringBuilder.dataRow(r: HandsReportRow) {
        append("<tr>")
        append("<td class='lbl'>").append(esc(r.particular ?: "")).append("</td>")
        for (cell in r.cells()) append("<td>").append(num(cell)).append("</td>")
        append("</tr>")
    }

    private fun StringBuilder.totalRow(label: String, s: Sum, cls: String) {
        append("<tr class='").append(cls).append("'>")
        append("<td class='lbl'>").append(esc(label)).append("</td>")
        for (v in s.c) append("<td>").append(num(v)).append("</td>")
        append("</tr>")
    }

    private fun StringBuilder.noteRow(label: String, value: Double) {
        append("<tr class='gsum'>")
        append("<td class='lbl'>").append(esc(label)).append("</td>")
        append("<td>").append(num(value)).append("</td>")
        append("<td colspan='15'></td>")
        append("</tr>")
    }

    private fun buildHtml(rows: List<HandsReportRow>): String {
        val sb = StringBuilder()
        sb.append(reportHead("HANDS REPORT"))

        sb.append("<table><thead>")
        // shift bands over shed bands over M/H · Hands
        sb.append("<tr>")
            .append("<th class='lbl' rowspan='3'>Particular</th>")
            .append("<th colspan='4'>Shift A</th>")
            .append("<th colspan='4'>Shift B1</th>")
            .append("<th colspan='4'>Shift B2</th>")
            .append("<th colspan='4'>Shift C</th>")
            .append("</tr>")
        sb.append("<tr>")
        repeat(4) { sb.append("<th colspan='2'>Old Shed</th><th colspan='2'>New Shed</th>") }
        sb.append("</tr>")
        sb.append("<tr>")
        repeat(8) { sb.append("<th>M/H</th><th>Hands</th>") }
        sb.append("</tr></thead><tbody>")

        val grand = Sum()
        var i = 0
        while (i < rows.size) {
            val dept = rows[i].deptDesc ?: ""
            sb.append("<tr class='dept'><td colspan='17'>")
                .append(esc(dept.ifEmpty { "—" })).append("</td></tr>")
            val deptSum = Sum()
            while (i < rows.size && (rows[i].deptDesc ?: "") == dept) {
                val r = rows[i]
                sb.dataRow(r)
                deptSum.add(r); grand.add(r)
                i++
            }
            sb.totalRow("${dept.ifEmpty { "—" }} Total", deptSum, "dtot")
        }
        sb.totalRow("Grand Total", grand, "gtot")
        sb.noteRow("Grand Total Hands (as per data)", grand.totalHands)
        sb.noteRow("Difference (B counted in B1 & B2)", grand.handsSum() - grand.totalHands)

        sb.append("</tbody></table></body></html>")
        return sb.toString()
    }

    // ── Actual-vs-Std Hands (vw_hands_std_report) ───────────────────────────────
    /** 12 display cells from the 6 base measures [actA,stdA,actB,stdB,actC,stdC]. */
    private fun stdCells(a: DoubleArray): List<Double> {
        val actT = a[0] + a[2] + a[4]
        val stdT = a[1] + a[3] + a[5]
        return listOf(
            a[0], a[1], a[0] - a[1],
            a[2], a[3], a[2] - a[3],
            a[4], a[5], a[4] - a[5],
            actT, stdT, actT - stdT
        )
    }

    private fun StringBuilder.stdRow(label: String, base: DoubleArray, cls: String?) {
        append(if (cls == null) "<tr>" else "<tr class='$cls'>")
        append("<td class='lbl'>").append(esc(label)).append("</td>")
        for (v in stdCells(base)) append("<td>").append(num(v)).append("</td>")
        append("</tr>")
    }

    private fun buildStdHtml(rows: List<HandsStdReportRow>): String {
        val sb = StringBuilder()
        sb.append(reportHead("ACTUAL vs STD HANDS"))

        sb.append("<table><thead>")
        sb.append("<tr>")
            .append("<th class='lbl' rowspan='2'>Particular</th>")
            .append("<th colspan='3'>Shift A</th>")
            .append("<th colspan='3'>Shift B</th>")
            .append("<th colspan='3'>Shift C</th>")
            .append("<th colspan='3'>Total</th>")
            .append("</tr>")
        sb.append("<tr>")
        repeat(4) { sb.append("<th>Act</th><th>Std</th><th>Excess/Short</th>") }
        sb.append("</tr></thead><tbody>")

        val grand = DoubleArray(6)
        var i = 0
        while (i < rows.size) {
            val dept = rows[i].deptDesc ?: ""
            sb.append("<tr class='dept'><td colspan='13'>")
                .append(esc(dept.ifEmpty { "—" })).append("</td></tr>")
            val dsum = DoubleArray(6)
            while (i < rows.size && (rows[i].deptDesc ?: "") == dept) {
                val r = rows[i]
                val base = doubleArrayOf(
                    r.actA ?: 0.0, r.stdA ?: 0.0,
                    r.actB ?: 0.0, r.stdB ?: 0.0,
                    r.actC ?: 0.0, r.stdC ?: 0.0
                )
                for (k in 0..5) { dsum[k] += base[k]; grand[k] += base[k] }
                sb.stdRow(r.particular ?: "", base, null)
                i++
            }
            sb.stdRow("${dept.ifEmpty { "—" }} Total", dsum, "dtot")
        }
        sb.stdRow("Grand Total", grand, "gtot")

        sb.append("</tbody></table></body></html>")
        return sb.toString()
    }

    // ── Quality-wise Shift-wise report (SPG Doff 1 / Winding 2) ─────────────────
    private fun buildQualityHtml(rows: List<QualityShiftReportRow>, gt: GrandTotalRow?): String {
        val sb = StringBuilder()
        sb.append(reportHead(reportName.uppercase(Locale.US)))
        sb.append("<table><thead><tr>")
            .append("<th class='lbl'>Quality</th>")
            .append("<th>Shift A</th><th>Shift B</th><th>Shift C</th><th>Total</th>")
            .append("</tr></thead><tbody>")
        for (r in rows) {
            sb.append("<tr><td class='lbl'>").append(esc(r.qualityName ?: "")).append("</td>")
            for (v in listOf(r.shiftA, r.shiftB, r.shiftC, r.total))
                sb.append("<td>").append(num(v)).append("</td>")
            sb.append("</tr>")
        }
        if (gt != null) {
            sb.append("<tr class='gtot'><td class='lbl'>Total</td>")
            for (v in listOf(gt.shiftA, gt.shiftB, gt.shiftC, gt.total))
                sb.append("<td>").append(num(v)).append("</td>")
            sb.append("</tr>")
        }
        sb.append("</tbody></table></body></html>")
        return sb.toString()
    }

    // ── Assorting report (3 sections: selector / machine-type / quality) ────────
    private fun buildAssortingHtml(body: AssortingReportResponse?): String {
        val sb = StringBuilder()
        sb.append(reportHead("ASSORTING REPORT"))
        sb.append("<table><thead><tr>")
            .append("<th class='lbl'>Particular</th><th>Production</th>")
            .append("</tr></thead><tbody>")
        val sections = listOf(
            "Selector wise" to body?.bySelector,
            "Machine Type wise" to body?.byMachineType,
            "Quality wise" to body?.byQuality
        )
        for ((title, sec) in sections) {
            if (sec == null) continue
            sb.append("<tr class='dept'><td colspan='2'>").append(esc(title)).append("</td></tr>")
            for (row in sec.rows.orEmpty()) {
                sb.append("<tr><td class='lbl'>").append(esc(row.name ?: "")).append("</td>")
                    .append("<td>").append(num(row.production)).append("</td></tr>")
            }
            sb.append("<tr class='dtot'><td class='lbl'>Total</td><td>")
                .append(num(sec.grandTotal)).append("</td></tr>")
        }
        sb.append("</tbody></table></body></html>")
        return sb.toString()
    }
}
