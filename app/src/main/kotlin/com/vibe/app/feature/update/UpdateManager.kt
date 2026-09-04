package com.vibe.app.feature.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.vibe.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(): UpdateManifest? = withContext(Dispatchers.IO) {
        val releaseResponse = client.get(LATEST_RELEASE_API)
        if (!releaseResponse.status.isSuccess()) return@withContext null
        val release = json.decodeFromString<GitHubLatestRelease>(releaseResponse.body())
        val manifestAsset = release.assets.firstOrNull { it.name == MANIFEST_ASSET } ?: return@withContext null
        val manifestResponse = client.get(manifestAsset.browserDownloadUrl)
        if (!manifestResponse.status.isSuccess()) return@withContext null
        val manifest = json.decodeFromString<UpdateManifest>(manifestResponse.body())
        manifest.takeIf {
            it.mandatory && (BuildConfig.VERSION_CODE < it.minimumVersionCode || BuildConfig.VERSION_CODE < it.versionCode)
        }
    }

    suspend fun downloadAndVerify(
        manifest: UpdateManifest,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val releaseResponse = client.get(LATEST_RELEASE_API)
        check(releaseResponse.status.isSuccess()) { "تعذر قراءة الإصدار الأخير" }
        val release = json.decodeFromString<GitHubLatestRelease>(releaseResponse.body())
        val apkUrl = release.assets.firstOrNull { it.name == manifest.apkAsset }?.browserDownloadUrl
            ?: error("تعذر العثور على ملف التحديث")

        val response = client.get(apkUrl)
        check(response.status.isSuccess()) { "تعذر تنزيل التحديث" }
        val total = response.headers["Content-Length"]?.toLongOrNull()?.coerceAtLeast(1L)
        val target = File(context.filesDir, "updates/lm_AI-${manifest.versionCode}.apk")
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()

        val digest = MessageDigest.getInstance("SHA-256")
        val channel = response.bodyAsChannel()
        var readTotal = 0L
        val buffer = ByteArray(64 * 1024)
        FileOutputStream(target).use { out ->
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read <= 0) continue
                out.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                readTotal += read
                if (total != null) onProgress(((readTotal * 100) / total).toInt().coerceIn(0, 100))
            }
            out.fd.sync()
        }

        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual.equals(manifest.sha256.trim(), ignoreCase = true)) {
            target.delete()
            "فشل التحقق من سلامة ملف التحديث"
        }
        onProgress(100)
        target
    }

    fun openInstaller(apk: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    @Serializable
    private data class GitHubLatestRelease(
        val assets: List<GitHubReleaseAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubReleaseAsset(
        val name: String,
        @kotlinx.serialization.SerialName("browser_download_url")
        val browserDownloadUrl: String,
    )

    companion object {
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/Malik05255/VibeApp-Fs/releases/latest"
        private const val MANIFEST_ASSET = "update-manifest.json"
    }
}
