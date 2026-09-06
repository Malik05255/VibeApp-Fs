package com.malik.lmai.auth.model

/**
 * User account model linked to Google authentication.
 * This model will be used later for cloud sync and project ownership.
 */
data class UserAccount(
    val id: String,
    val googleId: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val createdAt: Long,
    val lastLoginAt: Long
)
