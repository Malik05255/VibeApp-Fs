package com.vibe.app.presentation.ui.auth

import android.content.Context
import java.security.MessageDigest

data class GoogleAccount(
    val email: String,
    val displayName: String? = null,
    val profilePictureUrl: String? = null,
    val idToken: String? = null,
)

object GoogleAccountSession {
    private const val PREFS_NAME = "google_account_session"
    private const val KEY_EMAIL = "email"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_PROFILE_PICTURE = "profile_picture"
    private const val KEY_ID_TOKEN = "id_token"
    private const val KEY_LOCAL_MODE = "local_mode"
    const val LOCAL_OWNER_KEY = "local"

    fun get(context: Context): GoogleAccount? {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val email = preferences.getString(KEY_EMAIL, null)?.takeIf { it.isNotBlank() } ?: return null
        return GoogleAccount(
            email = email,
            displayName = preferences.getString(KEY_DISPLAY_NAME, null),
            profilePictureUrl = preferences.getString(KEY_PROFILE_PICTURE, null),
            idToken = preferences.getString(KEY_ID_TOKEN, null),
        )
    }

    fun getEmail(context: Context): String? = get(context)?.email

    fun currentOwnerKey(context: Context): String {
        val email = getEmail(context)?.trim()?.lowercase()
        return if (!email.isNullOrBlank()) "google:${sha256(email)}" else LOCAL_OWNER_KEY
    }

    fun isLocalMode(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOCAL_MODE, false)
    }

    fun save(context: Context, account: GoogleAccount) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_EMAIL, account.email.trim())
            .putString(KEY_DISPLAY_NAME, account.displayName)
            .putString(KEY_PROFILE_PICTURE, account.profilePictureUrl)
            .putString(KEY_ID_TOKEN, account.idToken)
            .putBoolean(KEY_LOCAL_MODE, false)
            .apply()
    }

    fun enableLocalMode(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_EMAIL)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_PROFILE_PICTURE)
            .remove(KEY_ID_TOKEN)
            .putBoolean(KEY_LOCAL_MODE, true)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
