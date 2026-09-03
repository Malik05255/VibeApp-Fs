package com.vibe.app.sync

import com.vibe.app.project.database.ProjectEntity

class SupabaseSyncRepositoryImpl : SupabaseSyncRepository {

    override suspend fun uploadProjects(
        userId: String,
        projects: List<ProjectEntity>
    ) {
        // Supabase client upload will be connected here.
    }

    override suspend fun downloadProjects(
        userId: String
    ): List<ProjectEntity> {
        // Supabase client download will be connected here.
        return emptyList()
    }
}
