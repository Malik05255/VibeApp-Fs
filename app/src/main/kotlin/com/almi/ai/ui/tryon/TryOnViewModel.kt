package com.almi.ai.ui.tryon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almi.ai.data.model.ProductPreview
import com.almi.ai.data.repository.MotionDirection
import com.almi.ai.data.repository.ProductPreviewRepository
import com.almi.ai.data.repository.TryOnGenerationRepository
import com.almi.ai.data.repository.VideoGenerationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TryOnViewModel @Inject constructor(
    private val productRepository: ProductPreviewRepository,
    private val generationRepository: TryOnGenerationRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TryOnUiState())
    val uiState: StateFlow<TryOnUiState> = _uiState.asStateFlow()

    fun setPersonImage(uri: String) = updateInputs { it.copy(personImage = uri) }
    fun setGarmentImage(uri: String) = updateInputs {
        it.copy(garmentImage = uri, productImage = null, productError = ProductError.NONE)
    }

    fun setProductUrl(value: String) {
        _uiState.update { it.copy(productUrl = value, productError = ProductError.NONE) }
    }

    fun setMotion(direction: MotionDirection) {
        _uiState.update { it.copy(motion = direction, generatedVideo = null, videoError = false) }
    }

    fun loadProduct() {
        val url = _uiState.value.productUrl.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(productError = ProductError.EMPTY_URL) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProduct = true, productError = ProductError.NONE) }
            productRepository.load(url)
                .onSuccess(::applyProduct)
                .onFailure {
                    _uiState.update {
                        it.copy(isLoadingProduct = false, productError = ProductError.UNAVAILABLE)
                    }
                }
        }
    }

    fun generateImage() {
        val state = _uiState.value
        val person = state.personImage ?: return
        val garment = state.effectiveGarmentImage ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGeneratingImage = true,
                    imageError = GenerationError.NONE,
                    generatedImage = null,
                    generatedVideo = null,
                    videoError = false,
                )
            }
            generationRepository.generateImage(
                personImage = person,
                garmentImage = garment,
                garmentDescription = state.productTitle,
            ).onSuccess { result ->
                _uiState.update {
                    it.copy(isGeneratingImage = false, generatedImage = result.uri)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isGeneratingImage = false,
                        imageError = classifyGenerationError(error),
                    )
                }
            }
        }
    }

    fun generateVideo() {
        val image = _uiState.value.generatedImage ?: return
        val motion = _uiState.value.motion
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGeneratingVideo = true,
                    videoError = false,
                    videoStatus = VideoGenerationStatus.SUBMITTING,
                    generatedVideo = null,
                )
            }
            generationRepository.generateVideo(image, motion) { status ->
                _uiState.update { it.copy(videoStatus = status) }
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isGeneratingVideo = false,
                        videoStatus = VideoGenerationStatus.IDLE,
                        generatedVideo = result.uri,
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isGeneratingVideo = false,
                        videoStatus = VideoGenerationStatus.IDLE,
                        videoError = true,
                    )
                }
            }
        }
    }

    fun reset() {
        _uiState.value = TryOnUiState()
    }

    private fun applyProduct(preview: ProductPreview) {
        _uiState.update {
            it.copy(
                isLoadingProduct = false,
                productUrl = preview.sourceUrl,
                productTitle = preview.title,
                merchant = preview.merchant,
                productImage = preview.imageUrl,
                garmentImage = null,
                productError = if (preview.imageUrl == null) ProductError.IMAGE_NOT_FOUND else ProductError.NONE,
                generatedImage = null,
                generatedVideo = null,
            )
        }
    }

    private fun updateInputs(update: (TryOnUiState) -> TryOnUiState) {
        _uiState.update {
            update(it).copy(
                generatedImage = null,
                generatedVideo = null,
                imageError = GenerationError.NONE,
                videoError = false,
            )
        }
    }

    private fun classifyGenerationError(error: Throwable): GenerationError {
        val message = generateSequence(error) { it.cause }
            .joinToString(" ") { it.message.orEmpty().lowercase() }
        return when {
            message.contains("free_image_unavailable") -> GenerationError.FREE_IMAGE_UNAVAILABLE
            message.contains("free_api_key_missing") -> GenerationError.FREE_KEY_MISSING
            message.contains("custom_config_missing") -> GenerationError.CONFIG_MISSING
            else -> GenerationError.REQUEST_FAILED
        }
    }
}

data class TryOnUiState(
    val personImage: String? = null,
    val garmentImage: String? = null,
    val productUrl: String = "",
    val productTitle: String = "",
    val productImage: String? = null,
    val merchant: String = "",
    val isLoadingProduct: Boolean = false,
    val productError: ProductError = ProductError.NONE,
    val motion: MotionDirection = MotionDirection.TURN,
    val isGeneratingImage: Boolean = false,
    val generatedImage: String? = null,
    val imageError: GenerationError = GenerationError.NONE,
    val isGeneratingVideo: Boolean = false,
    val videoStatus: VideoGenerationStatus = VideoGenerationStatus.IDLE,
    val generatedVideo: String? = null,
    val videoError: Boolean = false,
) {
    val effectiveGarmentImage: String?
        get() = garmentImage ?: productImage

    val canGenerate: Boolean
        get() = personImage != null && effectiveGarmentImage != null && !isGeneratingImage
}

enum class ProductError {
    NONE,
    EMPTY_URL,
    UNAVAILABLE,
    IMAGE_NOT_FOUND,
}

enum class GenerationError {
    NONE,
    CONFIG_MISSING,
    FREE_KEY_MISSING,
    FREE_IMAGE_UNAVAILABLE,
    REQUEST_FAILED,
}
