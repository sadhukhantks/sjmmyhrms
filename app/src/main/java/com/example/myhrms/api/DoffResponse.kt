package com.example.myhrms.api

import com.google.gson.annotations.SerializedName

// ── Spell ────────────────────────────────────────────────────────────────────

data class Spell(
    @SerializedName("spell_id")   val spellId: Int,
    @SerializedName("spell_name") val spellName: String?
) {
    override fun toString() = spellName ?: ""
}

data class SpellResponse(
    @SerializedName("status") val status: String?,
    @SerializedName("spells") val spells: List<Spell>?
)

// ── Doff master rows ─────────────────────────────────────────────────────────

data class DoffMachine(
    @SerializedName("mc_id")   val mcId: Int,
    @SerializedName("mc_name") val mcName: String?,
    @SerializedName("mc_code") val mcCode: String?
) {
    override fun toString(): String =
        listOfNotNull(mcCode?.takeIf { it.isNotBlank() }, mcName?.takeIf { it.isNotBlank() })
            .joinToString(" - ").ifBlank { "MC $mcId" }
}

data class DoffMachineResponse(
    @SerializedName("status")   val status: String?,
    @SerializedName("machines") val machines: List<DoffMachine>?
)

data class SpinningQuality(
    @SerializedName("quality_id")   val qualityId: Int,
    @SerializedName("quality_name") val qualityName: String?
) {
    override fun toString() = qualityName ?: ""
}

data class SpinningQualityResponse(
    @SerializedName("status")    val status: String?,
    @SerializedName("qualities") val qualities: List<SpinningQuality>?
)

data class Trolly(
    @SerializedName("trolly_id")     val trollyId: Int,
    @SerializedName("trolly_name")   val trollyName: String?,
    @SerializedName("trolly_weight") val trollyWeight: Double?,
    @SerializedName("bucket_weight") val bucketWeight: Double?
) {
    override fun toString() = trollyName ?: "T$trollyId"
}

data class TrollyResponse(
    @SerializedName("status")    val status: String?,
    @SerializedName("trollies")  val trollies: List<Trolly>?
)

// ── Defaults helper response ─────────────────────────────────────────────────

data class DoffDefaultsResponse(
    @SerializedName("status")        val status: String?,
    @SerializedName("quality_id")    val qualityId: Int?,
    @SerializedName("quality_name")  val qualityName: String?,
    @SerializedName("trolly_id")     val trollyId: Int?,
    @SerializedName("trolly_name")   val trollyName: String?,
    @SerializedName("trolly_weight") val trollyWeight: Double?,
    @SerializedName("bucket_weight") val bucketWeight: Double?,
    @SerializedName("eb_id")         val ebId: Int?,
    @SerializedName("emp_code")      val empCode: String?,
    @SerializedName("emp_name")      val empName: String?
)

// ── Transaction list / save ──────────────────────────────────────────────────

data class DoffTransaction(
    @SerializedName("id")            val id: Int?,
    @SerializedName("doff_date")     val doffDate: String?,
    @SerializedName("spell_id")      val spellId: Int?,
    @SerializedName("spell_name")    val spellName: String?,
    @SerializedName("mc_id")         val mcId: Int?,
    @SerializedName("mc_name")       val mcName: String?,
    @SerializedName("mc_code")       val mcCode: String?,
    @SerializedName("quality_id")    val qualityId: Int?,
    @SerializedName("quality_name")  val qualityName: String?,
    @SerializedName("trolly_id")     val trollyId: Int?,
    @SerializedName("trolly_name")   val trollyName: String?,
    @SerializedName("trolly_weight") val trollyWeight: Double?,
    @SerializedName("bucket_weight") val bucketWeight: Double?,
    @SerializedName("eb_id")         val ebId: Int?,
    @SerializedName("emp_code")      val empCode: String?,
    @SerializedName("emp_name")      val empName: String?,
    @SerializedName("gross_weight")  val grossWeight: Double?,
    @SerializedName("tare_weight")   val tareWeight: Double?,
    @SerializedName("net_weight")    val netWeight: Double?,
    @SerializedName("weight_type")   val weightType: String?,
    @SerializedName("branch_id")     val branchId: Int?
)

data class DoffListResponse(
    @SerializedName("status")       val status: String?,
    @SerializedName("message")      val message: String?,
    @SerializedName("transactions") val transactions: List<DoffTransaction>?
)

data class DoffSaveRequest(
    @SerializedName("id")           val id: Int? = null,
    @SerializedName("doff_date")    val doffDate: String,
    @SerializedName("spell_id")     val spellId: Int,
    @SerializedName("mc_id")        val mcId: Int,
    @SerializedName("quality_id")   val qualityId: Int?,
    @SerializedName("trolly_id")    val trollyId: Int?,
    @SerializedName("eb_id")        val ebId: Int,
    @SerializedName("gross_weight") val grossWeight: Double,
    @SerializedName("tare_weight")  val tareWeight: Double,
    @SerializedName("net_weight")   val netWeight: Double,
    @SerializedName("weight_type")  val weightType: String? = null,
    @SerializedName("branch_id")    val branchId: Int?,
    @SerializedName("user_id")      val userId: Int
)

data class DoffSaveResponse(
    @SerializedName("status")  val status: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("id")      val id: Int?
)

// ── Validation / summary for "New Doff Entry" ────────────────────────────────

data class DoffMachineValidateResponse(
    @SerializedName("status")              val status: String?,
    @SerializedName("message")             val message: String?,
    @SerializedName("mc_id")               val mcId: Int?,
    @SerializedName("mc_no")               val mcNo: String?,
    @SerializedName("mc_name")             val mcName: String?,
    @SerializedName("mc_code")             val mcCode: String?,
    @SerializedName("mech_posting_code")   val mechPostingCode: Int?,
    @SerializedName("trolly_id")           val trollyId: Int?,
    @SerializedName("trolly_name")         val trollyName: String?,
    @SerializedName("trolly_posting_code") val trollyPostingCode: Int?,
    @SerializedName("trolly_weight")       val trollyWeight: Double?,
    @SerializedName("bucket_weight")       val bucketWeight: Double?
)

data class DoffTrollyValidateResponse(
    @SerializedName("status")        val status: String?,
    @SerializedName("message")       val message: String?,
    @SerializedName("trolly_id")     val trollyId: Int?,
    @SerializedName("trolly_no")     val trollyNo: String?,
    @SerializedName("trolly_name")   val trollyName: String?,
    @SerializedName("trolly_weight") val trollyWeight: Double?,
    @SerializedName("bucket_weight") val bucketWeight: Double?
)

data class DoffSummaryRow(
    @SerializedName("mc_id")      val mcId: Int?,
    @SerializedName("mc_no")      val mcNo: String?,
    @SerializedName("mc_name")    val mcName: String?,
    @SerializedName("no_of_doff") val noOfDoff: Int?,
    @SerializedName("total_wt")   val totalWt: Double?
)

data class DoffSummaryResponse(
    @SerializedName("status")  val status: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("summary") val summary: List<DoffSummaryRow>?
)

// ── Spell-wise Frame Entry ───────────────────────────────────────────────────

data class FrameEntriesResponse(
    @SerializedName("status")  val status: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("mc_ids")  val mcIds: List<Int>?
)

data class FrameEntriesSaveRequest(
    @SerializedName("date")      val date: String,
    @SerializedName("spell_id")  val spellId: Int,
    @SerializedName("branch_id") val branchId: Int,
    @SerializedName("user_id")   val userId: Int,
    @SerializedName("mc_ids")    val mcIds: List<Int>
)

data class FrameEntriesSaveResponse(
    @SerializedName("status")  val status: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("count")   val count: Int?
)

