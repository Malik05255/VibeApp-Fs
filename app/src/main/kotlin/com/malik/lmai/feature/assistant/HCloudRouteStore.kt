package com.malik.lmai.feature.assistant

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists only public H routing metadata.
 *
 * No Supabase service role, runtime secret, Google token, provider key, or WhatsApp
 * credential is ever stored here. Once a standby is promoted, it remains pinned until
 * the owner explicitly disconnects/replaces that backup; there is no automatic failback.
 */
@Singleton
class HCloudRouteStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun rememberStandbyProjectEndpoint(rawEndpoint: String?): Boolean {
        val normalized = normalizeProjectEndpoint(rawEndpoint) ?: return false
        preferences.edit().putString(KEY_STANDBY_PROJECT_ENDPOINT, normalized).apply()
        return true
    }

    fun standbyProjectEndpoint(): String? = normalizeProjectEndpoint(
        preferences.getString(KEY_STANDBY_PROJECT_ENDPOINT, null),
    )

    fun standbyFunctionBase(): String? = standbyProjectEndpoint()?.let { "$it/functions/v1" }

    fun pinnedStandbyFunctionBase(): String? = if (preferences.getBoolean(KEY_STANDBY_PINNED, false)) {
        standbyFunctionBase()
    } else {
        null
    }

    @Synchronized
    fun pinStandby(rawEndpoint: String): Boolean {
        val normalized = normalizeProjectEndpoint(rawEndpoint) ?: return false
        preferences.edit()
            .putString(KEY_STANDBY_PROJECT_ENDPOINT, normalized)
            .putBoolean(KEY_STANDBY_PINNED, true)
            .apply()
        return true
    }

    fun isStandbyPinned(): Boolean =
        preferences.getBoolean(KEY_STANDBY_PINNED, false) && standbyProjectEndpoint() != null

    @Synchronized
    fun clearStandby() {
        preferences.edit()
            .remove(KEY_STANDBY_PROJECT_ENDPOINT)
            .remove(KEY_STANDBY_PINNED)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "h_cloud_route_v1"
        private const val KEY_STANDBY_PROJECT_ENDPOINT = "standby_project_endpoint"
        private const val KEY_STANDBY_PINNED = "standby_pinned_request_only"

        internal fun normalizeProjectEndpoint(rawEndpoint: String?): String? {
            val text = rawEndpoint?.trim().orEmpty()
            if (text.isEmpty()) return null
            return runCatching {
                val uri = URI(text)
                val host = uri.host?.lowercase().orEmpty()
                if (
                    uri.scheme != "https" ||
                    !host.endsWith(".supabase.co") ||
                    host.length <= ".supabase.co".length ||
                    uri.userInfo != null ||
                    uri.query != null ||
                    uri.fragment != null ||
                    (uri.path?.let { it.isNotEmpty() && it != "/" } == true)
                ) {
                    return null
                }
                "https://$host"
            }.getOrNull()
        }
    }
}