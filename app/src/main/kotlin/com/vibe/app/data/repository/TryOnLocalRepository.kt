package com.vibe.app.data.repository

import android.content.Context
import com.vibe.app.data.model.SavedTryOnDraft
import com.vibe.app.data.model.SavedTryOnGarment
import com.vibe.app.data.model.SavedTryOnHistory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

class TryOnLocalRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadWardrobe(): List<SavedTryOnGarment> = runCatching {
        val array = JSONArray(preferences.getString(KEY_WARDROBE, "[]") ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val image = item.optString("image")
                if (image.isBlank()) continue
                add(
                    SavedTryOnGarment(
                        id = item.optString("id"),
                        image = image,
                        title = item.optString("title"),
                        sourceUrl = item.optString("sourceUrl"),
                        merchant = item.optString("merchant"),
                        category = item.optString("category", "TOP"),
                        savedAt = item.optLong("savedAt", 0L),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun saveWardrobe(items: List<SavedTryOnGarment>) {
        val array = JSONArray()
        items.take(MAX_WARDROBE_ITEMS).forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("image", item.image)
                    .put("title", item.title)
                    .put("sourceUrl", item.sourceUrl)
                    .put("merchant", item.merchant)
                    .put("category", item.category)
                    .put("savedAt", item.savedAt)
            )
        }
        preferences.edit().putString(KEY_WARDROBE, array.toString()).apply()
    }

    fun loadHistory(): List<SavedTryOnHistory> = runCatching {
        val array = JSONArray(preferences.getString(KEY_HISTORY, "[]") ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val personImage = item.optString("personImage")
                if (personImage.isBlank()) continue
                add(
                    SavedTryOnHistory(
                        id = item.optString("id"),
                        personImage = personImage,
                        garmentImages = item.optJSONArray("garmentImages").toStringList(),
                        garmentTitles = item.optJSONArray("garmentTitles").toStringList(),
                        garmentCategories = item.optJSONArray("garmentCategories").toStringList(),
                        motion = item.optString("motion", "TURN"),
                        createdAt = item.optLong("createdAt", 0L),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun saveHistory(items: List<SavedTryOnHistory>) {
        val array = JSONArray()
        items.take(MAX_HISTORY_ITEMS).forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("personImage", item.personImage)
                    .put("garmentImages", JSONArray(item.garmentImages))
                    .put("garmentTitles", JSONArray(item.garmentTitles))
                    .put("garmentCategories", JSONArray(item.garmentCategories))
                    .put("motion", item.motion)
                    .put("createdAt", item.createdAt)
            )
        }
        preferences.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    fun loadDraft(): SavedTryOnDraft? = runCatching {
        val raw = preferences.getString(KEY_DRAFT, null) ?: return@runCatching null
        val item = JSONObject(raw)
        SavedTryOnDraft(
            personImage = item.optString("personImage").takeIf { it.isNotBlank() },
            garmentImages = item.optJSONArray("garmentImages").toStringList(),
            garmentTitles = item.optJSONArray("garmentTitles").toStringList(),
            garmentCategories = item.optJSONArray("garmentCategories").toStringList(),
            motion = item.optString("motion", "TURN"),
        )
    }.getOrNull()

    fun saveDraft(draft: SavedTryOnDraft) {
        val item = JSONObject()
            .put("personImage", draft.personImage.orEmpty())
            .put("garmentImages", JSONArray(draft.garmentImages))
            .put("garmentTitles", JSONArray(draft.garmentTitles))
            .put("garmentCategories", JSONArray(draft.garmentCategories))
            .put("motion", draft.motion)
        preferences.edit().putString(KEY_DRAFT, item.toString()).apply()
    }

    fun clearDraft() {
        preferences.edit().remove(KEY_DRAFT).apply()
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "tryon_local"
        const val KEY_WARDROBE = "wardrobe"
        const val KEY_HISTORY = "history"
        const val KEY_DRAFT = "draft"
        const val MAX_WARDROBE_ITEMS = 40
        const val MAX_HISTORY_ITEMS = 20
    }
}
