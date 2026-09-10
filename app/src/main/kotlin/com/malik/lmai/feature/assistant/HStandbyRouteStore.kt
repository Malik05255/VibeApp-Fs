package com.malik.lmai.feature.assistant

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/** Stores public standby routing metadata only; no runtime/provider credentials. */
@Singleton
class HStandbyRouteStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun remember(endpoint: String?, failoverControlUrl: String? = null) {
        val normalized = normalizeEndpoint(endpoint) ?: return
        val normalizedControl = normalizeFailoverControlUrl(failoverControlUrl)
        val current = this.endpoint()
        val editor = preferences.edit().putString(KEY_ENDPOINT, normalized)
        // A new/different standby endpoint cannot inherit an old promotion latch.
        if (current != normalized) editor.remove(KEY_REQUEST_ACTIVE_LATCHED)
        if (normalizedControl != null) {
            editor.putString(KEY_FAILOVER_CONTROL_URL, normalizedControl)
        } else {
            editor.remove(KEY_FAILOVER_CONTROL_URL)
        }
        editor.apply()
    }

    fun endpoint(): String? = normalizeEndpoint(preferences.getString(KEY_ENDPOINT, null))

    fun failoverControlUrl(): String? =
        normalizeFailoverControlUrl(preferences.getString(KEY_FAILOVER_CONTROL_URL, null))

    fun markRequestActive() {
        if (endpoint() != null) preferences.edit().putBoolean(KEY_REQUEST_ACTIVE_LATCHED, true).apply()
    }

    fun requestActiveLatched(): Boolean = endpoint() != null &&
        preferences.getBoolean(KEY_REQUEST_ACTIVE_LATCHED, false)

    fun clear() {
        preferences.edit()
            .remove(KEY_ENDPOINT)
            .remove(KEY_FAILOVER_CONTROL_URL)
            .remove(KEY_REQUEST_ACTIVE_LATCHED)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "h_standby_route_v1"
        private const val KEY_ENDPOINT = "supabase_endpoint"
        private const val KEY_FAILOVER_CONTROL_URL = "failover_control_url"
        private const val KEY_REQUEST_ACTIVE_LATCHED = "request_active_latched"
        private const val FAILOVER_CONTROL_PATH = "/h-app-failover-route"

        internal fun normalizeEndpoint(raw: String?): String? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            return runCatching {
                val uri = URI(text)
                if (uri.scheme != "https") return@runCatching null
                val host = uri.host?.lowercase() ?: return@runCatching null
                if (!host.endsWith(".supabase.co") || host.length <= ".supabase.co".length) return@runCatching null
                if (uri.userInfo != null || uri.query != null || uri.fragment != null) return@runCatching null
                if (uri.port !in listOf(-1, 443)) return@runCatching null
                if (!uri.path.isNullOrEmpty() && uri.path != "/") return@runCatching null
                "https://$host"
            }.getOrNull()
        }

        internal fun normalizeFailoverControlUrl(raw: String?): String? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            return runCatching {
                val uri = URI(text)
                if (uri.scheme != "https") return@runCatching null
                val host = uri.host?.lowercase() ?: return@runCatching null
                if (!host.endsWith(".workers.dev") || host.length <= ".workers.dev".length) return@runCatching null
                if (uri.userInfo != null || uri.query != null || uri.fragment != null) return@runCatching null
                if (uri.port !in listOf(-1, 443)) return@runCatching null
                if (uri.path != FAILOVER_CONTROL_PATH) return@runCatching null
                "https://$host$FAILOVER_CONTROL_PATH"
            }.getOrNull()
        }
    }
}
