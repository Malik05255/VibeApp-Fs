package com.malik.lmai.presentation.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.malik.lmai.feature.assistant.HOwnerContinuityClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlin.coroutines.resume

@HiltViewModel
class AuthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hOwnerContinuityClient: HOwnerContinuityClient,
) : ViewModel() {

    fun completeGoogleSignIn(
        account: GoogleAccount,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (account.email.isBlank()) {
            onError("Google account email is missing")
            return
        }

        viewModelScope.launch {
            runCatching {
                GoogleAccountSession.save(context, account)

                // Move H is intentionally best-effort for authentication availability: a
                // temporary network failure must not block Google sign-in. When the cloud
                // positively proves that this account owns an existing H, persist only the
                // opaque continuity handle and use it as the local H owner anchor. A positive
                // unlinked result clears a stale handle. Errors leave a same-account handle
                // untouched so offline startup cannot silently fork H's local scope.
                val continuity = hOwnerContinuityClient.status()
                when {
                    continuity.ok && continuity.linked &&
                        continuity.resumeExistingH && continuity.continuityHandle != null -> {
                        GoogleAccountSession.saveHContinuityHandle(
                            context,
                            continuity.continuityHandle,
                        )
                    }

                    continuity.ok && !continuity.linked && continuity.pairingRequired -> {
                        GoogleAccountSession.clearHContinuityHandle(context)
                    }
                }
            }.onSuccess {
                onSuccess()
            }.onFailure { error ->
                onError(error.message ?: "Unable to save Google account locally")
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            val googleClient = GoogleSignIn.getClient(
                context,
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build(),
            )

            runCatching {
                suspendCancellableCoroutine<Unit> { continuation ->
                    googleClient.revokeAccess().addOnCompleteListener {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
                suspendCancellableCoroutine<Unit> { continuation ->
                    googleClient.signOut().addOnCompleteListener {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }

            GoogleAccountSession.clear(context)
            onComplete()
        }
    }
}
