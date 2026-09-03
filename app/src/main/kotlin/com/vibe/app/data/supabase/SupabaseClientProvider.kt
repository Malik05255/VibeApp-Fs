package com.vibe.app.data.supabase

/**
 * Supabase client entry point.
 * Implementation will be connected after adding the Supabase Kotlin SDK dependency.
 */
object SupabaseClientProvider {
    fun isConfigured(): Boolean {
        return SupabaseConfig.url.isNotBlank() && SupabaseConfig.anonKey.isNotBlank()
    }
}
