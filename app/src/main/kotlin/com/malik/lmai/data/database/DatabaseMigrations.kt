package com.malik.lmai.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS projects (
                    id TEXT NOT NULL PRIMARY KEY,
                    userId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    data TEXT NOT NULL,
                    images TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }
}
