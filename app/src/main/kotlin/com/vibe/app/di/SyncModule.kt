package com.vibe.app.di

import com.vibe.app.sync.SupabaseSyncRepository
import com.vibe.app.sync.SupabaseSyncRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
    @Provides
    @Singleton
    fun provideSupabaseSyncRepository(): SupabaseSyncRepository = SupabaseSyncRepositoryImpl()
}
