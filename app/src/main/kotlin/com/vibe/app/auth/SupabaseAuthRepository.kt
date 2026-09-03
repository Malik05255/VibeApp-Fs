package com.vibe.app.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.providers.Google

class SupabaseAuthRepository(
    private val supabase: SupabaseClient
) {
    suspend fun signInWithGoogleIdToken(idToken: String) {
        require(idToken.isNotBlank()) {
            "Google ID Token is empty"
        }

        supabase.auth.signInWith(Google) {
            this.idToken = idToken
        }
    }
}
