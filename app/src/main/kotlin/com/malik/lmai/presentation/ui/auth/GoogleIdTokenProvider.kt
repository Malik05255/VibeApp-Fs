package com.malik.lmai.presentation.ui.auth

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.malik.lmai.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Supplies a currently usable Google ID token for H Cloud calls.
 *
 * Google ID tokens are short-lived. Persisting the token from the interactive sign-in
 * forever makes an otherwise valid H owner session eventually lose cloud memory and
 * reminder sync. This provider keeps the existing account session but silently asks
 * Google for a fresh ID token when the cached JWT is near expiry or when a cloud caller
 * explicitly asks for a retry after HTTP 401.
 */
@Singleton
class GoogleIdTokenProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasSignedInSession(): Boolean = GoogleAccountSession.get(context) != null

    suspend fun getToken(forceRefresh: Boolean = false): String? {
        val session = GoogleAccountSession.get(context) ?: return null
        val cached = session.idToken?.trim()?.takeIf { it.isNotEmpty() }
        if (!forceRefresh && cached != null && GoogleIdTokenFreshness.isFresh(cached)) {
            return cached
        }

        val refreshedAccount = silentRefresh() ?: return null
        val refreshedToken = refreshedAccount.idToken?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null

        GoogleAccountSession.save(
            context,
            GoogleAccount(
                email = refreshedAccount.email?.trim()?.takeIf { it.isNotEmpty() }
                    ?: session.email,
                displayName = refreshedAccount.displayName ?: session.displayName,
                profilePictureUrl = refreshedAccount.photoUrl?.toString()
                    ?: session.profilePictureUrl,
                idToken = refreshedToken,
            ),
        )
        return refreshedToken
    }

    private suspend fun silentRefresh(): GoogleSignInAccount? {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (clientId.isEmpty()) return null

        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestIdToken(clientId)
            .requestScopes(Scope(DRIVE_FILE_SCOPE))
            .build()
        val client = GoogleSignIn.getClient(context, options)

        return suspendCancellableCoroutine { continuation ->
            client.silentSignIn().addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener
                continuation.resume(if (task.isSuccessful) task.result else null)
            }
        }
    }

    companion object {
        private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}

/** Pure JWT-expiry logic kept separate so it is regression-testable without Android auth. */
internal object GoogleIdTokenFreshness {
    private val json = Json { ignoreUnknownKeys = true }
    private const val MINIMUM_VALIDITY_SECONDS = 5 * 60L

    fun isFresh(
        token: String,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L,
    ): Boolean {
        val parts = token.split('.')
        if (parts.size < 2) return false
        val payload = runCatching {
            val decoded = Base64.getUrlDecoder().decode(padBase64Url(parts[1]))
            json.parseToJsonElement(decoded.toString(Charsets.UTF_8)).jsonObject
        }.getOrNull() ?: return false
        val expiresAt = payload["exp"]?.jsonPrimitive?.longOrNull ?: return false
        return expiresAt > nowEpochSeconds + MINIMUM_VALIDITY_SECONDS
    }

    private fun padBase64Url(value: String): String {
        val remainder = value.length % 4
        return if (remainder == 0) value else value + "=".repeat(4 - remainder)
    }
}
