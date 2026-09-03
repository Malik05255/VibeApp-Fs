package com.vibe.app.project.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity)

    @Query("SELECT * FROM projects WHERE userId = :userId")
    suspend fun getByUserId(userId: String): List<ProjectEntity>

    @Delete
    suspend fun delete(project: ProjectEntity)
}
