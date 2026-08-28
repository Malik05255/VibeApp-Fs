package com.vibe.app.presentation.ui.tryon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.data.model.ProductPreview
import com.vibe.app.data.model.SavedTryOnDraft
import com.vibe.app.data.model.SavedTryOnGarment
import com.vibe.app.data.model.SavedTryOnHistory
import com.vibe.app.data.repository.ProductPreviewRepository
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
                prototypePrepared = false,
            )
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
                prototypePrepared = false,
            )
        }
        persistDraft()
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

    fun onCategorySelected(category: GarmentCategory) {
        _uiState.update { it.copy(selectedCategory = category, prototypePrepared = false) }
        persistDraft()
    }

    fun onMotionPresetSelected(preset: MotionPreset) {
        _uiState.update { it.copy(motionPreset = preset, prototypePrepared = false) }
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
                prototypePrepared = false,
            )
        }
        persistDraft()
    }

    fun removeOutfitGarment(id: String) {
        _uiState.update {
            it.copy(
                outfitGarments = it.outfitGarments.filterNot { garment -> garment.id == id },
                prototypePrepared = false,
            )
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
                prototypePrepared = false,
            )
        }
        persistDraft()
    }

    fun removeWardrobeGarment(id: String) {
        val updated = _uiState.value.wardrobe.filterNot { it.id == id }
        localRepository.saveWardrobe(updated)
        _uiState.update { it.copy(wardrobe = updated) }
    }

    fun preparePrototype() {
        val state = _uiState.value
        if (!state.canPrepare) return

        val garments = state.activeGarments
        val historyItem = SavedTryOnHistory(
            id = UUID.randomUUID().toString(),
            personImage = state.personImageUri ?: return,
            garmentImages = garments.map { it.image },
            garmentTitles = garments.map { it.title },
            garmentCategories = garments.map { it.category.name },
            motion = state.motionPreset.name,
            createdAt = System.currentTimeMillis(),
        )
        val updatedHistory = listOf(historyItem) + state.history
        localRepository.saveHistory(updatedHistory)

        _uiState.update {
            it.copy(
                stage = TryOnStage.RESULT,
                prototypePrepared = true,
                history = updatedHistory.take(20),
            )
        }
        persistDraft()
    }

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
                prototypePrepared = false,
            )
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
                prototypePrepared = false,
            )
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

enum class ProductLoadError {
    EMPTY_URL,
    UNAVAILABLE,
    IMAGE_NOT_FOUND,
}

private fun String?.toGarmentCategory(): GarmentCategory =
    runCatching { GarmentCategory.valueOf(this.orEmpty()) }.getOrDefault(GarmentCategory.TOP)

private fun String?.toMotionPreset(): MotionPreset =
    runCatching { MotionPreset.valueOf(this.orEmpty()) }.getOrDefault(MotionPreset.TURN)
