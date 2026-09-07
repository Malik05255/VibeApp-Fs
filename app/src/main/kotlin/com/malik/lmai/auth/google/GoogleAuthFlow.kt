package com.malik.lmai.auth.google

/**
 * Coordinates the Google authentication result before passing it to the app auth layer.
 */
class GoogleAuthFlow {
    fun requireIdToken(result: GoogleAuthResult): String {
        return when (result) {
            is GoogleAuthResult.Success -> {
                require(result.idToken.isNotBlank()) {
                    "Google ID token is empty"
                }
                result.idToken
            }
            is GoogleAuthResult.Error -> {
                throw IllegalStateException(result.message, result.cause)
            }
            GoogleAuthResult.Cancelled -> {
                throw IllegalStateException("Google sign in cancelled")
            }
        }
    }
}
