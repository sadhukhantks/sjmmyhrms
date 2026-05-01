package com.example.myhrms.api

import android.content.Context
import android.content.SharedPreferences

object ApiConfig {

    private const val PREF_NAME = "api_config"
    private const val KEY_BASE_URL = "base_url"
    private const val DEFAULT_URL = "http://192.168.0.223:5051/"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getBaseUrl(context: Context): String {
        return getPrefs(context).getString(KEY_BASE_URL, DEFAULT_URL) ?: DEFAULT_URL
    }

    fun saveBaseUrl(context: Context, url: String) {
        var formattedUrl = url.trim()
        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
            formattedUrl = "http://$formattedUrl"
        }
        if (!formattedUrl.endsWith("/")) {
            formattedUrl = "$formattedUrl/"
        }
        getPrefs(context).edit().putString(KEY_BASE_URL, formattedUrl).apply()
    }
}

