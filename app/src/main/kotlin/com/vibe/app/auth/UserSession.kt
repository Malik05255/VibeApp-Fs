package com.vibe.app.auth

import kotlinx.coroutines.flow.Flow

interface UserSession {
    val currentUser: Flow<AuthState>
    suspend fun clear()
}
