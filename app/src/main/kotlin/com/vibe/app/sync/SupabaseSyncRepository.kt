package com.vibe.app.sync

import com.vibe.app.project.database.ProjectEntity

interface SupabaseSyncRepository {
    suspend fun uploadProjects(userId: String, projects: List<ProjectEntity>)
    suspend fun downloadProjects(userId: String): List<ProjectEntity>
}
