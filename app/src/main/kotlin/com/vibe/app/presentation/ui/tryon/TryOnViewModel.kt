package com.vibe.app.presentation.ui.tryon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.data.model.ProductPreview
import com.vibe.app.data.repository.ProductPreviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TryOnViewModel @Inject constructor(
    private val productPreviewRepository: ProductPreviewRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TryOnUiState())
    val uiState: StateFlow<TryOnUiState> = _uiState.asStateFlow()

    fun onPersonImageSelected(uri: String) {
        _uiState.update {
            it.copy(
                personImageUri = uri,
                stage = TryOnStage.PRODUCT,
                prototypePrepared = false,
            )
        }
    }

    fun onGarmentImageSelected(uri: String) {
        _uiState.update {
            it.copy(
                garmentImageUri = uri,
                productError = null,
                stage = if (it.personImageUri != null) TryOnStage.REVIEW else TryOnStage.PERSON,
                prototypePrepared = false,
            )
        }
    }

    fun onProductUrlChanged(value: String) {
        _uiState.update {
            it.copy(
                productUrl = value,
                productError = null,
                prototypePrepared = false,
            )
        }
    }

    fun onMotionPresetSelected(preset: MotionPreset) {
        _uiState.update { it.copy(motionPreset = preset) }
    }

    fun loadProductPreview() {
        val url = _uiState.value.productUrl.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(productError = ProductLoadError.EMPTY_URL) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProduct = true, productError = null) }
            productPreviewRepository.load(url)
                .onSuccess(::applyProductPreview)
                .onFailure {
                    _uiState.update { current ->
                        current.copy(
                            isLoadingProduct = false,
                            productError = ProductLoadError.UNAVAILABLE,
                        )
                    }
                }
        }
    }

    fun preparePrototype() {
        if (!_uiState.value.canPrepare) return
        _uiState.update {
            it.copy(
                stage = TryOnStage.RESULT,
                prototypePrepared = true,
            )
        }
    }

    fun reset() {
        _uiState.value = TryOnUiState()
    }

    private fun applyProductPreview(preview: ProductPreview) {
        _uiState.update { current ->
            current.copy(
                productUrl = preview.sourceUrl,
                productTitle = preview.title,
                productImageUrl = preview.imageUrl,
                merchant = preview.merchant,
                isLoadingProduct = false,
                productError = if (preview.imageUrl == null) ProductLoadError.IMAGE_NOT_FOUND else null,
                stage = if (current.personImageUri != null && preview.imageUrl != null) {
                    TryOnStage.REVIEW
                } else {
                    current.stage
                },
                prototypePrepared = false,
            )
        }
    }
}

data class TryOnUiState(
    val personImageUri: String? = null,
    val garmentImageUri: String? = null,
    val productUrl: String = "",
    val productTitle: String = "",
    val productImageUrl: String? = null,
    val merchant: String = "",
    val isLoadingProduct: Boolean = false,
    val productError: ProductLoadError? = null,
    val stage: TryOnStage = TryOnStage.PERSON,
    val motionPreset: MotionPreset = MotionPreset.TURN,
    val prototypePrepared: Boolean = false,
) {
    val effectiveGarmentImage: String?
        get() = garmentImageUri ?: productImageUrl

    val canPrepare: Boolean
        get() = personImageUri != null && effectiveGarmentImage != null
}

enum class TryOnStage {
    PERSON,
    PRODUCT,
    REVIEW,
    RESULT,
}

enum class MotionPreset {
    TURN,
    WALK,
    DETAIL,
}

enum class ProductLoadError {
    EMPTY_URL,
    UNAVAILABLE,
    IMAGE_NOT_FOUND,
}
