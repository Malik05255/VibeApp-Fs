package com.malik.lmai.feature.reminder.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [HReminderEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class HReminderDatabase : RoomDatabase() {
    abstract fun reminderDao(): HReminderDao

    companion object {
        @Volatile private var instance: HReminderDatabase? = null

        fun get(context: Context): HReminderDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                HReminderDatabase::class.java,
                "h_personal_reminders.db",
            ).build().also { instance = it }
        }
    }
}
