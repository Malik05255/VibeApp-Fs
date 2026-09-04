package com.vibe.app.sync

import com.vibe.app.data.database.entity.Project

class AuthSyncCoordinator(
    private val cloudSyncManager: CloudSyncManager
) {
    suspend fun syncAfterLogin(
        userId: String,
        localProjects: List<Project>
    ): List<Project> {
        val cloudProjects = cloudSyncManager.sync(userId, localProjects)

        return mergeProjects(
            localProjects,
            cloudProjects
        )
    }

    private fun mergeProjects(
        localProjects: List<Project>,
        cloudProjects: List<Project>
    ): List<Project> {
        val merged = localProjects.associateBy { it.projectId }.toMutableMap()

        cloudProjects.forEach { cloudProject ->
            val localProject = merged[cloudProject.projectId]

            if (localProject == null || cloudProject.updatedAt > localProject.updatedAt) {
                merged[cloudProject.projectId] = cloudProject
            }
        }

        return merged.values.toList()
    }
}
