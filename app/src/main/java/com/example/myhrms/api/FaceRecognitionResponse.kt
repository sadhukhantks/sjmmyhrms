package com.example.myhrms.api

import com.google.gson.annotations.SerializedName

data class FaceRecognitionResponse(
    @SerializedName("status")
    val status: String?,

    @SerializedName("emp_code")
    val empCode: String?,

    @SerializedName("emp_name")
    val empName: String?,

    @SerializedName("photo_html")
    val photoHtml: String?,

    @SerializedName("message")
    val message: String?
)

