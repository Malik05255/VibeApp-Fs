package com.vibe.app.sync

import com.vibe.app.project.database.ProjectEntity

interface CloudSyncRepository {
    suspend fun uploadProjects(projects: List<ProjectEntity>, userId: String)
    suspend fun downloadProjects(userId: String): List<ProjectEntity>
}

class CloudSyncManager(
    private val cloudSyncRepository: CloudSyncRepository
) {
    suspend fun sync(userId: String, localProjects: List<ProjectEntity>): List<ProjectEntity> {
        cloudSyncRepository.uploadProjects(localProjects, userId)
        return cloudSyncRepository.downloadProjects(userId)
    }
}

class SupabaseCloudSyncRepository(
    private val supabaseSyncRepository: SupabaseSyncRepository
) : CloudSyncRepository {

    override suspend fun uploadProjects(
        projects: List<ProjectEntity>,
        userId: String
    ) {
        supabaseSyncRepository.uploadProjects(userId, projects)
    }

    override suspend fun downloadProjects(
        userId: String
    ): List<ProjectEntity> {
        return supabaseSyncRepository.downloadProjects(userId)
    }
}
