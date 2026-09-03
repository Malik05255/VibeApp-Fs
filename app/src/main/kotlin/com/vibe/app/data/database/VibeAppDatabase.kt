package com.vibe.app.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vibe.app.auth.database.UserEntity
import com.vibe.app.project.database.ProjectDao
import com.vibe.app.project.database.ProjectEntity

@Database(
    entities = [
        UserEntity::class,
        ProjectEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class VibeAppDatabase : RoomDatabase() {
    abstract fun userDao(): com.vibe.app.auth.database.UserDao
    abstract fun projectDao(): ProjectDao
}
