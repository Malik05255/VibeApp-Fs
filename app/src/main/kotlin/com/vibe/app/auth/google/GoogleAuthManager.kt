package com.vibe.app.auth.google

import android.content.Context

class GoogleAuthManager(
    private val provider: GoogleCredentialProvider
) {
    suspend fun signIn(context: Context): GoogleAuthResult {
        return provider.getGoogleCredential()
    }
}
