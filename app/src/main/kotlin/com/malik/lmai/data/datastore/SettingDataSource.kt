package com.malik.lmai.data.datastore

import com.malik.lmai.data.model.DynamicTheme
import com.malik.lmai.data.model.ThemeMode

interface SettingDataSource {

    suspend fun updateDynamicTheme(
        theme: DynamicTheme
    )

    suspend fun updateThemeMode(
        themeMode: ThemeMode
    )

    suspend fun getDynamicTheme(): DynamicTheme?

    suspend fun getThemeMode(): ThemeMode?

    suspend fun updateDebugMode(
        enabled: Boolean
    )

    suspend fun getDebugMode(): Boolean

    suspend fun updateApiProvider(
        provider: String
    )

    suspend fun getApiProvider(): String

    suspend fun updateApiKey(
        apiKey: String
    )

    suspend fun getApiKey(): String

    suspend fun updateCustomApiUrl(
        url: String
    )

    suspend fun getCustomApiUrl(): String

    suspend fun updateFreeAiEnabled(
        enabled: Boolean
    )

    suspend fun getFreeAiEnabled(): Boolean

    suspend fun updateAiExecutionMode(
        mode: String
    )

    suspend fun getAiExecutionMode(): String
}
