package com.vibe.app.auth

sealed interface AuthState {
    data object SignedOut : AuthState
    data object Loading : AuthState
    data class SignedIn(
        val userId: String,
        val email: String?,
        val displayName: String?
    ) : AuthState
    data class Error(val message: String) : AuthState
}
