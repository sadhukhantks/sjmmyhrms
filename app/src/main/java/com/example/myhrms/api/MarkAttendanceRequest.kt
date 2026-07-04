package com.example.myhrms.api

import com.google.gson.annotations.SerializedName

data class MarkAttendanceRequest(
    @SerializedName("emp_code")
    val empCode: String,

    @SerializedName("status")
    val status: String,  // "Face" or "Manual"

    @SerializedName("att_type")
    val attType: String,  // R=Regular, O=OT, C=Cash

    @SerializedName("department_id")
    val departmentId: Int? = null,

    @SerializedName("shift_id")
    val shiftId: Int? = null,

    @SerializedName("designation_id")
    val designationId: Int? = null,

    @SerializedName("attendance_date")
    val attendanceDate: String? = null,

    @SerializedName("shift_hours")
    val shiftHours: Double? = null,

    @SerializedName("working_hours")
    val workingHours: Double? = null,

    @SerializedName("idle_hours")
    val idleHours: Double? = null,

    @SerializedName("machine_ids")
    val machineIds: List<Int>? = null,

    @SerializedName("branch_id")
    val branchId: Int? = null,

    @SerializedName("shed_type")
    val shedType: String? = null  // "Old" or "New"
)
