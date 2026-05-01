package com.example.myhrms.api

import com.google.gson.annotations.SerializedName

data class DashboardStatsResponse(
    @SerializedName("status")
    val status: String?,

    @SerializedName("date")
    val date: String?,

    @SerializedName("total_departments")
    val totalDepartments: Int,

    @SerializedName("total_designations")
    val totalDesignations: Int,

    @SerializedName("total_shifts")
    val totalShifts: Int,

    @SerializedName("total_employees")
    val totalEmployees: Int,

    @SerializedName("total_present")
    val totalPresent: Int,

    @SerializedName("present_face")
    val presentFace: Int,

    @SerializedName("present_manual")
    val presentManual: Int,

    @SerializedName("total_absent")
    val totalAbsent: Int,

    @SerializedName("department_wise")
    val departmentWise: List<DeptWiseStat>?,

    @SerializedName("department_present")
    val departmentPresent: List<DeptWiseStat>?,

    @SerializedName("department_master")
    val departmentMaster: List<DeptWiseStat>?,

    @SerializedName("message")
    val message: String?
)

data class DeptWiseStat(
    @SerializedName("department_id")
    val departmentId: Int,

    @SerializedName("department_name")
    val departmentName: String,

    @SerializedName("total_employees")
    val totalEmployees: Int,

    @SerializedName("present")
    val present: Int,

    @SerializedName("absent")
    val absent: Int
)

