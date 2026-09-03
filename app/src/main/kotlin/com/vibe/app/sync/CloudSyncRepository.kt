package com.vibe.app.sync

import com.vibe.app.project.database.ProjectEntity

interface CloudSyncRepository {
    suspend fun uploadProjects(projects: List<ProjectEntity>)
    suspend fun downloadProjects(userId: String): List<ProjectEntity>
}

class CloudSyncManager(
    private val cloudSyncRepository: CloudSyncRepository
) {
    suspend fun sync(userId: String, projects: List<ProjectEntity>) {
        cloudSyncRepository.uploadProjects(projects)
        cloudSyncRepository.downloadProjects(userId)
    }
}
