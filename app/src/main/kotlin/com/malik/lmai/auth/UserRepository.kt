package com.malik.lmai.auth

import com.malik.lmai.auth.model.UserAccount

/**
 * Repository layer responsible for user account operations.
 *
 * This abstraction keeps authentication providers separated from
 * user storage and future cloud synchronization.
 */
interface UserRepository {
    suspend fun saveUser(user: UserAccount)

    suspend fun getUser(userId: String): UserAccount?

    suspend fun updateLastLogin(userId: String, timestamp: Long)

    suspend fun deleteUser(userId: String)
}
