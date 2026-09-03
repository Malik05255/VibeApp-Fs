package com.vibe.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.vibe.app.sync.AuthSyncCoordinator

class GoogleAuthManager(
    private val context: Context,
    private val userRepository: UserRepository,
    private val authSyncCoordinator: AuthSyncCoordinator
) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun signIn(): AuthState {
        return try {
            val googleOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = GoogleIdTokenCredential
                .createFrom(result.credential.data)

            val user = com.vibe.app.auth.model.UserAccount(
                id = credential.id,
                googleId = credential.id,
                email = credential.id,
                displayName = credential.displayName,
                photoUrl = credential.profilePictureUri?.toString(),
                createdAt = System.currentTimeMillis(),
                lastLoginAt = System.currentTimeMillis()
            )

            userRepository.saveUser(user)

            authSyncCoordinator.syncAfterLogin(
                userId = user.id,
                localProjects = emptyList()
            )

            AuthState.SignedIn(
                userId = user.id,
                email = user.email,
                displayName = user.displayName
            )
        } catch (e: GetCredentialException) {
            val message = e.message ?: "Google Sign-In failed"

            if (message.contains("10") || message.contains("DEVELOPER_ERROR")) {
                AuthState.Error("Google Sign-In configuration error (Code 10). Check OAuth Client ID and SHA-1.")
            } else {
                AuthState.Error(message)
            }
        } catch (e: Exception) {
            AuthState.Error(e.message ?: "Google Sign-In failed")
        }
    }
}
