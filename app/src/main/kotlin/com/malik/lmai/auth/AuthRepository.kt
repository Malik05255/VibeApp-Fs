package com.malik.lmai.auth

interface AuthRepository {
    suspend fun signInWithGoogle(): AuthState
    suspend fun signOut()
}
