package com.vibe.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vibe.app.data.database.entity.Project
import com.vibe.app.data.database.entity.ProjectBuildStatus

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects WHERE owner_key = :ownerKey ORDER BY created_at DESC")
    suspend fun getProjects(ownerKey: String): List<Project>

    @Query("""
        SELECT * FROM projects
        WHERE owner_key = :ownerKey OR owner_key = :legacyOwnerKey
        ORDER BY created_at DESC
    """)
    suspend fun getProjectsForGitHub(
        ownerKey: String,
        legacyOwnerKey: String,
    ): List<Project>

    @Query("SELECT * FROM projects WHERE project_id = :projectId AND owner_key = :ownerKey")
    suspend fun getProject(projectId: String, ownerKey: String): Project?

    @Query("SELECT * FROM projects WHERE chat_id = :chatId AND owner_key = :ownerKey")
    suspend fun getProjectByChatId(chatId: Int, ownerKey: String): Project?

    @Query("SELECT * FROM projects WHERE github_repository_full_name = :repositoryFullName ORDER BY created_at ASC")
    suspend fun getProjectsByRepository(repositoryFullName: String): List<Project>

    @Query("SELECT EXISTS(SELECT 1 FROM projects WHERE project_id = :projectId)")
    suspend fun projectExists(projectId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project)

    @Update
    suspend fun updateProject(project: Project)

    @Query("""
        UPDATE projects
        SET build_status = :status, last_built_at = :lastBuiltAt, updated_at = :updatedAt
        WHERE project_id = :projectId AND owner_key = :ownerKey
    """)
    suspend fun updateBuildStatus(
        projectId: String,
        ownerKey: String,
        status: ProjectBuildStatus,
        lastBuiltAt: Long?,
        updatedAt: Long,
    )

    @Query("UPDATE projects SET name = :name, updated_at = :updatedAt WHERE project_id = :projectId AND owner_key = :ownerKey")
    suspend fun updateName(projectId: String, ownerKey: String, name: String, updatedAt: Long)

    @Query("""
        UPDATE projects
        SET owner_key = :ownerKey,
            github_repository_id = :repositoryId,
            github_repository_full_name = :repositoryFullName,
            github_branch = :branch,
            updated_at = :updatedAt
        WHERE project_id = :projectId
          AND (owner_key = :ownerKey OR owner_key = :legacyOwnerKey)
    """)
    suspend fun linkGitHubRepository(
        projectId: String,
        ownerKey: String,
        legacyOwnerKey: String,
        repositoryId: Long,
        repositoryFullName: String,
        branch: String,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM projects WHERE project_id = :projectId AND owner_key = :ownerKey")
    suspend fun deleteProject(projectId: String, ownerKey: String)

    @Query("SELECT * FROM projects WHERE owner_key = :ownerKey AND name LIKE '%' || :query || '%' ORDER BY created_at DESC")
    suspend fun searchProjects(query: String, ownerKey: String): List<Project>
}
