package com.vibe.app.sync

import com.vibe.app.project.database.ProjectEntity

class AuthSyncCoordinator(
    private val cloudSyncManager: CloudSyncManager
) {
    suspend fun syncAfterLogin(
        userId: String,
        localProjects: List<ProjectEntity>
    ): List<ProjectEntity> {
        val cloudProjects = cloudSyncManager.sync(userId, localProjects)

        return mergeProjects(
            localProjects,
            cloudProjects
        )
    }

    private fun mergeProjects(
        localProjects: List<ProjectEntity>,
        cloudProjects: List<ProjectEntity>
    ): List<ProjectEntity> {
        val merged = localProjects.associateBy { it.id }.toMutableMap()

        cloudProjects.forEach { cloudProject ->
            val localProject = merged[cloudProject.id]

            if (localProject == null || cloudProject.updatedAt > localProject.updatedAt) {
                merged[cloudProject.id] = cloudProject
            }
        }

        return merged.values.toList()
    }
}
