package com.vibe.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.vibe.app.BuildConfig
import com.vibe.app.sync.AuthSyncCoordinator

class GoogleAuthManager(
    private val context: Context,
    private val userRepository: UserRepository,
    private val supabaseAuthRepository: SupabaseAuthRepository,
    private val authSyncCoordinator: AuthSyncCoordinator
) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun signIn(): AuthState {
        return try {
            val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
            if (clientId.isBlank()) {
                return AuthState.Error("Google Web Client ID is missing")
            }

            val googleOption = GetGoogleIdOption.Builder()
                .setServerClientId(clientId)
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

            val idToken = credential.idToken
            if (idToken.isBlank()) {
                return AuthState.Error("Google ID Token is empty")
            }

            val supabaseResult = supabaseAuthRepository.signInWithGoogleToken(idToken)
            if (supabaseResult.isFailure) {
                return AuthState.Error(
                    supabaseResult.exceptionOrNull()?.message
                        ?: "Supabase Google authentication failed"
                )
            }

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
            authSyncCoordinator.syncAfterLogin(userId = user.id, localProjects = emptyList())

            AuthState.SignedIn(
                userId = user.id,
                email = user.email,
                displayName = user.displayName
            )
        } catch (e: GetCredentialException) {
            val message = e.message ?: "Google Sign-In failed"
            if (message.contains("10") || message.contains("DEVELOPER_ERROR", ignoreCase = true)) {
                AuthState.Error("Google Sign-In configuration error (Code 10). Verify GOOGLE_WEB_CLIENT_ID, Android OAuth package com.vibe.app, and signing SHA-1/SHA-256.")
            } else {
                AuthState.Error(message)
            }
        } catch (e: Exception) {
            AuthState.Error(e.message ?: "Google Sign-In failed")
        }
    }
}
