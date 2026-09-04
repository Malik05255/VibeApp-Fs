package com.vibe.app.presentation.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@HiltViewModel
class AuthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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
            }.onSuccess {
                onSuccess()
            }.onFailure { error ->
                onError(error.message ?: "Unable to save Google account locally")
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                val googleClient = GoogleSignIn.getClient(
                    context,
                    GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build(),
                )
                googleClient.revokeAccess().await()
                googleClient.signOut().await()
            }
            GoogleAccountSession.clear(context)
            onComplete()
        }
    }
}
