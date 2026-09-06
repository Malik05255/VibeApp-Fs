package com.malik.lmai.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.data.repository.SettingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingRepository: SettingRepository
) : ViewModel() {

    private val _platforms =
        MutableStateFlow<List<PlatformV2>>(emptyList())

    val platforms: StateFlow<List<PlatformV2>> =
        _platforms.asStateFlow()

    init {
        loadPlatforms()
    }

    fun loadPlatforms() {
        viewModelScope.launch {
            try {
                _platforms.value =
                    settingRepository.fetchPlatformV2s()
            } catch (_: Exception) {
                _platforms.value = emptyList()
            }
        }
    }

    fun togglePlatformEnabled(
        platform: PlatformV2
    ) {
        viewModelScope.launch {
            try {
                val updated =
                    platform.copy(
                        enabled = !platform.enabled
                    )

                settingRepository.updatePlatformV2(
                    updated
                )

                loadPlatforms()
            } catch (_: Exception) {
                // Keep the current state if the update fails.
            }
        }
    }

    fun deletePlatform(
        platform: PlatformV2
    ) {
        viewModelScope.launch {
            try {
                settingRepository.deletePlatformV2(
                    platform
                )

                loadPlatforms()
            } catch (_: Exception) {
                // Keep the current state if deletion fails.
            }
        }
    }

    fun addPlatform(
        platform: PlatformV2
    ) {
        viewModelScope.launch {
            try {
                settingRepository.addPlatformV2(
                    platform
                )

                loadPlatforms()
            } catch (_: Exception) {
                // Keep the current state if adding fails.
            }
        }
    }
}
