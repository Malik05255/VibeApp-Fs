package com.malik.lmai.feature.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.malik.lmai.BuildConfig
import com.malik.lmai.R
import com.malik.lmai.data.preferences.AppText
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileInputStream
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
        val release = fetchLatestRelease() ?: return@withContext null
        val manifestAsset = release.assets.firstOrNull { it.name == MANIFEST_ASSET }
            ?: return@withContext null
        val manifestResponse = client.get(manifestAsset.browserDownloadUrl) {
            commonDownloadHeaders()
        }
        if (!manifestResponse.status.isSuccess()) return@withContext null

        val manifest = json.decodeFromString<UpdateManifest>(manifestResponse.body())
        if (BuildConfig.VERSION_CODE >= manifest.versionCode) return@withContext null

        val exactApkUrl = manifest.apkUrl.trim().ifBlank {
            release.assets.firstOrNull { it.name == manifest.apkAsset }
                ?.browserDownloadUrl
                .orEmpty()
        }
        if (exactApkUrl.isBlank()) return@withContext null

        manifest.copy(apkUrl = exactApkUrl)
    }

    suspend fun downloadAndVerify(
        manifest: UpdateManifest,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val target = File(context.filesDir, "updates/lm_AI-${manifest.versionCode}.apk")
        target.parentFile?.mkdirs()

        if (target.exists()) {
            val cachedHash = sha256(target)
            if (cachedHash.equals(manifest.sha256.trim(), ignoreCase = true)) {
                onProgress(100)
                return@withContext target
            }
            target.delete()
        }

        val apkUrl = manifest.apkUrl.trim().ifBlank {
            resolveApkUrl(manifest.apkAsset)
        }
        check(apkUrl.isNotBlank()) { AppText.get(R.string.update_asset_missing) }

        val response = client.get(apkUrl) {
            commonDownloadHeaders()
            header(HttpHeaders.Accept, "application/octet-stream")
        }
        check(response.status.isSuccess()) { AppText.get(R.string.update_download_failed) }

        val total = response.headers[HttpHeaders.ContentLength]
            ?.toLongOrNull()
            ?.coerceAtLeast(1L)
        val digest = MessageDigest.getInstance("SHA-256")
        val channel = response.bodyAsChannel()
        var readTotal = 0L
        val buffer = ByteArray(64 * 1024)

        onProgress(1)
        FileOutputStream(target).use { out ->
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read < 0) break
                if (read == 0) continue

                out.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                readTotal += read

                if (total != null) {
                    onProgress(((readTotal * 100) / total).toInt().coerceIn(1, 99))
                }
            }
            out.fd.sync()
        }

        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual.equals(manifest.sha256.trim(), ignoreCase = true)) {
            target.delete()
            AppText.get(R.string.update_integrity_failed)
        }

        onProgress(100)
        target
    }

    fun openInstaller(apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            return
        }

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
        }
        context.startActivity(intent)
    }

    private suspend fun resolveApkUrl(assetName: String): String {
        val release = fetchLatestRelease() ?: return ""
        return release.assets.firstOrNull { it.name == assetName }
            ?.browserDownloadUrl
            .orEmpty()
    }

    private suspend fun fetchLatestRelease(): GitHubLatestRelease? {
        val response = client.get(LATEST_RELEASE_API) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.CacheControl, "no-cache")
            header(HttpHeaders.UserAgent, USER_AGENT)
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        if (!response.status.isSuccess()) return null
        return json.decodeFromString(response.body())
    }

    private fun io.ktor.client.request.HttpRequestBuilder.commonDownloadHeaders() {
        header(HttpHeaders.CacheControl, "no-cache")
        header(HttpHeaders.UserAgent, USER_AGENT)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
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
            "https://api.github.com/repos/Malik05255/LmaiApp-Fs/releases/latest"
        private const val MANIFEST_ASSET = "update-manifest.json"
        private const val USER_AGENT = "lm_AI-Android"
    }
}
