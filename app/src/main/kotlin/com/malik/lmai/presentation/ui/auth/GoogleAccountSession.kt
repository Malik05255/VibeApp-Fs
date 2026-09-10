package com.malik.lmai.presentation.ui.auth

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
    private const val KEY_H_CONTINUITY_HANDLE = "h_continuity_handle"
    const val LOCAL_OWNER_KEY = "local"

    private val H_CONTINUITY_HANDLE_PATTERN = Regex("^h1_[0-9a-f]{64}$")

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

    /**
     * Returns H's local owner scope.
     *
     * Once H Cloud proves that this Google session maps to an existing H, the opaque
     * continuity handle becomes the durable local anchor. This avoids coupling H's identity
     * to a mutable email address. The email hash remains only a bootstrap fallback before
     * the account has been linked to H Cloud.
     */
    fun currentOwnerKey(context: Context): String {
        val continuityHandle = getHContinuityHandle(context)
        if (continuityHandle != null) return "h-continuity:$continuityHandle"

        val email = getEmail(context)?.trim()?.lowercase()
        return if (!email.isNullOrBlank()) "google:${sha256(email)}" else LOCAL_OWNER_KEY
    }

    fun getHContinuityHandle(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_H_CONTINUITY_HANDLE, null)
            ?.trim()
            ?.takeIf(H_CONTINUITY_HANDLE_PATTERN::matches)

    fun saveHContinuityHandle(context: Context, handle: String) {
        val normalized = handle.trim()
        require(H_CONTINUITY_HANDLE_PATTERN.matches(normalized)) {
            "Invalid H continuity handle"
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_H_CONTINUITY_HANDLE, normalized)
            .apply()
    }

    fun clearHContinuityHandle(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_H_CONTINUITY_HANDLE)
            .apply()
    }

    fun isLocalMode(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOCAL_MODE, false)
    }

    fun save(context: Context, account: GoogleAccount) {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previousEmail = preferences.getString(KEY_EMAIL, null)?.trim()?.lowercase()
        val nextEmail = account.email.trim().lowercase()
        val editor = preferences.edit()
            .putString(KEY_EMAIL, account.email.trim())
            .putString(KEY_DISPLAY_NAME, account.displayName)
            .putString(KEY_PROFILE_PICTURE, account.profilePictureUrl)
            .putString(KEY_ID_TOKEN, account.idToken)
            .putBoolean(KEY_LOCAL_MODE, false)

        // Never carry a cloud H identity from one Google account into another account.
        // A same-account refresh keeps the existing handle available while the cloud proof
        // is refreshed; an account switch clears it before any H subsystem can reuse it.
        if (previousEmail != null && previousEmail != nextEmail) {
            editor.remove(KEY_H_CONTINUITY_HANDLE)
        }
        editor.apply()
    }

    fun enableLocalMode(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_EMAIL)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_PROFILE_PICTURE)
            .remove(KEY_ID_TOKEN)
            .remove(KEY_H_CONTINUITY_HANDLE)
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
