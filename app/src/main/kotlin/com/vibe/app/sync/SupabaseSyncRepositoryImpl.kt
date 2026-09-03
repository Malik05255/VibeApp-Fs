package com.vibe.app.sync

import com.vibe.app.data.supabase.SupabaseClientProvider
import com.vibe.app.project.database.ProjectEntity
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import java.util.UUID

class SupabaseSyncRepositoryImpl : SupabaseSyncRepository {

    private val client
        get() = SupabaseClientProvider.client

    override suspend fun uploadProjects(
        userId: String,
        projects: List<ProjectEntity>
    ) {
        if (!SupabaseClientProvider.isConfigured()) return

        val rows = projects.map {
            ProjectCloudDto(
                id = it.id,
                userId = userId,
                title = it.title,
                data = it.data,
                images = it.images ?: "",
                createdAt = it.createdAt,
                updatedAt = it.updatedAt
            )
        }

        client.from("projects").upsert(rows)
    }

    override suspend fun downloadProjects(
        userId: String
    ): List<ProjectEntity> {
        if (!SupabaseClientProvider.isConfigured()) return emptyList()

        return client
            .from("projects")
            .select {
                filter {
                    eq("user_id", userId)
                }
            }
            .decodeList<ProjectCloudDto>()
            .map {
                ProjectEntity(
                    id = it.id,
                    userId = it.userId,
                    title = it.title,
                    data = it.data,
                    images = it.images,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            }
    }
}

@Serializable
data class ProjectCloudDto(
    val id: String,
    val userId: String,
    val title: String,
    val data: String,
    val images: String,
    val createdAt: Long,
    val updatedAt: Long
)
