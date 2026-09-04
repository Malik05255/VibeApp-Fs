package com.vibe.app.di

import com.vibe.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        val configuredUrl = BuildConfig.SUPABASE_URL.trim()
        val configuredKey = BuildConfig.SUPABASE_ANON_KEY.trim()

        // The GitHub workflow may intentionally build without Supabase secrets.
        // Hilt creates this singleton when AuthViewModel is resolved, so throwing
        // here would crash the app before the login screen is usable.
        val supabaseUrl = configuredUrl.ifBlank { FALLBACK_SUPABASE_URL }
        val supabaseKey = configuredKey.ifBlank { FALLBACK_SUPABASE_KEY }

        return createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseKey
        ) {
            install(Auth)
            install(Postgrest)
        }
    }

    private const val FALLBACK_SUPABASE_URL = "https://invalid.localhost"
    private const val FALLBACK_SUPABASE_KEY = "unconfigured-anon-key"
}
