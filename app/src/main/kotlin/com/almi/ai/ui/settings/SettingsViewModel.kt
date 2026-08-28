package com.almi.ai.ui.settings

import androidx.lifecycle.ViewModel
import com.almi.ai.data.preferences.AlmiPreferences
import com.almi.ai.data.preferences.AppThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AlmiPreferences,
) : ViewModel() {
    val language: StateFlow<String> = preferences.language
    val themeMode: StateFlow<AppThemeMode> = preferences.themeMode
    val apiKey: StateFlow<String> = preferences.apiKey

    fun setLanguage(language: String) = preferences.setLanguage(language)
    fun setThemeMode(mode: AppThemeMode) = preferences.setThemeMode(mode)
    fun saveApiKey(value: String) = preferences.setApiKey(value)
    fun clearApiKey() = preferences.clearApiKey()
}
