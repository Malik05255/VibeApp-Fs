package com.vibe.app.data.model

data class SavedTryOnGarment(
    val id: String,
    val image: String,
    val title: String,
    val sourceUrl: String,
    val merchant: String,
    val category: String,
    val savedAt: Long,
)

data class SavedTryOnHistory(
    val id: String,
    val personImage: String,
    val garmentImages: List<String>,
    val garmentTitles: List<String>,
    val garmentCategories: List<String>,
    val motion: String,
    val createdAt: Long,
)

data class SavedTryOnDraft(
    val personImage: String?,
    val garmentImages: List<String>,
    val garmentTitles: List<String>,
    val garmentCategories: List<String>,
    val motion: String,
)
