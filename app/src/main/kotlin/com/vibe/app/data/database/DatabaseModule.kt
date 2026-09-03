package com.vibe.app.data.database

import android.content.Context
import androidx.room.Room

object DatabaseModule {
    @Volatile
    private var INSTANCE: VibeAppDatabase? = null

    fun provideDatabase(context: Context): VibeAppDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                VibeAppDatabase::class.java,
                "vibeapp_database"
            ).build().also { INSTANCE = it }
        }
    }
}
