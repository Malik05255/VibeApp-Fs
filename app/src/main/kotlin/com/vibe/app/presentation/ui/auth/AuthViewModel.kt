package com.vibe.app.presentation.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.auth.SupabaseAuthRepository
import com.vibe.app.data.database.ChatDatabaseV2
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
    private val chatDatabaseV2: ChatDatabaseV2,
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
            }.getOrElse { error ->
                onError(error.message ?: "Supabase Google authentication failed")
                return@launch
            }

            if (authResult.isFailure) {
                onError(
                    authResult.exceptionOrNull()?.message
                        ?: "Supabase Google authentication failed"
                )
                return@launch
            }

            val userId = runCatching { supabaseAuthRepository.currentUserId() }.getOrNull()
            if (userId.isNullOrBlank()) {
                onError("Supabase user id is missing after sign-in")
                return@launch
            }

            GoogleAccountSession.save(context, account)

            val syncResult = runCatching {
                val projectDao = chatDatabaseV2.projectDao()
                val localProjects = projectDao.getProjects()

                // Upload current local projects first so a fresh sign-in never loses
                // work created before cloud auth was enabled.
                supabaseSyncRepository.uploadProjects(userId, localProjects)

                // Then pull the cloud copy. Insert only projects whose chat row exists;
                // Project has an FK to chats_v2 so orphan cloud rows cannot be inserted.
                val cloudProjects = supabaseSyncRepository.downloadProjects(userId)
                cloudProjects.forEach { cloudProject ->
                    val existing = projectDao.getProject(cloudProject.projectId)
                    val localChatId = existing?.chatId
                    if (localChatId != null) {
                        projectDao.insertProject(
                            cloudProject.copy(
                                chatId = localChatId,
                                workspacePath = existing.workspacePath.ifBlank { cloudProject.workspacePath },
                                lastBuiltAt = existing.lastBuiltAt,
                            )
                        )
                    }
                }
            }

            if (syncResult.isFailure) {
                onError(syncResult.exceptionOrNull()?.message ?: "Project synchronization failed")
                return@launch
            }

            onSuccess()
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            runCatching { supabaseAuthRepository.signOut() }
            GoogleAccountSession.clear(context)
            onComplete()
        }
    }
}
