package com.vibe.app.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken

interface SupabaseAuthRepository {
    suspend fun signInWithGoogleToken(idToken: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
    fun currentUserId(): String?
}

class SupabaseAuthRepositoryImpl(
    private val supabase: SupabaseClient
) : SupabaseAuthRepository {

    override suspend fun signInWithGoogleToken(idToken: String): Result<Unit> {
        if (idToken.isBlank()) {
            return Result.failure(IllegalArgumentException("Google ID Token is empty"))
        }

        return runCatching {
            supabase.auth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
            }

            checkNotNull(supabase.auth.currentUserOrNull()) {
                "Supabase did not create an authenticated user session"
            }
        }
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        supabase.auth.signOut()
    }

    override fun currentUserId(): String? {
        return supabase.auth.currentUserOrNull()?.id
    }
}
