package com.example.myhrms.api

import com.google.gson.annotations.SerializedName

// ── Hands Report — view vw_hands_report ──────────────────────────────────────
// One row per department + designation (particular). Pivoted Shift {A, B1, B2, C}
// x Shed {Old (os) / New (ns)}: mh_* = active machine count (M/H), hands_* =
// actual hands = SUM(working_hours)/8. total_hands = honest head-count.

data class HandsReportRow(
    @SerializedName("dept_desc")   val deptDesc: String?,
    @SerializedName("dept_code")   val deptCode: String?,
    @SerializedName("particular")  val particular: String?,
    @SerializedName("mh_a_os")     val mhAOs: Double?,
    @SerializedName("hands_a_os")  val handsAOs: Double?,
    @SerializedName("mh_a_ns")     val mhANs: Double?,
    @SerializedName("hands_a_ns")  val handsANs: Double?,
    @SerializedName("mh_b1_os")    val mhB1Os: Double?,
    @SerializedName("hands_b1_os") val handsB1Os: Double?,
    @SerializedName("mh_b1_ns")    val mhB1Ns: Double?,
    @SerializedName("hands_b1_ns") val handsB1Ns: Double?,
    @SerializedName("mh_b2_os")    val mhB2Os: Double?,
    @SerializedName("hands_b2_os") val handsB2Os: Double?,
    @SerializedName("mh_b2_ns")    val mhB2Ns: Double?,
    @SerializedName("hands_b2_ns") val handsB2Ns: Double?,
    @SerializedName("mh_c_os")     val mhCOs: Double?,
    @SerializedName("hands_c_os")  val handsCOs: Double?,
    @SerializedName("mh_c_ns")     val mhCNs: Double?,
    @SerializedName("hands_c_ns")  val handsCNs: Double?,
    @SerializedName("total_hands") val totalHands: Double?
) {
    /** 16 measure cells in display order: (A,B1,B2,C) x (Old,New) x (M/H,Hands). */
    fun cells(): List<Double?> = listOf(
        mhAOs, handsAOs, mhANs, handsANs,
        mhB1Os, handsB1Os, mhB1Ns, handsB1Ns,
        mhB2Os, handsB2Os, mhB2Ns, handsB2Ns,
        mhCOs, handsCOs, mhCNs, handsCNs
    )
}

data class HandsReportResponse(
    @SerializedName("status") val status: String?,
    @SerializedName("total")  val total: Int?,
    @SerializedName("rows")   val rows: List<HandsReportRow>?
)
