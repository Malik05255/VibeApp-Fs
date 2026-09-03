package com.vibe.app.auth.storage

import com.vibe.app.auth.model.UserAccount

/**
 * Storage abstraction for user accounts.
 *
 * This keeps authentication independent from the storage engine.
 * A Room or Cloud implementation can be plugged in later.
 */
interface UserStorage {
    suspend fun save(user: UserAccount)
    suspend fun get(id: String): UserAccount?
    suspend fun remove(id: String)
}
