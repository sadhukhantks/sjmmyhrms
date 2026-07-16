package com.example.myhrms.api

import android.content.Context
import android.content.SharedPreferences
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ApiConfig {

    private const val PREF_NAME = "api_config"
    private const val KEY_BASE_URL = "base_url"
    const val DEFAULT_URL = "https://attendance.infoskyglobalit.in/"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getBaseUrl(context: Context): String {
        return getPrefs(context).getString(KEY_BASE_URL, DEFAULT_URL) ?: DEFAULT_URL
    }

    /** Saves the URL only if Retrofit will accept it. Returns false (nothing saved) otherwise. */
    fun saveBaseUrl(context: Context, url: String): Boolean {
        var formattedUrl = url.trim()
        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
            formattedUrl = "http://$formattedUrl"
        }
        if (!formattedUrl.endsWith("/")) {
            formattedUrl = "$formattedUrl/"
        }
        // same parser Retrofit.baseUrl() uses — rejects typos like "193.168.0 133"
        if (formattedUrl.toHttpUrlOrNull() == null) {
            return false
        }
        getPrefs(context).edit().putString(KEY_BASE_URL, formattedUrl).apply()
        return true
    }
}

