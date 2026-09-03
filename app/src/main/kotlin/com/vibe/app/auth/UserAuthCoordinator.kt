package com.vibe.app.auth

import com.vibe.app.auth.model.UserAccount

/**
 * Coordinates authentication result with user persistence.
 */
class UserAuthCoordinator(
    private val userRepository: UserRepository
) {
    suspend fun onSignedIn(state: AuthState): AuthState {
        if (state is AuthState.SignedIn) {
            userRepository.saveUser(
                UserAccount(
                    id = state.userId,
                    googleId = state.userId,
                    email = state.email,
                    displayName = state.displayName,
                    photoUrl = null,
                    createdAt = System.currentTimeMillis(),
                    lastLoginAt = System.currentTimeMillis()
                )
            )
        }
        return state
    }
}
