package com.vibe.app.auth.google

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.vibe.app.BuildConfig

class GoogleCredentialProvider(
    private val context: Context
) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun getGoogleCredential(): GoogleAuthResult {
        return try {
            val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
            if (clientId.isBlank()) {
                return GoogleAuthResult.Error(
                    IllegalStateException("GOOGLE_WEB_CLIENT_ID is missing")
                )
            }

            val option = GetGoogleIdOption.Builder()
                .setServerClientId(clientId)
                .setFilterByAuthorizedAccounts(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()

            val response = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = response.credential.data
                .getString("googleCredential")

            if (credential.isNullOrBlank()) {
                GoogleAuthResult.Error(
                    IllegalStateException("Google token missing")
                )
            } else {
                GoogleAuthResult.Success(credential)
            }
        } catch (t: Throwable) {
            GoogleAuthResult.Error(t)
        }
    }
}
