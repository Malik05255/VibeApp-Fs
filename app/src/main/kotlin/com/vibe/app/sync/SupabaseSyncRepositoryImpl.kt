package com.vibe.app.sync

import com.vibe.app.data.database.entity.Project
import com.vibe.app.data.database.entity.ProjectBuildStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SupabaseSyncRepositoryImpl(
    private val client: SupabaseClient
) : SupabaseSyncRepository {

    override suspend fun uploadProjects(
        userId: String,
        projects: List<Project>
    ) {
        if (projects.isEmpty()) return

        val rows = projects.map { project ->
            ProjectCloudDto(
                id = project.projectId,
                userId = userId,
                title = project.name,
                data = project.workspacePath,
                images = project.buildStatus.name,
                createdAt = project.createdAt,
                updatedAt = project.updatedAt
            )
        }

        client.from("projects").upsert(rows)
    }

    override suspend fun downloadProjects(
        userId: String
    ): List<Project> {
        return client
            .from("projects")
            .select {
                filter {
                    eq("user_id", userId)
                }
            }
            .decodeList<ProjectCloudDto>()
            .map { row ->
                Project(
                    projectId = row.id,
                    name = row.title,
                    chatId = 0,
                    workspacePath = row.data,
                    buildStatus = runCatching {
                        ProjectBuildStatus.valueOf(row.images)
                    }.getOrDefault(ProjectBuildStatus.READY),
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt
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
