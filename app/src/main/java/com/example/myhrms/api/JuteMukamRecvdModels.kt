package com.example.myhrms.api

import com.google.gson.annotations.SerializedName

// ── Setup (dropdown data) ─────────────────────────────────────────────────────

data class JmrParty(
    @SerializedName("party_id")   val partyId: Int,
    @SerializedName("party_name") val partyName: String?
)

data class JmrMukam(
    @SerializedName("mukam_id")   val mukamId: Int,
    @SerializedName("mukam_name") val mukamName: String?
)

data class JmrQuality(
    @SerializedName("jute_qlty_id") val qualityId: Int,
    @SerializedName("jute_quality") val quality: String?
)

data class JmrRecvdItem(
    @SerializedName("jute_mukam_recvd_no") val recvdNo: Int,
    @SerializedName("recvd_date")          val recvdDate: String?
)

data class JuteMukamRecvdSetupResponse(
    @SerializedName("status")     val status: String?,
    @SerializedName("message")    val message: String?,
    @SerializedName("parties")    val parties: List<JmrParty>?,
    @SerializedName("mukams")     val mukams: List<JmrMukam>?,
    @SerializedName("qualities")  val qualities: List<JmrQuality>?,
    @SerializedName("recvd_list") val recvdList: List<JmrRecvdItem>?
)

// ── Single entry ──────────────────────────────────────────────────────────────

data class JuteMukamRecvdEntry(
    @SerializedName("jute_mukam_recvd_no") val recvdNo: Int?,
    @SerializedName("recvd_date")          val recvdDate: String?,
    @SerializedName("party_id")            val partyId: Int?,
    @SerializedName("mukam_id")            val mukamId: Int?,
    @SerializedName("quality_id")          val qualityId: Int?,
    @SerializedName("gross_weight")        val grossWeight: Double?,
    @SerializedName("tare_weight")         val tareWeight: Double?,
    @SerializedName("net_weight")          val netWeight: Double?,
    @SerializedName("geo_location")        val geoLocation: String?,
    @SerializedName("geo_place")           val geoPlace: String?,
    @SerializedName("remarks")             val remarks: String?,
    @SerializedName("mukam_photo")         val mukamPhoto: String?
)

data class JuteMukamRecvdEntryResponse(
    @SerializedName("status")   val status: String?,
    @SerializedName("message")  val message: String?,
    @SerializedName("data")     val data: JuteMukamRecvdEntry?,
    @SerializedName("editable") val editable: Boolean = false
)

// ── Save / Update ─────────────────────────────────────────────────────────────

data class JuteMukamRecvdSaveRequest(
    @SerializedName("co_id")        val coId: Int,
    @SerializedName("recvd_date")   val recvdDate: String?,
    @SerializedName("party_id")     val partyId: Int?,
    @SerializedName("mukam_id")     val mukamId: Int?,
    @SerializedName("quality_id")   val qualityId: Int?,
    @SerializedName("gross_weight") val grossWeight: Double?,
    @SerializedName("tare_weight")  val tareWeight: Double?,
    @SerializedName("geo_location") val geoLocation: String?,
    @SerializedName("geo_place")    val geoPlace: String?,
    @SerializedName("remarks")      val remarks: String?,
    @SerializedName("mukam_photo")  val mukamPhoto: String?,
    @SerializedName("user_id")      val userId: Int
)

data class JuteMukamRecvdSaveResponse(
    @SerializedName("status")              val status: String?,
    @SerializedName("message")             val message: String?,
    @SerializedName("jute_mukam_recvd_no") val recvdNo: Int?
)
