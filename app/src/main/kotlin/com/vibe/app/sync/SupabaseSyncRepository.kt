package com.vibe.app.sync

import com.vibe.app.data.database.entity.Project

interface SupabaseSyncRepository {
    suspend fun uploadProjects(userId: String, projects: List<Project>)
    suspend fun downloadProjects(userId: String): List<Project>
}
