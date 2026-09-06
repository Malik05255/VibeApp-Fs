package com.malik.lmai.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.malik.lmai.auth.database.UserEntity
import com.malik.lmai.project.database.ProjectDao
import com.malik.lmai.project.database.ProjectEntity

@Database(
    entities = [
        UserEntity::class,
        ProjectEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class LmaiAppDatabase : RoomDatabase() {
    abstract fun userDao(): com.malik.lmai.auth.database.UserDao
    abstract fun projectDao(): ProjectDao
}
