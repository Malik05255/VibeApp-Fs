package com.vibe.app.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vibe.app.auth.database.UserEntity

@Database(
    entities = [
        UserEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class VibeAppDatabase : RoomDatabase() {
    abstract fun userDao(): com.vibe.app.auth.database.UserDao
}
