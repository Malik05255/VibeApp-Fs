package com.malik.lmai.feature.project.memo

import com.malik.lmai.feature.project.LmaiProjectDirs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface IntentStore {
    suspend fun exists(vibeDirs: LmaiProjectDirs): Boolean
    suspend fun load(vibeDirs: LmaiProjectDirs): Intent?
    suspend fun save(vibeDirs: LmaiProjectDirs, intent: Intent, appName: String)
    suspend fun loadRawMarkdown(vibeDirs: LmaiProjectDirs): String?
    suspend fun saveRawMarkdown(vibeDirs: LmaiProjectDirs, markdown: String)
}

@Singleton
class DefaultIntentStore @Inject constructor() : IntentStore {

    override suspend fun exists(vibeDirs: LmaiProjectDirs): Boolean = withContext(Dispatchers.IO) {
        vibeDirs.intentFile.exists() && vibeDirs.intentFile.length() > 0
    }

    override suspend fun load(vibeDirs: LmaiProjectDirs): Intent? = withContext(Dispatchers.IO) {
        if (!vibeDirs.intentFile.exists()) null
        else IntentMarkdownCodec.decode(vibeDirs.intentFile.readText())
    }

    override suspend fun save(vibeDirs: LmaiProjectDirs, intent: Intent, appName: String) =
        withContext(Dispatchers.IO) {
            vibeDirs.ensureCreated()
            vibeDirs.intentFile.writeText(IntentMarkdownCodec.encode(intent, appName))
        }

    override suspend fun loadRawMarkdown(vibeDirs: LmaiProjectDirs): String? =
        withContext(Dispatchers.IO) {
            if (vibeDirs.intentFile.exists()) vibeDirs.intentFile.readText() else null
        }

    override suspend fun saveRawMarkdown(vibeDirs: LmaiProjectDirs, markdown: String) =
        withContext(Dispatchers.IO) {
            vibeDirs.ensureCreated()
            vibeDirs.intentFile.writeText(markdown)
        }
}
