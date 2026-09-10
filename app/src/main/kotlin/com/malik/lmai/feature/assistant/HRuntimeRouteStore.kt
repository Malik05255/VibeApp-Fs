package com.malik.lmai.feature.assistant

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores only public routing metadata. Supabase service-role keys and H runtime secrets
 * never enter this store or the APK.
 */
@Singleton
class HRuntimeRouteStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun standbyBaseEndpoint(): String? =
        HRuntimeRoutingPolicy.normalizeStandbyBaseEndpoint(preferences.getString(KEY_STANDBY_BASE, null))

    fun updateStandbyBaseEndpoint(raw: String?): Boolean {
        val normalized = HRuntimeRoutingPolicy.normalizeStandbyBaseEndpoint(raw) ?: return false
        val previous = standbyBaseEndpoint()
        if (previous == normalized) return true
        preferences.edit()
            .putString(KEY_STANDBY_BASE, normalized)
            .putBoolean(KEY_STANDBY_ACTIVE_CONFIRMED, false)
            .apply()
        return true
    }

    fun clearStandby() {
        preferences.edit()
            .remove(KEY_STANDBY_BASE)
            .remove(KEY_STANDBY_ACTIVE_CONFIRMED)
            .apply()
    }

    fun standbyActiveConfirmed(baseEndpoint: String): Boolean {
        val normalized = HRuntimeRoutingPolicy.normalizeStandbyBaseEndpoint(baseEndpoint) ?: return false
        return normalized == standbyBaseEndpoint() &&
            preferences.getBoolean(KEY_STANDBY_ACTIVE_CONFIRMED, false)
    }

    fun confirmStandbyActive(baseEndpoint: String) {
        val normalized = HRuntimeRoutingPolicy.normalizeStandbyBaseEndpoint(baseEndpoint) ?: return
        if (normalized != standbyBaseEndpoint()) return
        preferences.edit().putBoolean(KEY_STANDBY_ACTIVE_CONFIRMED, true).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "h_public_runtime_route_v1"
        private const val KEY_STANDBY_BASE = "standby_base_endpoint"
        private const val KEY_STANDBY_ACTIVE_CONFIRMED = "standby_request_only_active_confirmed"
    }
}
