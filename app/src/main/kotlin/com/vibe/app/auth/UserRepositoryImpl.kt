package com.vibe.app.auth

import com.vibe.app.auth.model.UserAccount

/**
 * Default implementation point for user persistence.
 *
 * Storage is intentionally isolated here so Room/Cloud synchronization can be
 * added without changing authentication flow.
 */
class UserRepositoryImpl : UserRepository {
    private val users = mutableMapOf<String, UserAccount>()

    override suspend fun saveUser(user: UserAccount) {
        users[user.id] = user
    }

    override suspend fun getUser(userId: String): UserAccount? {
        return users[userId]
    }

    override suspend fun updateLastLogin(userId: String, timestamp: Long) {
        val user = users[userId] ?: return
        users[userId] = user.copy(lastLoginAt = timestamp)
    }

    override suspend fun deleteUser(userId: String) {
        users.remove(userId)
    }
}
