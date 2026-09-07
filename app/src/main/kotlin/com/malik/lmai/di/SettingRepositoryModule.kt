package com.malik.lmai.di

import com.malik.lmai.data.database.dao.ChatPlatformModelV2Dao
import com.malik.lmai.data.database.dao.PlatformV2Dao
import com.malik.lmai.data.datastore.SettingDataSource
import com.malik.lmai.data.network.OpenRouterModelsAPI
import com.malik.lmai.data.repository.SettingRepository
import com.malik.lmai.data.repository.SettingRepositoryImpl
import com.malik.lmai.feature.ai.FreeAiRouter
import com.malik.lmai.feature.ai.openrouter.OpenRouterCredentialStore
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
