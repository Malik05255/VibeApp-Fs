package com.almi.ai.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.almi.ai.BuildConfig
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class AlmiPatchSpec(
    val fromVersionCode: Int,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
)

internal data class AlmiRollbackSpec(
    val fromVersionCode: Int,
    val targetVersionCode: Int,
    val targetLabel: String,
    val patchUrl: String,
    val patchSha256: String,
    val patchSizeBytes: Long,
    val targetApkSha256: String,
)

internal data class AlmiRelease(
    val releaseId: String,
    val versionCode: Int,
    val versionName: String,
    val mandatory: Boolean,
    val titleAr: String,
    val titleEn: String,
    val notesAr: String,
    val notesEn: String,
    val targetApkSha256: String,
    val patches: List<AlmiPatchSpec>,
    val rollback: AlmiRollbackSpec?,
) {
    fun patchForCurrent(): AlmiPatchSpec? = patches.firstOrNull { it.fromVersionCode == BuildConfig.VERSION_CODE }
}

internal sealed interface AlmiUpdateState {
    data object Idle : AlmiUpdateState
    data object Checking : AlmiUpdateState
    data object Current : AlmiUpdateState
    data class Available(
        val release: AlmiRelease,
        val mandatory: Boolean,
        val skipAllowed: Boolean,
        val manualCheck: Boolean,
    ) : AlmiUpdateState
    data class Downloading(val release: AlmiRelease, val progress: Float, val rollback: Boolean) : AlmiUpdateState
    data class ReadyToInstall(val release: AlmiRelease, val rollback: Boolean) : AlmiUpdateState
    data class Message(val textAr: String, val textEn: String, val blocking: Boolean = false) : AlmiUpdateState
}

/**
 * Latest-only self-update controller.
 *
 * The endpoint exposes one release: the newest release only. Older pending cards therefore disappear
 * automatically as soon as the endpoint advances. A rollback skip exemption is scoped to exactly the
 * release that was rolled back; a newer release never inherits that exemption.
 *
 * ALMI downloads only a .alpatch matching the exact installed version. There is deliberately no
 * full-APK network fallback. The signed target APK is reconstructed locally from the installed APK,
 * SHA-256 verified, certificate verified, and then handed to Android Package Installer.
 */
internal class AlmiUpdateManager(private val context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<AlmiUpdateState>(AlmiUpdateState.Idle)
    val state: StateFlow<AlmiUpdateState> = _state.asStateFlow()

    init {
        finalizePendingRollbackIfInstalled()
    }

    suspend fun check(manual: Boolean = false) {
        _state.value = AlmiUpdateState.Checking
        runCatching { fetchLatestRelease() }
            .onSuccess { release ->
                if (release.versionCode <= BuildConfig.VERSION_CODE) {
                    clearStaleRollbackExemptionIfNeeded(release.releaseId)
                    _state.value = if (manual) {
                        AlmiUpdateState.Message("أنت تستخدم أحدث إصدار.", "You are using the latest version.")
                    } else {
                        AlmiUpdateState.Current
                    }
                    return@onSuccess
                }

                val patch = release.patchForCurrent()
                if (patch == null) {
                    _state.value = AlmiUpdateState.Message(
                        "يوجد تحديث أحدث، لكن لا توجد حزمة فرق آمنة لهذا الإصدار بعد. لن يتم تنزيل التطبيق كاملًا.",
                        "A newer release exists, but no safe delta package exists for this build yet. ALMI will not download the full app.",
                        blocking = release.mandatory && !skipAllowedFor(release),
                    )
                    return@onSuccess
                }

                val skipAllowed = skipAllowedFor(release)
                clearStaleRollbackExemptionIfNeeded(release.releaseId)
                _state.value = AlmiUpdateState.Available(
                    release = release,
                    mandatory = release.mandatory && !skipAllowed,
                    skipAllowed = skipAllowed,
                    manualCheck = manual,
                )
            }
            .onFailure { failure ->
                val cached = readCachedRelease()
                if (cached != null && cached.versionCode > BuildConfig.VERSION_CODE) {
                    val skipAllowed = skipAllowedFor(cached)
                    _state.value = AlmiUpdateState.Available(
                        release = cached,
                        mandatory = cached.mandatory && !skipAllowed,
                        skipAllowed = skipAllowed,
                        manualCheck = manual,
                    )
                } else {
                    _state.value = if (manual) {
                        AlmiUpdateState.Message(
                            "تعذر التحقق من التحديثات: ${failure.message.orEmpty()}",
                            "Could not check for updates: ${failure.message.orEmpty()}",
                        )
                    } else {
                        AlmiUpdateState.Current
                    }
                }
            }
    }

    suspend fun installLatest(release: AlmiRelease) {
        val patch = release.patchForCurrent()
        if (patch == null) {
            _state.value = AlmiUpdateState.Message(
                "لا توجد حزمة فرق لهذا الإصدار. لم يتم تنزيل APK كامل.",
                "No delta package exists for this build. No full APK was downloaded.",
                blocking = release.mandatory && !skipAllowedFor(release),
            )
            return
        }
        installPatch(
            release = release,
            patchUrl = patch.url,
            patchSha256 = patch.sha256,
            expectedTargetSha256 = release.targetApkSha256,
            rollback = false,
            rollbackTargetVersionCode = null,
        )
    }

    suspend fun rollbackLatest(release: AlmiRelease) {
        val rollback = release.rollback
        if (rollback == null || rollback.fromVersionCode != BuildConfig.VERSION_CODE) {
            _state.value = AlmiUpdateState.Message(
                "لا توجد نقطة تراجع متوافقة مع الإصدار المثبت.",
                "No rollback point is compatible with the installed build.",
            )
            return
        }
        prefs.edit()
            .putString(KEY_PENDING_ROLLBACK_RELEASE, release.releaseId)
            .putInt(KEY_PENDING_ROLLBACK_TARGET, rollback.targetVersionCode)
            .apply()
        installPatch(
            release = release,
            patchUrl = rollback.patchUrl,
            patchSha256 = rollback.patchSha256,
            expectedTargetSha256 = rollback.targetApkSha256,
            rollback = true,
            rollbackTargetVersionCode = rollback.targetVersionCode,
        )
    }

    fun skipCurrentRelease(release: AlmiRelease) {
        if (!skipAllowedFor(release)) return
        _state.value = AlmiUpdateState.Current
    }

    fun dismissNonBlocking() {
        val current = _state.value
        if (current is AlmiUpdateState.Available && current.mandatory) return
        if (current is AlmiUpdateState.Message && current.blocking) return
        _state.value = AlmiUpdateState.Current
    }

    fun rollbackAvailable(release: AlmiRelease?): Boolean =
        release?.rollback?.fromVersionCode == BuildConfig.VERSION_CODE

    private suspend fun installPatch(
        release: AlmiRelease,
        patchUrl: String,
        patchSha256: String,
        expectedTargetSha256: String,
        rollback: Boolean,
        rollbackTargetVersionCode: Int?,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val updateDir = File(app.cacheDir, UPDATE_DIR).apply { mkdirs() }
            val patchFile = File(updateDir, if (rollback) "rollback.alpatch" else "latest.alpatch")
            val targetApk = File(updateDir, if (rollback) "ALMI_rollback.apk" else "ALMI_update.apk")
            patchFile.delete()
            targetApk.delete()

            downloadFile(patchUrl, patchFile) { fraction ->
                _state.value = AlmiUpdateState.Downloading(release, fraction * .55f, rollback)
            }
            check(AlmiDeltaPatch.sha256(patchFile).equals(patchSha256, ignoreCase = true)) {
                "Delta package SHA-256 mismatch"
            }

            val installedApk = File(app.applicationInfo.sourceDir)
            AlmiDeltaPatch.apply(installedApk, patchFile, targetApk) { fraction ->
                _state.value = AlmiUpdateState.Downloading(release, .55f + fraction * .45f, rollback)
            }
            check(AlmiDeltaPatch.sha256(targetApk).equals(expectedTargetSha256, ignoreCase = true)) {
                "Reconstructed APK SHA-256 mismatch"
            }
            verifySigningCertificate(targetApk)
            if (rollback && rollbackTargetVersionCode != null) {
                prefs.edit().putInt(KEY_PENDING_ROLLBACK_TARGET, rollbackTargetVersionCode).apply()
            }
            _state.value = AlmiUpdateState.ReadyToInstall(release, rollback)
            launchPackageInstaller(targetApk)
        }.onFailure { failure ->
            if (rollback) {
                prefs.edit().remove(KEY_PENDING_ROLLBACK_RELEASE).remove(KEY_PENDING_ROLLBACK_TARGET).apply()
            }
            _state.value = AlmiUpdateState.Message(
                "فشل تجهيز التحديث بأمان: ${failure.message.orEmpty()}",
                "Could not safely prepare the update: ${failure.message.orEmpty()}",
                blocking = !rollback && release.mandatory && !skipAllowedFor(release),
            )
        }
    }

    private suspend fun fetchLatestRelease(): AlmiRelease = withContext(Dispatchers.IO) {
        val connection = (URL(BuildConfig.ALMI_UPDATE_MANIFEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "ALMI/${BuildConfig.VERSION_NAME}")
        }
        try {
            check(connection.responseCode in 200..299) { "Update server HTTP ${connection.responseCode}" }
            val raw = connection.inputStream.bufferedReader().use { it.readText() }
            val release = parseRelease(raw)
            prefs.edit().putString(KEY_CACHED_RELEASE, raw).apply()
            release
        } finally {
            connection.disconnect()
        }
    }

    private fun readCachedRelease(): AlmiRelease? = prefs.getString(KEY_CACHED_RELEASE, null)
        ?.let { runCatching { parseRelease(it) }.getOrNull() }

    private fun parseRelease(raw: String): AlmiRelease {
        val root = JSONObject(raw)
        check(root.optInt("schema", 0) == 1) { "Unsupported update manifest schema" }
        val patchesJson = root.getJSONArray("patches")
        val patches = buildList {
            for (index in 0 until patchesJson.length()) {
                val item = patchesJson.getJSONObject(index)
                add(
                    AlmiPatchSpec(
                        fromVersionCode = item.getInt("fromVersionCode"),
                        url = item.getString("url"),
                        sha256 = item.getString("sha256"),
                        sizeBytes = item.getLong("sizeBytes"),
                    )
                )
            }
        }
        val rollback = root.optJSONObject("rollback")?.let { item ->
            AlmiRollbackSpec(
                fromVersionCode = item.getInt("fromVersionCode"),
                targetVersionCode = item.getInt("targetVersionCode"),
                targetLabel = item.getString("targetLabel"),
                patchUrl = item.getString("patchUrl"),
                patchSha256 = item.getString("patchSha256"),
                patchSizeBytes = item.getLong("patchSizeBytes"),
                targetApkSha256 = item.getString("targetApkSha256"),
            )
        }
        return AlmiRelease(
            releaseId = root.getString("releaseId"),
            versionCode = root.getInt("versionCode"),
            versionName = root.getString("versionName"),
            mandatory = root.optBoolean("mandatory", true),
            titleAr = root.optString("titleAr", "تحديث جديد"),
            titleEn = root.optString("titleEn", "New update"),
            notesAr = root.optString("notesAr", "يتوفر إصدار أحدث من ALMI."),
            notesEn = root.optString("notesEn", "A newer ALMI release is available."),
            targetApkSha256 = root.getString("targetApkSha256"),
            patches = patches,
            rollback = rollback,
        )
    }

    private fun skipAllowedFor(release: AlmiRelease): Boolean =
        prefs.getString(KEY_ROLLED_BACK_RELEASE, null) == release.releaseId

    private fun clearStaleRollbackExemptionIfNeeded(latestReleaseId: String) {
        val rolledBack = prefs.getString(KEY_ROLLED_BACK_RELEASE, null) ?: return
        if (rolledBack != latestReleaseId) {
            prefs.edit().remove(KEY_ROLLED_BACK_RELEASE).apply()
        }
    }

    private fun finalizePendingRollbackIfInstalled() {
        val pendingRelease = prefs.getString(KEY_PENDING_ROLLBACK_RELEASE, null) ?: return
        val target = prefs.getInt(KEY_PENDING_ROLLBACK_TARGET, -1)
        if (target == BuildConfig.VERSION_CODE) {
            prefs.edit()
                .putString(KEY_ROLLED_BACK_RELEASE, pendingRelease)
                .remove(KEY_PENDING_ROLLBACK_RELEASE)
                .remove(KEY_PENDING_ROLLBACK_TARGET)
                .apply()
        }
    }

    private fun downloadFile(url: String, target: File, onProgress: (Float) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ALMI-Updater/${BuildConfig.VERSION_NAME}")
        }
        try {
            check(connection.responseCode in 200..299) { "Patch server HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong.coerceAtLeast(1L)
            BufferedInputStream(connection.inputStream, 256 * 1024).use { input ->
                FileOutputStream(target).buffered(256 * 1024).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var received = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        output.write(buffer, 0, count)
                        received += count
                        onProgress((received.toDouble() / total).toFloat().coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun verifySigningCertificate(apk: File) {
        val info = if (android.os.Build.VERSION.SDK_INT >= 28) {
            app.packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            app.packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
        } ?: error("Android could not inspect reconstructed APK")

        val signatures = if (android.os.Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            info.signatures?.toList().orEmpty()
        }
        check(signatures.isNotEmpty()) { "Reconstructed APK has no signing certificate" }
        val digest = MessageDigest.getInstance("SHA-256").digest(signatures.first().toByteArray())
            .joinToString("") { "%02x".format(it) }
        check(digest.equals(BuildConfig.ALMI_RELEASE_CERT_SHA256, ignoreCase = true)) {
            "Reconstructed APK signing certificate mismatch"
        }
    }

    private fun launchPackageInstaller(apk: File) {
        if (android.os.Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
            _state.value = AlmiUpdateState.Message(
                "اسمح لـ ALMI بتثبيت التحديثات، ثم اضغط تحديث مرة أخرى.",
                "Allow ALMI to install updates, then tap Update again.",
                blocking = (_state.value as? AlmiUpdateState.ReadyToInstall)?.rollback != true,
            )
            return
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        app.startActivity(intent)
    }

    companion object {
        private const val PREFS = "almi_update_state_v1"
        private const val KEY_CACHED_RELEASE = "cached_release"
        private const val KEY_PENDING_ROLLBACK_RELEASE = "pending_rollback_release"
        private const val KEY_PENDING_ROLLBACK_TARGET = "pending_rollback_target"
        private const val KEY_ROLLED_BACK_RELEASE = "rolled_back_release"
        private const val UPDATE_DIR = "almi_updates"
    }
}
