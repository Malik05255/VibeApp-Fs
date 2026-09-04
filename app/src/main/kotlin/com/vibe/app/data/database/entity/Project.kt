package com.vibe.app.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class ProjectBuildStatus {
    INITIALIZING,
    READY,
    BUILDING,
    SUCCESS,
    FAILED,
}

class ProjectBuildStatusConverter {
    @TypeConverter
    fun fromStatus(status: ProjectBuildStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): ProjectBuildStatus =
        runCatching { ProjectBuildStatus.valueOf(value) }
            .getOrDefault(ProjectBuildStatus.READY)
}

@Entity(
    tableName = "projects",
    indices = [
        Index(value = ["chat_id"]),
        Index(value = ["owner_key"]),
        Index(value = ["github_repository_full_name"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ChatRoomV2::class,
            parentColumns = ["chat_id"],
            childColumns = ["chat_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class Project(
    @PrimaryKey
    @ColumnInfo("project_id")
    val projectId: String,

    @ColumnInfo("name")
    val name: String,

    @ColumnInfo("chat_id")
    val chatId: Int,

    @ColumnInfo("workspace_path")
    val workspacePath: String,

    @ColumnInfo("build_status")
    val buildStatus: ProjectBuildStatus,

    @ColumnInfo("owner_key")
    val ownerKey: String = "local",

    @ColumnInfo("github_repository_id")
    val githubRepositoryId: Long? = null,

    @ColumnInfo("github_repository_full_name")
    val githubRepositoryFullName: String? = null,

    @ColumnInfo("github_branch")
    val githubBranch: String? = null,

    @ColumnInfo("last_built_at")
    val lastBuiltAt: Long? = null,

    @ColumnInfo("created_at")
    val createdAt: Long = System.currentTimeMillis() / 1000,

    @ColumnInfo("updated_at")
    val updatedAt: Long = System.currentTimeMillis() / 1000,
)

data class ProjectWithChat(
    val project: Project,
    val chat: ChatRoomV2,
    val lastMessageContent: String? = null,
)
