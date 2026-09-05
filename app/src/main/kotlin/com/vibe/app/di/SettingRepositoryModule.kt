package com.vibe.app.di

import com.vibe.app.data.database.dao.ChatPlatformModelV2Dao
import com.vibe.app.data.database.dao.PlatformV2Dao
import com.vibe.app.data.datastore.SettingDataSource
import com.vibe.app.data.network.OpenRouterModelsAPI
import com.vibe.app.data.repository.SettingRepository
import com.vibe.app.data.repository.SettingRepositoryImpl
import com.vibe.app.feature.ai.FreeAiRouter
import com.vibe.app.feature.ai.openrouter.OpenRouterCredentialStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingRepositoryModule {

    @Provides
    @Singleton
    fun provideSettingRepository(
        settingDataSource: SettingDataSource,
        platformV2Dao: PlatformV2Dao,
        chatPlatformModelV2Dao: ChatPlatformModelV2Dao,
        openRouterModelsAPI: OpenRouterModelsAPI,
        freeAiRouter: FreeAiRouter,
        openRouterCredentialStore: OpenRouterCredentialStore,
    ): SettingRepository = SettingRepositoryImpl(
        settingDataSource,
        platformV2Dao,
        chatPlatformModelV2Dao,
        openRouterModelsAPI,
        freeAiRouter,
        openRouterCredentialStore,
    )
}
