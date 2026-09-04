package com.vibe.app.presentation.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.auth.SupabaseAuthRepository
import com.vibe.app.data.database.DatabaseModule
import com.vibe.app.sync.SupabaseSyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabaseAuthRepository: SupabaseAuthRepository,
    private val supabaseSyncRepository: SupabaseSyncRepository,
) : ViewModel() {

    fun completeGoogleSignIn(
        account: GoogleAccount,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val idToken = account.idToken
        if (idToken.isNullOrBlank()) {
            onError("Google ID token is missing")
            return
        }

        viewModelScope.launch {
            val authResult = runCatching {
                supabaseAuthRepository.signInWithGoogleToken(idToken)
            }

            if (authResult.isFailure || authResult.getOrNull() != true) {
                onError(authResult.exceptionOrNull()?.message ?: "Supabase Google authentication failed")
                return@launch
            }

            val userId = runCatching {
                supabaseAuthRepository.currentUserId()
            }.getOrNull()

            if (userId.isNullOrBlank()) {
                onError("Supabase user id is missing after sign-in")
                return@launch
            }

            // Persist the authenticated account before cloud restore. Cloud restore is best-effort
            // and must never crash or block entry to the app if the remote schema/data is malformed.
            GoogleAccountSession.save(context, account)

            runCatching {
                val cloudProjects = supabaseSyncRepository.downloadProjects(userId)
                val projectDao = DatabaseModule.provideDatabase(context).projectDao()
                cloudProjects.forEach { project ->
                    runCatching { projectDao.insert(project) }
                }
            }

            onSuccess()
        }
    }
}
