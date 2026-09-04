package com.vibe.app.presentation.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.feature.update.UpdateManager
import com.vibe.app.feature.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val updateManager: UpdateManager,
) : ViewModel() {

    private val _isReady: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _updateState = MutableStateFlow(UpdateState())
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    init {
        checkForUpdate()
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _updateState.value = UpdateState(checking = true)
            runCatching { updateManager.checkForUpdate() }
                .onSuccess { manifest ->
                    _updateState.value = UpdateState(checking = false, available = manifest)
                    setAsReady()
                }
                .onFailure {
                    _updateState.value = UpdateState(checking = false, error = it.message)
                    setAsReady()
                }
        }
    }

    fun installRequiredUpdate() {
        val manifest = _updateState.value.available ?: return
        viewModelScope.launch {
            _updateState.value = _updateState.value.copy(downloading = true, progress = 0, error = null)
            var apk: File? = null
            runCatching {
                apk = updateManager.downloadAndVerify(manifest) { progress ->
                    _updateState.update { it.copy(progress = progress) }
                }
            }.onSuccess {
                _updateState.update { it.copy(downloading = false, progress = 100) }
                apk?.let(updateManager::openInstaller)
            }.onFailure {
                _updateState.update { state ->
                    state.copy(downloading = false, error = it.message ?: "تعذر تثبيت التحديث")
                }
            }
        }
    }

    private fun setAsReady() {
        _isReady.update { true }
    }
}
