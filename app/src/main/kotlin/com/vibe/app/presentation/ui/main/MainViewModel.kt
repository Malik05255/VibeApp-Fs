package com.vibe.app.presentation.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.R
import com.vibe.app.data.preferences.AppText
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

    private var lastUpdateCheckAt: Long = 0L
    private var dismissedVersionCode: Int? = null

    init {
        checkForUpdate(force = true)
    }

    fun checkForUpdate(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUpdateCheckAt < UPDATE_CHECK_INTERVAL_MS) {
            setAsReady()
            return
        }
        lastUpdateCheckAt = now

        viewModelScope.launch {
            _updateState.update { it.copy(checking = true, error = null) }
            runCatching { updateManager.checkForUpdate() }
                .onSuccess { manifest ->
                    val visibleManifest = manifest?.takeUnless {
                        dismissedVersionCode == it.versionCode
                    }
                    _updateState.update {
                        it.copy(
                            checking = false,
                            available = visibleManifest,
                            error = null,
                        )
                    }
                    setAsReady()
                }
                .onFailure { error ->
                    _updateState.update {
                        it.copy(checking = false, error = error.message)
                    }
                    setAsReady()
                }
        }
    }

    fun dismissOptionalUpdate() {
        val manifest = _updateState.value.available ?: return
        dismissedVersionCode = manifest.versionCode
        _updateState.update { it.copy(available = null, error = null) }
    }

    fun installUpdate() {
        val manifest = _updateState.value.available ?: return
        viewModelScope.launch {
            _updateState.value = _updateState.value.copy(
                downloading = true,
                progress = 0,
                error = null,
            )
            var apk: File? = null
            runCatching {
                apk = updateManager.downloadAndVerify(manifest) { progress ->
                    _updateState.update { it.copy(progress = progress) }
                }
            }.onSuccess {
                _updateState.update { it.copy(downloading = false, progress = 100) }
                apk?.let(updateManager::openInstaller)
            }.onFailure { error ->
                _updateState.update { state ->
                    state.copy(
                        downloading = false,
                        error = error.message ?: AppText.get(R.string.update_install_failed),
                    )
                }
            }
        }
    }

    fun installRequiredUpdate() = installUpdate()

    private fun setAsReady() {
        _isReady.value = true
    }

    companion object {
        private const val UPDATE_CHECK_INTERVAL_MS = 30 * 1000L
    }
}
