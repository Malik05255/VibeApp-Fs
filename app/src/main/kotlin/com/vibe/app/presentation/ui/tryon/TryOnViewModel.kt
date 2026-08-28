package com.vibe.app.presentation.ui.tryon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.data.model.ProductPreview
import com.vibe.app.data.model.SavedTryOnDraft
import com.vibe.app.data.model.SavedTryOnGarment
import com.vibe.app.data.model.SavedTryOnHistory
import com.vibe.app.data.repository.ProductPreviewRepository
import com.vibe.app.data.repository.TryOnGenerationRepository
import com.vibe.app.data.repository.TryOnLocalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TryOnViewModel @Inject constructor(
    private val productPreviewRepository: ProductPreviewRepository,
    private val localRepository: TryOnLocalRepository,
    private val generationRepository: TryOnGenerationRepository,
) : ViewModel() {

    private val loadedDraft = localRepository.loadDraft()
    private val restoredDraftGarments = loadedDraft?.garmentImages.orEmpty().mapIndexed { index, image ->
        OutfitGarment(
            id = UUID.randomUUID().toString(),
            image = image,
            title = loadedDraft?.garmentTitles?.getOrNull(index).orEmpty(),
            sourceUrl = "",
            merchant = "",
            category = loadedDraft?.garmentCategories?.getOrNull(index).toGarmentCategory(),
        )
    }

    private val _uiState = MutableStateFlow(
        TryOnUiState(
            personImageUri = loadedDraft?.personImage,
            outfitGarments = restoredDraftGarments,
            motionPreset = loadedDraft?.motion.toMotionPreset(),
            stage = when {
                loadedDraft?.personImage != null && restoredDraftGarments.isNotEmpty() -> TryOnStage.REVIEW
                loadedDraft?.personImage != null -> TryOnStage.PRODUCT
                else -> TryOnStage.PERSON
            },
            wardrobe = localRepository.loadWardrobe(),
            history = localRepository.loadHistory(),
        )
    )
    val uiState: StateFlow<TryOnUiState> = _uiState.asStateFlow()

    fun onPersonImageSelected(uri: String) {
        _uiState.update {
            it.copy(
                personImageUri = uri,
                stage = TryOnStage.PRODUCT,
            ).clearGeneratedMedia()
        }
        persistDraft()
    }

    fun onGarmentImageSelected(uri: String) {
        _uiState.update {
            it.copy(
                garmentImageUri = uri,
                productImageUrl = null,
                productError = null,
                stage = if (it.personImageUri != null) TryOnStage.REVIEW else TryOnStage.PERSON,
            ).clearGeneratedMedia()
        }
        persistDraft()
    }

    fun onProductUrlChanged(value: String) {
        _uiState.update {
            it.copy(
                productUrl = value,
                productError = null,
            ).clearGeneratedMedia()
        }
    }

    fun onCategorySelected(category: GarmentCategory) {
        _uiState.update { it.copy(selectedCategory = category).clearGeneratedMedia() }
        persistDraft()
    }

    fun onMotionPresetSelected(preset: MotionPreset) {
        _uiState.update {
            it.copy(
                motionPreset = preset,
                generatedVideoUri = null,
                generatedVideoModel = null,
                generatedVideoCostUsd = null,
                videoGenerationError = null,
                videoGenerationStatus = MediaGenerationStatus.IDLE,
            )
        }
        persistDraft()
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

    fun addCurrentGarmentToOutfit() {
        val state = _uiState.value
        val garment = state.candidateGarment ?: return
        val updated = state.outfitGarments
            .filterNot { it.category == garment.category }
            .plus(garment)
            .sortedBy { it.category.ordinal }

        _uiState.update {
            it.copy(
                outfitGarments = updated,
                stage = if (it.personImageUri != null) TryOnStage.REVIEW else it.stage,
            ).clearGeneratedMedia()
        }
        persistDraft()
    }

    fun removeOutfitGarment(id: String) {
        _uiState.update {
            it.copy(
                outfitGarments = it.outfitGarments.filterNot { garment -> garment.id == id },
            ).clearGeneratedMedia()
        }
        persistDraft()
    }

    fun saveCurrentGarmentToWardrobe() {
        val state = _uiState.value
        val garment = state.candidateGarment ?: return
        val saved = SavedTryOnGarment(
            id = UUID.randomUUID().toString(),
            image = garment.image,
            title = garment.title,
            sourceUrl = garment.sourceUrl,
            merchant = garment.merchant,
            category = garment.category.name,
            savedAt = System.currentTimeMillis(),
        )

        val updated = listOf(saved) + state.wardrobe.filterNot {
            it.image == saved.image && it.category == saved.category
        }
        localRepository.saveWardrobe(updated)
        _uiState.update { it.copy(wardrobe = updated.take(40)) }
    }

    fun useWardrobeGarment(id: String) {
        val saved = _uiState.value.wardrobe.firstOrNull { it.id == id } ?: return
        val category = saved.category.toGarmentCategory()
        val garment = OutfitGarment(
            id = UUID.randomUUID().toString(),
            image = saved.image,
            title = saved.title,
            sourceUrl = saved.sourceUrl,
            merchant = saved.merchant,
            category = category,
        )

        _uiState.update { state ->
            state.copy(
                garmentImageUri = saved.image.takeIf { !it.startsWith("http://") && !it.startsWith("https://") },
                productImageUrl = saved.image.takeIf { it.startsWith("http://") || it.startsWith("https://") },
                productTitle = saved.title,
                productUrl = saved.sourceUrl,
                merchant = saved.merchant,
                selectedCategory = category,
                outfitGarments = state.outfitGarments
                    .filterNot { it.category == category }
                    .plus(garment)
                    .sortedBy { it.category.ordinal },
                stage = if (state.personImageUri != null) TryOnStage.REVIEW else state.stage,
            ).clearGeneratedMedia()
        }
        persistDraft()
    }

    fun removeWardrobeGarment(id: String) {
        val updated = _uiState.value.wardrobe.filterNot { it.id == id }
        localRepository.saveWardrobe(updated)
        _uiState.update { it.copy(wardrobe = updated) }
    }

    fun generateTryOn() {
        val state = _uiState.value
        val personImage = state.personImageUri ?: return
        val garments = state.activeGarments
        if (garments.isEmpty() || state.isGeneratingImage || state.isGeneratingVideo) return

        _uiState.update {
            it.copy(
                stage = TryOnStage.RESULT,
                prototypePrepared = false,
                isGeneratingImage = true,
                generatedImageUri = null,
                generatedImageModel = null,
                generatedImageCostUsd = null,
                imageGenerationError = null,
                generatedVideoUri = null,
                generatedVideoModel = null,
                generatedVideoCostUsd = null,
                videoGenerationError = null,
                videoGenerationStatus = MediaGenerationStatus.IDLE,
            )
        }

        viewModelScope.launch {
            generationRepository.generateTryOnImage(
                personImage = personImage,
                garmentImages = garments.map { it.image },
                garmentDescriptions = garments.map {
                    "${it.category.name}: ${it.title.ifBlank { "garment reference" }}"
                },
            ).fold(
                onSuccess = { result ->
                    val latest = _uiState.value
                    val history = createHistoryItem(latest)
                    val updatedHistory = if (history != null) {
                        listOf(history) + latest.history
                    } else {
                        latest.history
                    }
                    if (history != null) localRepository.saveHistory(updatedHistory)

                    _uiState.update {
                        it.copy(
                            isGeneratingImage = false,
                            generatedImageUri = result.uri,
                            generatedImageModel = result.model,
                            generatedImageCostUsd = result.costUsd,
                            imageGenerationError = null,
                            prototypePrepared = true,
                            history = updatedHistory.take(20),
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isGeneratingImage = false,
                            imageGenerationError = error.message ?: "Image generation failed.",
                            prototypePrepared = false,
                        )
                    }
                },
            )
        }
    }

    fun generateVideo() {
        val state = _uiState.value
        val image = state.generatedImageUri ?: return
        if (state.isGeneratingImage || state.isGeneratingVideo) return

        _uiState.update {
            it.copy(
                isGeneratingVideo = true,
                generatedVideoUri = null,
                generatedVideoModel = null,
                generatedVideoCostUsd = null,
                videoGenerationError = null,
                videoGenerationStatus = MediaGenerationStatus.SUBMITTING,
            )
        }

        viewModelScope.launch {
            generationRepository.generateTryOnVideo(
                generatedImage = image,
                motion = state.motionPreset.name,
                onStatus = { rawStatus ->
                    _uiState.update {
                        it.copy(videoGenerationStatus = rawStatus.toMediaGenerationStatus())
                    }
                },
            ).fold(
                onSuccess = { result ->
                    _uiState.update {
                        it.copy(
                            isGeneratingVideo = false,
                            generatedVideoUri = result.uri,
                            generatedVideoModel = result.model,
                            generatedVideoCostUsd = result.costUsd,
                            videoGenerationError = null,
                            videoGenerationStatus = MediaGenerationStatus.COMPLETED,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isGeneratingVideo = false,
                            videoGenerationError = error.message ?: "Video generation failed.",
                            videoGenerationStatus = MediaGenerationStatus.IDLE,
                        )
                    }
                },
            )
        }
    }

    // Backward-compatible action for the older workspace screen.
    fun preparePrototype() = generateTryOn()

    fun restoreHistory(id: String) {
        val item = _uiState.value.history.firstOrNull { it.id == id } ?: return
        val garments = item.garmentImages.mapIndexed { index, image ->
            OutfitGarment(
                id = UUID.randomUUID().toString(),
                image = image,
                title = item.garmentTitles.getOrNull(index).orEmpty(),
                sourceUrl = "",
                merchant = "",
                category = item.garmentCategories.getOrNull(index).toGarmentCategory(),
            )
        }
        _uiState.update {
            it.copy(
                personImageUri = item.personImage,
                garmentImageUri = null,
                productImageUrl = null,
                productTitle = "",
                productUrl = "",
                merchant = "",
                outfitGarments = garments,
                motionPreset = item.motion.toMotionPreset(),
                stage = TryOnStage.REVIEW,
            ).clearGeneratedMedia()
        }
        persistDraft()
    }

    fun clearHistory() {
        localRepository.saveHistory(emptyList())
        _uiState.update { it.copy(history = emptyList()) }
    }

    fun reset() {
        val current = _uiState.value
        localRepository.clearDraft()
        _uiState.value = TryOnUiState(
            wardrobe = current.wardrobe,
            history = current.history,
        )
    }

    private fun createHistoryItem(state: TryOnUiState): SavedTryOnHistory? {
        val person = state.personImageUri ?: return null
        val garments = state.activeGarments
        if (garments.isEmpty()) return null
        return SavedTryOnHistory(
            id = UUID.randomUUID().toString(),
            personImage = person,
            garmentImages = garments.map { it.image },
            garmentTitles = garments.map { it.title },
            garmentCategories = garments.map { it.category.name },
            motion = state.motionPreset.name,
            createdAt = System.currentTimeMillis(),
        )
    }

    private fun applyProductPreview(preview: ProductPreview) {
        _uiState.update { current ->
            current.copy(
                garmentImageUri = null,
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
            ).clearGeneratedMedia()
        }
        persistDraft()
    }

    private fun persistDraft() {
        val state = _uiState.value
        val garments = state.activeGarments
        if (state.personImageUri == null && garments.isEmpty()) {
            localRepository.clearDraft()
            return
        }
        localRepository.saveDraft(
            SavedTryOnDraft(
                personImage = state.personImageUri,
                garmentImages = garments.map { it.image },
                garmentTitles = garments.map { it.title },
                garmentCategories = garments.map { it.category.name },
                motion = state.motionPreset.name,
            )
        )
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
    val selectedCategory: GarmentCategory = GarmentCategory.TOP,
    val outfitGarments: List<OutfitGarment> = emptyList(),
    val wardrobe: List<SavedTryOnGarment> = emptyList(),
    val history: List<SavedTryOnHistory> = emptyList(),
    val motionPreset: MotionPreset = MotionPreset.TURN,
    val prototypePrepared: Boolean = false,
    val isGeneratingImage: Boolean = false,
    val generatedImageUri: String? = null,
    val generatedImageModel: String? = null,
    val generatedImageCostUsd: Double? = null,
    val imageGenerationError: String? = null,
    val isGeneratingVideo: Boolean = false,
    val generatedVideoUri: String? = null,
    val generatedVideoModel: String? = null,
    val generatedVideoCostUsd: Double? = null,
    val videoGenerationError: String? = null,
    val videoGenerationStatus: MediaGenerationStatus = MediaGenerationStatus.IDLE,
) {
    val effectiveGarmentImage: String?
        get() = garmentImageUri ?: productImageUrl

    val candidateGarment: OutfitGarment?
        get() = effectiveGarmentImage?.let { image ->
            OutfitGarment(
                id = "candidate-${selectedCategory.name}-$image",
                image = image,
                title = productTitle,
                sourceUrl = productUrl,
                merchant = merchant,
                category = selectedCategory,
            )
        }

    val activeGarments: List<OutfitGarment>
        get() = outfitGarments.ifEmpty { listOfNotNull(candidateGarment) }

    val canPrepare: Boolean
        get() = personImageUri != null && activeGarments.isNotEmpty()

    val hasGeneratedImage: Boolean
        get() = !generatedImageUri.isNullOrBlank()

    val hasGeneratedVideo: Boolean
        get() = !generatedVideoUri.isNullOrBlank()
}

data class OutfitGarment(
    val id: String,
    val image: String,
    val title: String,
    val sourceUrl: String,
    val merchant: String,
    val category: GarmentCategory,
)

enum class GarmentCategory {
    TOP,
    BOTTOM,
    OUTERWEAR,
    SHOES,
    ACCESSORY,
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

enum class MediaGenerationStatus {
    IDLE,
    SUBMITTING,
    PROCESSING,
    DOWNLOADING,
    COMPLETED,
}

enum class ProductLoadError {
    EMPTY_URL,
    UNAVAILABLE,
    IMAGE_NOT_FOUND,
}

private fun TryOnUiState.clearGeneratedMedia(): TryOnUiState = copy(
    prototypePrepared = false,
    isGeneratingImage = false,
    generatedImageUri = null,
    generatedImageModel = null,
    generatedImageCostUsd = null,
    imageGenerationError = null,
    isGeneratingVideo = false,
    generatedVideoUri = null,
    generatedVideoModel = null,
    generatedVideoCostUsd = null,
    videoGenerationError = null,
    videoGenerationStatus = MediaGenerationStatus.IDLE,
)

private fun String?.toGarmentCategory(): GarmentCategory =
    runCatching { GarmentCategory.valueOf(this.orEmpty()) }.getOrDefault(GarmentCategory.TOP)

private fun String?.toMotionPreset(): MotionPreset =
    runCatching { MotionPreset.valueOf(this.orEmpty()) }.getOrDefault(MotionPreset.TURN)

private fun String.toMediaGenerationStatus(): MediaGenerationStatus = when (lowercase()) {
    "submitting" -> MediaGenerationStatus.SUBMITTING
    "processing" -> MediaGenerationStatus.PROCESSING
    "downloading" -> MediaGenerationStatus.DOWNLOADING
    "completed" -> MediaGenerationStatus.COMPLETED
    else -> MediaGenerationStatus.IDLE
}
