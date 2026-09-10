package com.malik.lmai.feature.assistant

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores only public routing metadata for H's validated standby runtime.
 *
 * No runtime secret, Supabase service credential, Google token, provider key, or WhatsApp
 * credential is persisted here. Once a standby becomes request-active, routing is sticky
 * until the owner disconnects/replaces that standby; automatic failback is intentionally
 * forbidden because it could split durable H state across two clouds.
 */
@Singleton
class HRuntimeRouteStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun rememberStandbyEndpoint(rawEndpoint: String): Boolean {
        val normalized = normalizeSupabaseBaseUrl(rawEndpoint) ?: return false
        val previous = prefs.getString(KEY_STANDBY_BASE_URL, null)
        val editor = prefs.edit().putString(KEY_STANDBY_BASE_URL, normalized)
        if (previous != normalized) editor.putBoolean(KEY_STANDBY_STICKY_ACTIVE, false)
        editor.apply()
        return true
    }

    fun standbyBaseUrl(): String? = prefs.getString(KEY_STANDBY_BASE_URL, null)
        ?.let(::normalizeSupabaseBaseUrl)

    fun isStandbyStickyActive(): Boolean =
        standbyBaseUrl() != null && prefs.getBoolean(KEY_STANDBY_STICKY_ACTIVE, false)

    fun markStandbyActive() {
        if (standbyBaseUrl() != null) {
            prefs.edit().putBoolean(KEY_STANDBY_STICKY_ACTIVE, true).apply()
        }
    }

    fun clearStandby() {
        prefs.edit()
            .remove(KEY_STANDBY_BASE_URL)
            .remove(KEY_STANDBY_STICKY_ACTIVE)
            .apply()
    }

    fun standbyFunctionUrl(primaryFunctionUrl: String): String? {
        val base = standbyBaseUrl() ?: return null
        val functionName = runCatching {
            val uri = URI(primaryFunctionUrl)
            val path = uri.path.orEmpty()
            if (!path.startsWith(FUNCTION_PATH_PREFIX)) return@runCatching null
            path.removePrefix(FUNCTION_PATH_PREFIX)
                .takeIf { it.matches(FUNCTION_NAME_PATTERN) }
        }.getOrNull() ?: return null
        return "$base$FUNCTION_PATH_PREFIX$functionName"
    }

    fun standbyRouteUrl(): String? = standbyBaseUrl()?.let { "$it$FUNCTION_PATH_PREFIX$STANDBY_ROUTE_FUNCTION" }

    companion object {
        private const val PREFS_NAME = "h_runtime_route_v1"
        private const val KEY_STANDBY_BASE_URL = "standby_base_url"
        private const val KEY_STANDBY_STICKY_ACTIVE = "standby_sticky_active"
        private const val FUNCTION_PATH_PREFIX = "/functions/v1/"
        private const val STANDBY_ROUTE_FUNCTION = "h-standby-app-route"
        private val FUNCTION_NAME_PATTERN = Regex("^[a-z0-9-]{1,80}$")

        internal fun normalizeSupabaseBaseUrl(raw: String): String? = runCatching {
            val uri = URI(raw.trim())
            val host = uri.host?.lowercase().orEmpty()
            if (uri.scheme != "https") return@runCatching null
            if (!host.endsWith(".supabase.co")) return@runCatching null
            if (!uri.userInfo.isNullOrEmpty() || !uri.query.isNullOrEmpty() || !uri.fragment.isNullOrEmpty()) {
                return@runCatching null
            }
            if (uri.path.orEmpty().let { it.isNotEmpty() && it != "/" }) return@runCatching null
            "https://$host"
        }.getOrNull()
    }
}
