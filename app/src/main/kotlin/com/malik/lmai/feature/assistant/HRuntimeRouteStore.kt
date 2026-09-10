package com.malik.lmai.feature.assistant

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores only a public Supabase project endpoint learned from an owner-authenticated H
 * preflight. No service role, runtime secret, Google token, provider key, or user identity
 * is persisted here.
 */
@Singleton
class HRuntimeRouteStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun rememberBackupEndpoint(rawEndpoint: String?) {
        val endpoint = HRuntimeRoutePolicy.normalizeSupabaseBaseUrl(rawEndpoint)
        if (endpoint == null) {
            clearBackupEndpoint()
            return
        }
        preferences.edit()
            .putString(KEY_BACKUP_ENDPOINT, endpoint)
            .putLong(KEY_BACKUP_OBSERVED_AT_MS, System.currentTimeMillis())
            .apply()
    }

    @Synchronized
    fun clearBackupEndpoint() {
        preferences.edit()
            .remove(KEY_BACKUP_ENDPOINT)
            .remove(KEY_BACKUP_OBSERVED_AT_MS)
            .apply()
    }

    fun backupEndpoint(nowMs: Long = System.currentTimeMillis()): String? {
        val observedAt = preferences.getLong(KEY_BACKUP_OBSERVED_AT_MS, 0L)
        if (observedAt <= 0L || nowMs < observedAt || nowMs - observedAt > ROUTE_CACHE_TTL_MS) return null
        return HRuntimeRoutePolicy.normalizeSupabaseBaseUrl(preferences.getString(KEY_BACKUP_ENDPOINT, null))
    }

    companion object {
        private const val PREFERENCES_NAME = "h_runtime_route_v1"
        private const val KEY_BACKUP_ENDPOINT = "backup_endpoint"
        private const val KEY_BACKUP_OBSERVED_AT_MS = "backup_observed_at_ms"
        private const val ROUTE_CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L
    }
}

object HRuntimeRoutePolicy {
    private val SUPABASE_HOST = Regex("^[a-z0-9-]{8,64}\\.supabase\\.co$", RegexOption.IGNORE_CASE)

    fun normalizeSupabaseBaseUrl(rawEndpoint: String?): String? {
        val raw = rawEndpoint?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching {
            val uri = URI(raw)
            val host = uri.host?.lowercase() ?: return@runCatching null
            if (uri.scheme?.lowercase() != "https") return@runCatching null
            if (!SUPABASE_HOST.matches(host)) return@runCatching null
            if (uri.userInfo != null || uri.query != null || uri.fragment != null) return@runCatching null
            if (uri.port !in listOf(-1, 443)) return@runCatching null
            if (!uri.path.isNullOrEmpty() && uri.path != "/") return@runCatching null
            "https://$host"
        }.getOrNull()
    }

    fun primaryPreflightCanFallBack(statusCode: Int): Boolean = statusCode == 0 || statusCode >= 500

    fun functionUrl(baseUrl: String, functionName: String): String? {
        val base = normalizeSupabaseBaseUrl(baseUrl) ?: return null
        val safeName = functionName.trim()
        if (!safeName.matches(Regex("^[a-z0-9][a-z0-9-]{1,80}$"))) return null
        return "$base/functions/v1/$safeName"
    }
}
