package com.example.myhrms

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.myhrms.api.RetrofitClient
import com.example.myhrms.ui.ResultDialog
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream

private const val XLSX_MIME =
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** Hands Report .xlsx for [date] (+ optional [branchId], 0 = all). */
fun AppCompatActivity.exportHandsReportXlsx(date: String, branchId: Int) =
    downloadXlsxAndOpen(
        RetrofitClient.getApiService(this).downloadHandsReportXlsx(date, branchId.takeIf { it > 0 }),
        "HandsReport_$date.xlsx")

/** Actual-vs-Std Hands .xlsx for [date] (+ optional [branchId], 0 = all). */
fun AppCompatActivity.exportHandsStdReportXlsx(date: String, branchId: Int) =
    downloadXlsxAndOpen(
        RetrofitClient.getApiService(this).downloadHandsStdReportXlsx(date, branchId.takeIf { it > 0 }),
        "ActualVsStdHands_$date.xlsx")

/** SPG Doff (1) quality-shift .xlsx for [date] + [branchId]. */
fun AppCompatActivity.exportSpg1QualityXlsx(date: String, branchId: Int) =
    downloadXlsxAndOpen(
        RetrofitClient.getApiService(this).downloadSpg1QualityShiftXlsx(date, branchId),
        "SPG1_QualityShift_$date.xlsx")

/** Winding (2) quality-shift .xlsx for [date] + [branchId]. */
fun AppCompatActivity.exportWe2QualityXlsx(date: String, branchId: Int) =
    downloadXlsxAndOpen(
        RetrofitClient.getApiService(this).downloadWe2QualityShiftXlsx(date, branchId),
        "Winding2_QualityShift_$date.xlsx")

/** Assorting report .xlsx for [date] + [branchId]. */
fun AppCompatActivity.exportAssortingReportXlsx(date: String, branchId: Int) =
    downloadXlsxAndOpen(
        RetrofitClient.getApiService(this).downloadAssortingReportXlsx(date, branchId),
        "AssortingReport_$date.xlsx")

/**
 * Stream [call]'s .xlsx body into the cache as [fileName] and open it via a
 * chooser. Shared by the New Machine Entry grid and the Hands Report screen.
 */
private fun AppCompatActivity.downloadXlsxAndOpen(call: Call<ResponseBody>, fileName: String) {
    Toast.makeText(this, "Preparing Excel…", Toast.LENGTH_SHORT).show()
    call
        .enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                val body = response.body()
                if (!response.isSuccessful || body == null) {
                    ResultDialog.error(this@downloadXlsxAndOpen,
                        title = "Export Failed",
                        message = "Could not generate the report (${response.code()})")
                    return
                }
                try {
                    val dir  = File(cacheDir, "reports").apply { mkdirs() }
                    val file = File(dir, fileName)
                    body.byteStream().use { input ->
                        FileOutputStream(file).use { output -> input.copyTo(output) }
                    }
                    val uri = FileProvider.getUriForFile(
                        this@downloadXlsxAndOpen, "$packageName.fileprovider", file)
                    val view = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, XLSX_MIME)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        startActivity(Intent.createChooser(view, "Open Hands Report"))
                    } catch (_: Exception) {
                        Toast.makeText(this@downloadXlsxAndOpen,
                            "No app found to open Excel files", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    ResultDialog.error(this@downloadXlsxAndOpen,
                        title = "Error", message = e.localizedMessage ?: "Could not save the file")
                }
            }
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                ResultDialog.error(this@downloadXlsxAndOpen,
                    title = "Error", message = t.localizedMessage ?: "Network error")
            }
        })
}
