package com.malik.lmai.auth.storage

import com.malik.lmai.auth.model.UserAccount

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
