package com.example.myhrms.api

import android.content.Context
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY  // Changed to BODY to see response content
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    // Configure Gson with lenient mode and proper null handling
    private val gson = GsonBuilder()
        .setLenient()  // Allow lenient JSON parsing
        .serializeNulls()  // Include null fields
        .create()

    private var currentBaseUrl: String? = null
    private var retrofit: Retrofit? = null

    fun getApiService(context: Context): ApiService {
        val savedUrl = ApiConfig.getBaseUrl(context)
        if (retrofit == null || currentBaseUrl != savedUrl) {
            currentBaseUrl = savedUrl
            retrofit = try {
                Retrofit.Builder()
                    .baseUrl(savedUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create(gson))  // Use configured Gson
                    .build()
            } catch (e: IllegalArgumentException) {
                // a bad URL saved by an older app version must not crash-loop the app
                Retrofit.Builder()
                    .baseUrl(ApiConfig.DEFAULT_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build()
            }
        }
        return retrofit!!.create(ApiService::class.java)
    }
}
