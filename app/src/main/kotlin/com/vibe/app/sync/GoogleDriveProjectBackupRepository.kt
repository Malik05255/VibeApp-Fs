package com.vibe.app.sync

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.http.FileContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.vibe.app.R
import com.vibe.app.data.database.entity.Project
import com.vibe.app.data.preferences.AppText
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.File as LocalFile
import java.util.Collections
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class DriveProjectManifest(
    val projectId: String,
    val name: String,
    val updatedAt: Long,
    val createdAt: Long,
    val backupVersion: Int = 1,
)

data class DriveBackupItem(
    val fileId: String,
    val projectId: String,
    val name: String,
    val updatedAt: Long,
)

@Singleton
class GoogleDriveProjectBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun drive(): Drive {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: error(AppText.get(R.string.drive_sign_in_required))
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            Collections.singleton(DriveScopes.DRIVE_FILE),
        ).apply {
            selectedAccount = account.account ?: error(AppText.get(R.string.drive_account_unavailable))
        }
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("lm_AI")
            .build()
    }

    fun listBackups(): List<DriveBackupItem> {
        val service = drive()
        val result = service.files().list()
            .setQ("trashed = false and appProperties has { key='lm_ai_backup' and value='1' }")
            .setSpaces("drive")
            .setFields("files(id,name,modifiedTime,appProperties)")
            .execute()
        return result.files.orEmpty().mapNotNull { file ->
            val props = file.appProperties.orEmpty()
            val projectId = props["project_id"] ?: return@mapNotNull null
            val updatedAt = props["updated_at"]?.toLongOrNull() ?: 0L
            DriveBackupItem(
                fileId = file.id,
                projectId = projectId,
                name = props["project_name"] ?: file.name.removeSuffix(".zip"),
                updatedAt = updatedAt,
            )
        }.sortedByDescending { it.updatedAt }
    }

    fun uploadProject(project: Project): DriveBackupItem {
        val service = drive()
        val backup = createBackupZip(project)
        val existing = listBackups().firstOrNull { it.projectId == project.projectId }
        val metadata = File().apply {
            name = "lm_AI_${project.projectId}.zip"
            mimeType = "application/zip"
            appProperties = mapOf(
                "lm_ai_backup" to "1",
                "project_id" to project.projectId,
                "project_name" to project.name,
                "updated_at" to project.updatedAt.toString(),
            )
        }
        val media = FileContent("application/zip", backup)
        val uploaded = if (existing == null) {
            service.files().create(metadata, media).setFields("id,name,appProperties").execute()
        } else {
            service.files().update(existing.fileId, metadata, media).setFields("id,name,appProperties").execute()
        }
        backup.delete()
        return DriveBackupItem(uploaded.id, project.projectId, project.name, project.updatedAt)
    }

    fun restoreProject(item: DriveBackupItem, destinationRoot: LocalFile): DriveProjectManifest {
        val service = drive()
        val temp = LocalFile.createTempFile("lm_ai_restore_", ".zip", context.cacheDir)
        FileOutputStream(temp).use { output -> service.files().get(item.fileId).executeMediaAndDownloadTo(output) }
        val target = LocalFile(destinationRoot, item.projectId)
        if (target.exists()) target.deleteRecursively()
        target.mkdirs()
        var manifest: DriveProjectManifest? = null
        ZipInputStream(FileInputStream(temp)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val out = LocalFile(target, entry.name)
                val canonicalRoot = target.canonicalFile
                val canonicalOut = out.canonicalFile
                require(canonicalOut.path.startsWith(canonicalRoot.path + LocalFile.separator) || canonicalOut == canonicalRoot) {
                    "Invalid backup entry"
                }
                if (entry.isDirectory) {
                    canonicalOut.mkdirs()
                } else {
                    canonicalOut.parentFile?.mkdirs()
                    FileOutputStream(canonicalOut).use { zip.copyTo(it) }
                    if (entry.name == "lm_ai_manifest.json") {
                        manifest = json.decodeFromString(DriveProjectManifest.serializer(), canonicalOut.readText())
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        temp.delete()
        return manifest ?: error(AppText.get(R.string.drive_invalid_backup))
    }

    private fun createBackupZip(project: Project): LocalFile {
        val source = LocalFile(project.workspacePath)
        require(source.exists()) { AppText.get(R.string.drive_missing_project_path, project.name) }
        val zipFile = LocalFile.createTempFile("lm_ai_${project.projectId}_", ".zip", context.cacheDir)
        val manifest = DriveProjectManifest(project.projectId, project.name, project.updatedAt, project.createdAt)
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(ZipEntry("lm_ai_manifest.json"))
            zip.write(json.encodeToString(manifest).toByteArray())
            zip.closeEntry()
            if (source.isDirectory) {
                source.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relative = file.relativeTo(source).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry("workspace/$relative"))
                    FileInputStream(file).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            } else {
                zip.putNextEntry(ZipEntry("workspace/${source.name}"))
                FileInputStream(source).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return zipFile
    }
}
