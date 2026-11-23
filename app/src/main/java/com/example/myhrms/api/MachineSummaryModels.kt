package com.example.myhrms.api

import com.google.gson.annotations.SerializedName

// ── Daily Machine Summary (tbl_daily_summ_mechine_data) ──────────────────────
// Date + machine + 5 spell production values (a1, a2, b1, b2, c).
// shift_a/b/c are derived on the backend (a1+a2, b1+b2, c).
// Machine dropdown reuses getRollStockMcCodes (mechine_code_master).

data class MachineSummaryEntry(
    @SerializedName("id")                val id: Int?,
    @SerializedName("tran_date")         val tranDate: String?,
    @SerializedName("mc_code_id")        val mcCodeId: Int?,
    @SerializedName("mc_code")           val mcCode: String?,
    @SerializedName("mechine_type_name") val mechineTypeName: String?,
    @SerializedName("spell_a1")          val spellA1: Double?,
    @SerializedName("spell_a2")          val spellA2: Double?,
    @SerializedName("spell_b1")          val spellB1: Double?,
    @SerializedName("spell_b2")          val spellB2: Double?,
    @SerializedName("spell_c")           val spellC: Double?,
    @SerializedName("shift_a")           val shiftA: Double?,
    @SerializedName("shift_b")           val shiftB: Double?,
    @SerializedName("shift_c")           val shiftC: Double?
) {
    fun machineLabel(): String =
        (mechineTypeName ?: mcCode ?: "MC$mcCodeId").trim()
}

data class MachineSummaryListResponse(
    @SerializedName("status")  val status: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("entries") val entries: List<MachineSummaryEntry>?
)

data class MachineSummarySaveRequest(
    @SerializedName("date")       val date: String,
    @SerializedName("branch_id")  val branchId: Int,
    @SerializedName("mc_code_id") val mcCodeId: Int,
    @SerializedName("spell_a1")   val spellA1: Double?,
    @SerializedName("spell_a2")   val spellA2: Double?,
    @SerializedName("spell_b1")   val spellB1: Double?,
    @SerializedName("spell_b2")   val spellB2: Double?,
    @SerializedName("spell_c")    val spellC: Double?,
    @SerializedName("user_id")    val userId: Int
)

data class MachineSummaryUpdateRequest(
    @SerializedName("mc_code_id") val mcCodeId: Int?,
    @SerializedName("spell_a1")   val spellA1: Double?,
    @SerializedName("spell_a2")   val spellA2: Double?,
    @SerializedName("spell_b1")   val spellB1: Double?,
    @SerializedName("spell_b2")   val spellB2: Double?,
    @SerializedName("spell_c")    val spellC: Double?,
    @SerializedName("user_id")    val userId: Int?
)

data class MachineSummarySaveResponse(
    @SerializedName("status")  val status: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("id")      val id: Int?
)
