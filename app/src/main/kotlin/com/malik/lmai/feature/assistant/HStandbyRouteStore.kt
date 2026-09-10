package com.malik.lmai.feature.assistant

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/** Stores only the public Supabase project base URL for the owner's configured H standby. */
@Singleton
class HStandbyRouteStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun remember(endpoint: String?) {
        val normalized = normalizeEndpoint(endpoint) ?: return
        preferences.edit().putString(KEY_ENDPOINT, normalized).apply()
    }

    fun endpoint(): String? = normalizeEndpoint(preferences.getString(KEY_ENDPOINT, null))

    fun clear() {
        preferences.edit().remove(KEY_ENDPOINT).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "h_standby_route_v1"
        private const val KEY_ENDPOINT = "supabase_endpoint"

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
    }
}
