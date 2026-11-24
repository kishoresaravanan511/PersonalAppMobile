package com.example.taskschedulerapp.data

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("beakai_session", Context.MODE_PRIVATE)

    fun saveUserEmail(email: String) {
        prefs.edit().putString("user_email", email).apply()
    }

    fun getUserEmail(): String? {
        return prefs.getString("user_email", null)
    }

    fun logout() {
        prefs.edit().remove("user_email").apply()
    }

    fun saveTheme(isDark: Boolean) {
        prefs.edit().putBoolean("is_dark_theme", isDark).apply()
    }

    fun isDarkTheme(): Boolean {
        return prefs.getBoolean("is_dark_theme", false)
    }
}
