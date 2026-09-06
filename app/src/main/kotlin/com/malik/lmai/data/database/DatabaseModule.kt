package com.malik.lmai.data.database

import android.content.Context
import androidx.room.Room

object DatabaseModule {
    @Volatile
    private var INSTANCE: LmaiAppDatabase? = null

    fun provideDatabase(context: Context): LmaiAppDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                LmaiAppDatabase::class.java,
                "lmai_database"
            )
                .addMigrations(DatabaseMigrations.MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { INSTANCE = it }
        }
    }
}
