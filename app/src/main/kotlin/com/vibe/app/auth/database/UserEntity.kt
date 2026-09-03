package com.vibe.app.auth.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,
    val googleId: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val createdAt: Long,
    val lastLoginAt: Long
)
