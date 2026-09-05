package com.vibe.app.data.repository

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.dto.OpenRouterModel
import com.vibe.app.data.dto.ThemeSetting

interface SettingRepository {

    suspend fun fetchPlatformV2s(): List<PlatformV2>

    suspend fun fetchThemes(): ThemeSetting

    suspend fun updateThemes(
        themeSetting: ThemeSetting
    )

    suspend fun addPlatformV2(
        platform: PlatformV2
    )

    suspend fun updatePlatformV2(
        platform: PlatformV2
    )

    suspend fun deletePlatformV2(
        platform: PlatformV2
    )

    suspend fun getPlatformV2ById(
        id: Int
    ): PlatformV2?

    suspend fun fetchOpenRouterModels(
        apiKey: String,
        isFreeOnly: Boolean
    ): List<OpenRouterModel>

    suspend fun getDebugMode(): Boolean

    suspend fun updateDebugMode(
        enabled: Boolean
    )

    suspend fun saveApiSettings(
        provider: String,
        apiKey: String,
        customUrl: String
    )

    suspend fun getApiProvider(): String

    suspend fun getApiKey(): String

    suspend fun getCustomApiUrl(): String

    suspend fun getFreeAiEnabled(): Boolean

    suspend fun updateFreeAiEnabled(enabled: Boolean)

    suspend fun getAiExecutionMode(): String

    suspend fun updateAiExecutionMode(mode: String)
}
