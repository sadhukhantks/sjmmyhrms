package com.example.myhrms.api

import com.google.gson.annotations.SerializedName

// ── Actual-vs-Std Hands Report — view vw_hands_std_report ─────────────────────
// One row per department + designation (particular). Per shift A/B/C: actual
// hands (act_) and standard hands (std_). Excess/Short (= act - std) and the
// Total group are derived on the client.

data class HandsStdReportRow(
    @SerializedName("dept_desc")  val deptDesc: String?,
    @SerializedName("dept_code")  val deptCode: String?,
    @SerializedName("particular") val particular: String?,
    @SerializedName("act_a")      val actA: Double?,
    @SerializedName("std_a")      val stdA: Double?,
    @SerializedName("act_b")      val actB: Double?,
    @SerializedName("std_b")      val stdB: Double?,
    @SerializedName("act_c")      val actC: Double?,
    @SerializedName("std_c")      val stdC: Double?
)

data class HandsStdReportResponse(
    @SerializedName("status") val status: String?,
    @SerializedName("total")  val total: Int?,
    @SerializedName("rows")   val rows: List<HandsStdReportRow>?
)
