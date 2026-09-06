package com.malik.lmai.auth.google

sealed interface GoogleAuthResult {
    data class Success(
        val idToken: String,
        val email: String? = null,
        val displayName: String? = null,
        val photoUrl: String? = null
    ) : GoogleAuthResult

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : GoogleAuthResult

    data object Cancelled : GoogleAuthResult
}
