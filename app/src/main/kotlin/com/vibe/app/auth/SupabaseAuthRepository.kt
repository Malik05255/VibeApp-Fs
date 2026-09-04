package com.vibe.app.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google

interface SupabaseAuthRepository {
    suspend fun signInWithGoogleToken(idToken: String): Boolean
    fun currentUserId(): String?
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
            supabase.auth.currentUserOrNull() != null
        } catch (e: Exception) {
            false
        }
    }

    override fun currentUserId(): String? {
        return supabase.auth.currentUserOrNull()?.id
    }
}
