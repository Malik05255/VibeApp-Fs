package com.vibe.app.sync

import com.vibe.app.project.database.ProjectEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SupabaseSyncRepositoryImpl(
    private val client: SupabaseClient
) : SupabaseSyncRepository {

    override suspend fun uploadProjects(
        userId: String,
        projects: List<ProjectEntity>
    ) {
        if (projects.isEmpty()) return

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
    @SerialName("user_id") val userId: String,
    val title: String,
    val data: String,
    val images: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long
)
