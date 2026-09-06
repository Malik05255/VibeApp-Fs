package com.malik.lmai.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.malik.lmai.data.database.ChatDatabaseV2
import com.malik.lmai.data.database.dao.ChatPlatformModelV2Dao
import com.malik.lmai.data.database.dao.ChatRoomV2Dao
import com.malik.lmai.data.database.dao.MessageV2Dao
import com.malik.lmai.data.database.dao.PlatformV2Dao
import com.malik.lmai.data.database.dao.ProjectDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DB_NAME_V2 = "chat_v2"

    private val MIGRATION_CHAT_DB_V2_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE projects ADD COLUMN github_repository_id INTEGER")
            db.execSQL("ALTER TABLE projects ADD COLUMN github_repository_full_name TEXT")
            db.execSQL("ALTER TABLE projects ADD COLUMN github_branch TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_projects_github_repository_full_name ON projects(github_repository_full_name)")
        }
    }

    private val MIGRATION_CHAT_DB_V2_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE projects ADD COLUMN owner_key TEXT NOT NULL DEFAULT 'local'")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_projects_owner_key ON projects(owner_key)")
        }
    }

    private val MIGRATION_CHAT_DB_V2_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `projects` (
                    `project_id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `chat_id` INTEGER NOT NULL,
                    `workspace_path` TEXT NOT NULL,
                    `build_status` TEXT NOT NULL,
                    `last_built_at` INTEGER,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`project_id`),
                    FOREIGN KEY(`chat_id`) REFERENCES `chats_v2`(`chat_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_CHAT_DB_V2_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chat_platform_model_v2` (
                    `chat_id` INTEGER NOT NULL,
                    `platform_uid` TEXT NOT NULL,
                    `model` TEXT NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`chat_id`, `platform_uid`),
                    FOREIGN KEY(`chat_id`) REFERENCES `chats_v2`(`chat_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )

            val platformModelMap = mutableMapOf<String, String>()
            db.query("SELECT uid, model FROM platform_v2").use { platformCursor ->
                val uidIndex = platformCursor.getColumnIndexOrThrow("uid")
                val modelIndex = platformCursor.getColumnIndexOrThrow("model")
                while (platformCursor.moveToNext()) {
                    val uid = platformCursor.getString(uidIndex)
                    val model = platformCursor.getString(modelIndex) ?: ""
                    platformModelMap[uid] = model
                }
            }

            val currentTimestamp = System.currentTimeMillis() / 1000
            db.query("SELECT chat_id, enabled_platform FROM chats_v2").use { chatCursor ->
                val chatIdIndex = chatCursor.getColumnIndexOrThrow("chat_id")
                val enabledPlatformIndex = chatCursor.getColumnIndexOrThrow("enabled_platform")
                while (chatCursor.moveToNext()) {
                    val chatId = chatCursor.getInt(chatIdIndex)
                    val enabledPlatform = chatCursor.getString(enabledPlatformIndex) ?: ""
                    if (enabledPlatform.isBlank()) continue

                    enabledPlatform
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .forEach { platformUid ->
                            val model = platformModelMap[platformUid] ?: ""
                            db.execSQL(
                                "INSERT OR REPLACE INTO chat_platform_model_v2 (chat_id, platform_uid, model, updated_at) VALUES (?, ?, ?, ?)",
                                arrayOf<Any>(chatId, platformUid, model, currentTimestamp)
                            )
                        }
                }
            }
        }
    }

    @Provides
    fun provideProjectDao(chatDatabaseV2: ChatDatabaseV2): ProjectDao = chatDatabaseV2.projectDao()

    @Provides
    fun provideChatPlatformModelV2Dao(chatDatabaseV2: ChatDatabaseV2): ChatPlatformModelV2Dao = chatDatabaseV2.chatPlatformModelDao()

    @Provides
    fun providePlatformV2Dao(chatDatabaseV2: ChatDatabaseV2): PlatformV2Dao = chatDatabaseV2.platformDao()

    @Provides
    fun provideChatRoomV2Dao(chatDatabaseV2: ChatDatabaseV2): ChatRoomV2Dao = chatDatabaseV2.chatRoomDao()

    @Provides
    fun provideMessageV2Dao(chatDatabaseV2: ChatDatabaseV2): MessageV2Dao = chatDatabaseV2.messageDao()

    @Provides
    @Singleton
    fun provideChatDatabaseV2(@ApplicationContext appContext: Context): ChatDatabaseV2 = Room.databaseBuilder(
        appContext,
        ChatDatabaseV2::class.java,
        DB_NAME_V2
    ).addMigrations(
        MIGRATION_CHAT_DB_V2_1_2,
        MIGRATION_CHAT_DB_V2_2_3,
        MIGRATION_CHAT_DB_V2_3_4,
        MIGRATION_CHAT_DB_V2_4_5,
    ).build()
}
