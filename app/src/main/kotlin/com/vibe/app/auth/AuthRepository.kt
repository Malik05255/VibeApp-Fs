package com.vibe.app.auth

interface AuthRepository {
    suspend fun signInWithGoogle(): AuthState
    suspend fun signOut()
}
