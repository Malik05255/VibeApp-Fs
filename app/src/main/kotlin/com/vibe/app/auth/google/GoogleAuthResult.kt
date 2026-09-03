package com.vibe.app.auth.google

sealed interface GoogleAuthResult {
    data class Success(val idToken: String) : GoogleAuthResult
    data class Error(val throwable: Throwable) : GoogleAuthResult
    data object Cancelled : GoogleAuthResult
}
