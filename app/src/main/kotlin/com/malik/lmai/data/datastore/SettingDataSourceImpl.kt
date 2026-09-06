package com.malik.lmai.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.malik.lmai.data.model.DynamicTheme
import com.malik.lmai.data.model.ThemeMode
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SettingDataSourceImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingDataSource {

    private val dynamicThemeKey = intPreferencesKey("dynamic_mode")
    private val themeModeKey = intPreferencesKey("theme_mode")
    private val debugModeKey = booleanPreferencesKey("debug_mode")
    private val apiProviderKey = stringPreferencesKey("api_provider")
    private val apiKeyKey = stringPreferencesKey("api_key")
    private val customApiUrlKey = stringPreferencesKey("custom_api_url")
    private val freeAiEnabledKey = booleanPreferencesKey("free_ai_enabled")
    private val aiExecutionModeKey = stringPreferencesKey("ai_execution_mode")

    override suspend fun updateDynamicTheme(theme: DynamicTheme) {
        dataStore.edit { pref -> pref[dynamicThemeKey] = theme.ordinal }
    }

    override suspend fun updateThemeMode(themeMode: ThemeMode) {
        dataStore.edit { pref -> pref[themeModeKey] = themeMode.ordinal }
    }

    override suspend fun getDynamicTheme(): DynamicTheme? {
        val mode = dataStore.data.map { pref -> pref[dynamicThemeKey] }.first() ?: return null
        return DynamicTheme.getByValue(mode)
    }

    override suspend fun getThemeMode(): ThemeMode? {
        val mode = dataStore.data.map { pref -> pref[themeModeKey] }.first() ?: return null
        return ThemeMode.getByValue(mode)
    }

    override suspend fun updateDebugMode(enabled: Boolean) {
        dataStore.edit { pref -> pref[debugModeKey] = enabled }
    }

    override suspend fun getDebugMode(): Boolean =
        dataStore.data.map { pref -> pref[debugModeKey] }.first() ?: false

    override suspend fun updateApiProvider(provider: String) {
        dataStore.edit { pref -> pref[apiProviderKey] = provider }
    }

    override suspend fun getApiProvider(): String =
        dataStore.data.map { pref -> pref[apiProviderKey] }.first() ?: "OPEN_ROUTER"

    override suspend fun updateApiKey(apiKey: String) {
        dataStore.edit { pref -> pref[apiKeyKey] = apiKey }
    }

    override suspend fun getApiKey(): String =
        dataStore.data.map { pref -> pref[apiKeyKey] }.first() ?: ""

    override suspend fun updateCustomApiUrl(url: String) {
        dataStore.edit { pref -> pref[customApiUrlKey] = url }
    }

    override suspend fun getCustomApiUrl(): String =
        dataStore.data.map { pref -> pref[customApiUrlKey] }.first() ?: ""

    override suspend fun updateFreeAiEnabled(enabled: Boolean) {
        dataStore.edit { pref -> pref[freeAiEnabledKey] = enabled }
    }

    override suspend fun getFreeAiEnabled(): Boolean =
        dataStore.data.map { pref -> pref[freeAiEnabledKey] }.first() ?: true

    override suspend fun updateAiExecutionMode(mode: String) {
        dataStore.edit { pref -> pref[aiExecutionModeKey] = mode }
    }

    override suspend fun getAiExecutionMode(): String =
        dataStore.data.map { pref -> pref[aiExecutionModeKey] }.first() ?: "MANUAL"
}
