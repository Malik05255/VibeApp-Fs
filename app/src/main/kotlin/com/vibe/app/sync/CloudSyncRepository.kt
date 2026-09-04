package com.vibe.app.sync

import com.vibe.app.data.database.entity.Project

interface CloudSyncRepository {
    suspend fun uploadProjects(projects: List<Project>, userId: String)
    suspend fun downloadProjects(userId: String): List<Project>
}

class CloudSyncManager(
    private val cloudSyncRepository: CloudSyncRepository
) {
    suspend fun sync(userId: String, localProjects: List<Project>): List<Project> {
        cloudSyncRepository.uploadProjects(localProjects, userId)
        return cloudSyncRepository.downloadProjects(userId)
    }
}

class SupabaseCloudSyncRepository(
    private val supabaseSyncRepository: SupabaseSyncRepository
) : CloudSyncRepository {

    override suspend fun uploadProjects(
        projects: List<Project>,
        userId: String
    ) {
        supabaseSyncRepository.uploadProjects(userId, projects)
    }

    override suspend fun downloadProjects(
        userId: String
    ): List<Project> {
        return supabaseSyncRepository.downloadProjects(userId)
    }
}
