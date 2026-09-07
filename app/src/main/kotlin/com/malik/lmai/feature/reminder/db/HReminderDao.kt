package com.malik.lmai.feature.reminder.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HReminderDao {
    @Query(
        """
        SELECT * FROM h_reminders
        WHERE ownerKey = :ownerKey AND domain = 'PERSONAL'
        ORDER BY
          CASE status WHEN 'ACTIVE' THEN 0 WHEN 'DEFERRED' THEN 1 WHEN 'DISABLED' THEN 2 ELSE 3 END,
          CASE WHEN scheduledAtMs IS NULL THEN 1 ELSE 0 END,
          scheduledAtMs ASC,
          updatedAtMs DESC
        """
    )
    fun observePersonal(ownerKey: String): Flow<List<HReminderEntity>>

    @Query("SELECT * FROM h_reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): HReminderEntity?

    @Query("SELECT * FROM h_reminders WHERE ownerKey = :ownerKey ORDER BY updatedAtMs DESC")
    suspend fun getAllForOwner(ownerKey: String): List<HReminderEntity>

    @Upsert
    suspend fun upsert(entity: HReminderEntity)

    @Delete
    suspend fun delete(entity: HReminderEntity)

    @Query("DELETE FROM h_reminders WHERE id = :id AND ownerKey = :ownerKey")
    suspend fun deleteById(ownerKey: String, id: String)

    @Query(
        """
        UPDATE h_reminders
        SET status = :status, updatedAtMs = :updatedAtMs, completedAtMs = :completedAtMs
        WHERE id = :id AND ownerKey = :ownerKey
        """
    )
    suspend fun updateStatus(
        ownerKey: String,
        id: String,
        status: String,
        updatedAtMs: Long,
        completedAtMs: Long?,
    )
}
