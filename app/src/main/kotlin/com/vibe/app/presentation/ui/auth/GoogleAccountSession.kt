package com.vibe.app.presentation.ui.auth

import android.content.Context

object GoogleAccountSession {
    private const val PREFS_NAME = "google_account_session"
    private const val KEY_EMAIL = "email"

    fun getEmail(context: Context): String? {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_EMAIL, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun save(context: Context, email: String) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EMAIL, email.trim())
            .apply()
    }

    fun clear(context: Context) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
