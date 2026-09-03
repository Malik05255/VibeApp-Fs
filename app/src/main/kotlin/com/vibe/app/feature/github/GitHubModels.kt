package com.vibe.app.feature.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubUser(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class GitHubRepository(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("private") val isPrivate: Boolean = false,
    @SerialName("default_branch") val defaultBranch: String = "main",
    val permissions: GitHubRepositoryPermissions? = null,
)

@Serializable
data class GitHubRepositoryPermissions(
    val pull: Boolean = false,
    val push: Boolean = false,
    val admin: Boolean = false,
)
