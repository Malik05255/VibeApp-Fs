package com.vibe.app.data.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientProvider {

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SupabaseConfig.url,
            supabaseKey = SupabaseConfig.anonKey
        ) {
            install(Postgrest)
        }
    }

    fun isConfigured(): Boolean {
        return SupabaseConfig.url.isNotBlank() &&
            SupabaseConfig.anonKey.isNotBlank()
    }
}
