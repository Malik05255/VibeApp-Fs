package com.vibe.app.di

import com.vibe.app.auth.SupabaseAuthRepository
import com.vibe.app.auth.SupabaseAuthRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideSupabaseAuthRepository(
        supabaseClient: SupabaseClient
    ): SupabaseAuthRepository {
        return SupabaseAuthRepositoryImpl(supabaseClient)
    }
}
