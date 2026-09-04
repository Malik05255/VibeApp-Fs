package com.vibe.app.presentation.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.auth.SupabaseAuthRepository
import com.vibe.app.data.database.DatabaseModule
import com.vibe.app.sync.SupabaseSyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
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
            val signedIn = supabaseAuthRepository.signInWithGoogleToken(idToken)
            if (!signedIn) {
                onError("Supabase Google authentication failed")
                return@launch
            }

            val userId = supabaseAuthRepository.currentUserId()
            if (userId.isNullOrBlank()) {
                onError("Supabase user id is missing after sign-in")
                return@launch
            }

            val cloudProjects = runCatching {
                supabaseSyncRepository.downloadProjects(userId)
            }.getOrElse {
                onError(it.message ?: "Failed to download cloud projects")
                return@launch
            }

            val projectDao = DatabaseModule.provideDatabase(context).projectDao()
            cloudProjects.forEach { projectDao.insert(it) }

            GoogleAccountSession.save(context, account)
            onSuccess()
        }
    }
}
