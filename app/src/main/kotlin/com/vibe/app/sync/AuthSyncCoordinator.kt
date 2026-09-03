package com.vibe.app.sync

import com.vibe.app.project.database.ProjectEntity

class AuthSyncCoordinator(
    private val cloudSyncManager: CloudSyncManager
) {
    suspend fun syncAfterLogin(
        userId: String,
        localProjects: List<ProjectEntity>
    ): List<ProjectEntity> {
        return cloudSyncManager.sync(userId, localProjects)
    }
}
