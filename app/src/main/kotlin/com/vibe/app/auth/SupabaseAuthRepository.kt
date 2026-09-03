package com.vibe.app.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google

interface SupabaseAuthRepository {
    suspend fun signInWithGoogleToken(idToken: String): Boolean
}

class SupabaseAuthRepositoryImpl(
    private val supabase: SupabaseClient
) : SupabaseAuthRepository {

    override suspend fun signInWithGoogleToken(idToken: String): Boolean {
        require(idToken.isNotBlank()) {
            "Google ID Token is empty"
        }

        return try {
            supabase.auth.signInWith(
                Google,
                idToken
            )
            true
        } catch (e: Exception) {
            false
        }
    }
}
