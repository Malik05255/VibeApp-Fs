package com.malik.lmai.auth

import com.malik.lmai.auth.database.UserDao
import com.malik.lmai.auth.database.UserEntity
import com.malik.lmai.auth.model.UserAccount

class UserRepositoryImpl(
    private val userDao: UserDao
) : UserRepository {

    override suspend fun saveUser(user: UserAccount) {
        userDao.insert(user.toEntity())
    }

    override suspend fun getUser(userId: String): UserAccount? {
        return userDao.getById(userId)?.toModel()
    }

    override suspend fun updateLastLogin(userId: String, timestamp: Long) {
        val current = userDao.getById(userId) ?: return
        userDao.update(current.copy(lastLoginAt = timestamp))
    }

    override suspend fun deleteUser(userId: String) {
        val current = userDao.getById(userId) ?: return
        userDao.delete(current)
    }

    private fun UserAccount.toEntity() = UserEntity(
        id = id,
        googleId = googleId,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl,
        createdAt = createdAt,
        lastLoginAt = lastLoginAt
    )

    private fun UserEntity.toModel() = UserAccount(
        id = id,
        googleId = googleId,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl,
        createdAt = createdAt,
        lastLoginAt = lastLoginAt
    )
}
