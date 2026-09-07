package com.malik.lmai.project

import com.malik.lmai.project.database.ProjectDao
import com.malik.lmai.project.database.ProjectEntity

class ProjectRepository(
    private val projectDao: ProjectDao,
    private val userSessionProvider: UserSessionProvider
) {
    suspend fun saveProject(
        title: String,
        data: String,
        images: String?
    ) {
        val userId = userSessionProvider.currentUserId() ?: return
        projectDao.insert(
            ProjectEntity(
                id = java.util.UUID.randomUUID().toString(),
                userId = userId,
                title = title,
                data = data,
                images = images,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getMyProjects(): List<ProjectEntity> {
        val userId = userSessionProvider.currentUserId() ?: return emptyList()
        return projectDao.getByUserId(userId)
    }
}

interface UserSessionProvider {
    fun currentUserId(): String?
}
