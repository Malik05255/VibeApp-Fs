package com.malik.lmai.project.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val data: String,
    val images: String?,
    val createdAt: Long,
    val updatedAt: Long
)
