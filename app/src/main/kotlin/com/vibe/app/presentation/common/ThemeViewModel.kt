package com.vibe.app.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.vibe.app.data.dto.ThemeSetting
import com.vibe.app.data.model.DynamicTheme
import com.vibe.app.data.model.ThemeMode
import com.vibe.app.data.repository.SettingRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ThemeViewModel @Inject constructor(private val settingRepository: SettingRepository) : ViewModel() {

    private val _themeSetting = MutableStateFlow(ThemeSetting())
    val themeSetting = _themeSetting.asStateFlow()

    init {
        fetchThemes()
    }

    private fun fetchThemes() {
        viewModelScope.launch {
            val saved = runCatching {
                settingRepository.fetchThemes()
            }.getOrElse {
                ThemeSetting(
                    dynamicTheme = DynamicTheme.OFF,
                    themeMode = ThemeMode.LIGHT,
                )
            }

            val normalized = saved.copy(
                dynamicTheme = DynamicTheme.OFF,
                themeMode = if (saved.themeMode == ThemeMode.SYSTEM) ThemeMode.LIGHT else saved.themeMode,
            )

            _themeSetting.update { normalized }

            if (normalized != saved) {
                runCatching {
                    settingRepository.updateThemes(normalized)
                }
            }
        }
    }

    fun updateDynamicTheme(theme: DynamicTheme) {
        val normalized = DynamicTheme.OFF
        _themeSetting.update { setting ->
            setting.copy(dynamicTheme = normalized)
        }
        viewModelScope.launch {
            runCatching {
                settingRepository.updateThemes(_themeSetting.value)
            }
        }
    }

    fun updateThemeMode(theme: ThemeMode) {
        val normalized = if (theme == ThemeMode.SYSTEM) ThemeMode.LIGHT else theme
        _themeSetting.update { setting ->
            setting.copy(themeMode = normalized)
        }
        viewModelScope.launch {
            runCatching {
                settingRepository.updateThemes(_themeSetting.value)
            }
        }
    }
}
