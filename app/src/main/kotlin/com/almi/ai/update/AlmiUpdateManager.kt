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
    val toVersionCode: Int,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val targetApkSha256: String,
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
 * The endpoint exposes exactly one release: the newest release. There is no update history in the
 * app UI, so when the endpoint advances an older pending release disappears automatically.
 *
 * Rollback is intentionally special. Android requires the rollback APK to carry a higher package
 * versionCode even though its product content is older. ALMI therefore records the releaseId that
 * was rolled back. While that exact release remains latest, its update card is shown with Skip. As
 * soon as the endpoint moves to a different releaseId, that exemption is removed and only the new
 * release is shown. Each delta patch declares its own target versionCode/hash, allowing a rollback
 * build to re-apply the same logical release using a higher Android installation code.
 *
 * ALMI downloads only a .alpatch matching the exact installed version. There is deliberately no
 * full-APK network fallback. The signed target APK is reconstructed locally from the installed APK,
 * SHA-256 verified, certificate verified, and then handed to Android Package Installer.
 *
 * Mandatory enforcement belongs only to the automatic launch check. A manual check from Settings
 * always remains dismissible, exactly like a user-initiated "check for updates" action should be.
 */
internal class AlmiUpdateManager(private val context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<AlmiUpdateState>(AlmiUpdateState.Idle)
    val state: StateFlow<AlmiUpdateState> = _state.asStateFlow()

    init {
        finalizePendingRollbackIfInstalled()
        clearPreparedInstallIfAlreadyApplied()
    }

    suspend fun check(manual: Boolean = false) {
        _state.value = AlmiUpdateState.Checking
        runCatching { fetchLatestRelease() }
            .onSuccess { release -> applyLatestRelease(release, manual) }
            .onFailure { failure ->
                val cached = readCachedRelease()
                if (cached != null && shouldOffer(cached)) {
                    applyLatestRelease(cached, manual)
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

    private fun applyLatestRelease(release: AlmiRelease, manual: Boolean) {
        val skipAllowed = skipAllowedFor(release)
        if (!shouldOffer(release)) {
            clearStaleRollbackExemptionIfNeeded(release.releaseId)
            _state.value = if (manual) {
                AlmiUpdateState.Message("أنت تستخدم أحدث إصدار.", "You are using the latest version.")
            } else {
                AlmiUpdateState.Current
            }
            return
        }

        val patch = release.patchForCurrent()
        clearStaleRollbackExemptionIfNeeded(release.releaseId)
        val blocking = release.mandatory && !skipAllowed && !manual
        if (patch == null) {
            _state.value = AlmiUpdateState.Message(
                "يوجد تحديث أحدث، لكن لا توجد حزمة فرق آمنة لهذا الإصدار بعد. لن يتم تنزيل التطبيق كاملًا.",
                "A newer release exists, but no safe delta package exists for this build yet. ALMI will not download the full app.",
                blocking = blocking,
            )
            return
        }

        _state.value = AlmiUpdateState.Available(
            release = release,
            mandatory = blocking,
            skipAllowed = skipAllowed,
            manualCheck = manual,
        )
    }

    private fun shouldOffer(release: AlmiRelease): Boolean {
        val rolledBackRelease = prefs.getString(KEY_ROLLED_BACK_RELEASE, null)
        if (rolledBackRelease != null) return release.patchForCurrent() != null
        return release.versionCode > BuildConfig.VERSION_CODE
    }

    suspend fun installLatest(release: AlmiRelease) {
        val blockingAttempt = (_state.value as? AlmiUpdateState.Available)?.mandatory == true
        val patch = release.patchForCurrent()
        if (patch == null) {
            _state.value = AlmiUpdateState.Message(
                "لا توجد حزمة فرق لهذا الإصدار. لم يتم تنزيل APK كامل.",
                "No delta package exists for this build. No full APK was downloaded.",
                blocking = blockingAttempt,
            )
            return
        }
        installPatch(
            release = release,
            patchUrl = patch.url,
            patchSha256 = patch.sha256,
            expectedTargetSha256 = patch.targetApkSha256,
            rollback = false,
            targetVersionCode = patch.toVersionCode,
            blockingUpdate = blockingAttempt,
        )
    }

    suspend fun rollbackPrevious() {
        _state.value = AlmiUpdateState.Checking
        val release = runCatching { fetchLatestRelease() }
            .getOrElse { failure ->
                _state.value = AlmiUpdateState.Message(
                    "تعذر التحقق من نقطة التراجع: ${failure.message.orEmpty()}",
                    "Could not verify rollback point: ${failure.message.orEmpty()}",
                )
                return
            }
        rollbackLatest(release)
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
            targetVersionCode = rollback.targetVersionCode,
            blockingUpdate = false,
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

    /** User-visible retry button: opens permission settings or the prepared Android installer. */
    fun resumePreparedInstall(): Boolean {
        val apk = preparedApk() ?: return false
        return runCatching {
            launchPackageInstaller(apk)
            true
        }.getOrDefault(false)
    }

    /** Activity.onResume hook. Continues exactly once after unknown-app permission was granted. */
    fun resumeAfterInstallPermission(): Boolean {
        if (!prefs.getBoolean(KEY_WAITING_INSTALL_PERMISSION, false)) return false
        if (android.os.Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) return false
        val apk = preparedApk() ?: run {
            prefs.edit().remove(KEY_WAITING_INSTALL_PERMISSION).apply()
            return false
        }
        prefs.edit().remove(KEY_WAITING_INSTALL_PERMISSION).apply()
        return runCatching {
            launchPackageInstaller(apk)
            true
        }.getOrDefault(false)
    }

    private fun preparedApk(): File? {
        val path = prefs.getString(KEY_PREPARED_APK_PATH, null) ?: return null
        val apk = File(path)
        if (!apk.isFile) {
            clearPreparedInstall()
            return null
        }
        return apk
    }

    private suspend fun installPatch(
        release: AlmiRelease,
        patchUrl: String,
        patchSha256: String,
        expectedTargetSha256: String,
        rollback: Boolean,
        targetVersionCode: Int,
        blockingUpdate: Boolean,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            check(targetVersionCode > BuildConfig.VERSION_CODE) {
                "Target Android versionCode must be greater than installed versionCode"
            }
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
            verifyArchiveVersionCode(targetApk, targetVersionCode)
            prefs.edit()
                .putString(KEY_PREPARED_APK_PATH, targetApk.absolutePath)
                .putInt(KEY_PREPARED_TARGET_CODE, targetVersionCode)
                .putBoolean(KEY_PREPARED_BLOCKING, blockingUpdate)
                .apply()
            _state.value = AlmiUpdateState.ReadyToInstall(release, rollback)
            launchPackageInstaller(targetApk)
        }.onFailure { failure ->
            clearPreparedInstall()
            if (rollback) {
                prefs.edit().remove(KEY_PENDING_ROLLBACK_RELEASE).remove(KEY_PENDING_ROLLBACK_TARGET).apply()
            }
            _state.value = AlmiUpdateState.Message(
                "فشل تجهيز التحديث بأمان: ${failure.message.orEmpty()}",
                "Could not safely prepare the update: ${failure.message.orEmpty()}",
                blocking = blockingUpdate,
            )
        }
    }

    private suspend fun fetchLatestRelease(): AlmiRelease = withContext(Dispatchers.IO) {
        val connection = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
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
                        toVersionCode = item.getInt("toVersionCode"),
                        url = item.getString("url"),
                        sha256 = item.getString("sha256"),
                        sizeBytes = item.getLong("sizeBytes"),
                        targetApkSha256 = item.getString("targetApkSha256"),
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

    private fun clearPreparedInstallIfAlreadyApplied() {
        val target = prefs.getInt(KEY_PREPARED_TARGET_CODE, -1)
        if (target == BuildConfig.VERSION_CODE) clearPreparedInstall()
    }

    private fun clearPreparedInstall() {
        prefs.edit()
            .remove(KEY_PREPARED_APK_PATH)
            .remove(KEY_PREPARED_TARGET_CODE)
            .remove(KEY_PREPARED_BLOCKING)
            .remove(KEY_WAITING_INSTALL_PERMISSION)
            .apply()
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
    private fun inspectArchive(apk: File) = if (android.os.Build.VERSION.SDK_INT >= 28) {
        app.packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
    } else {
        app.packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
    } ?: error("Android could not inspect reconstructed APK")

    @Suppress("DEPRECATION")
    private fun verifySigningCertificate(apk: File) {
        val info = inspectArchive(apk)
        val signatures = if (android.os.Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            info.signatures?.toList().orEmpty()
        }
        check(signatures.isNotEmpty()) { "Reconstructed APK has no signing certificate" }
        val digest = MessageDigest.getInstance("SHA-256").digest(signatures.first().toByteArray())
            .joinToString("") { "%02x".format(it) }
        check(digest.equals(RELEASE_CERT_SHA256, ignoreCase = true)) {
            "Reconstructed APK signing certificate mismatch"
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyArchiveVersionCode(apk: File, expected: Int) {
        val info = inspectArchive(apk)
        val actual = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        check(actual == expected.toLong()) { "Reconstructed APK versionCode mismatch: $actual / $expected" }
    }

    private fun launchPackageInstaller(apk: File) {
        if (android.os.Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            prefs.edit().putBoolean(KEY_WAITING_INSTALL_PERMISSION, true).apply()
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
            val blocking = prefs.getBoolean(KEY_PREPARED_BLOCKING, false)
            _state.value = AlmiUpdateState.Message(
                "اسمح لـ ALMI بتثبيت التحديثات، ثم ارجع للتطبيق وسيكمل التثبيت تلقائيًا.",
                "Allow ALMI to install updates, then return to the app and installation will continue automatically.",
                blocking = blocking,
            )
            return
        }
        prefs.edit().remove(KEY_WAITING_INSTALL_PERMISSION).apply()
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        app.startActivity(intent)
    }

    companion object {
        private const val MANIFEST_URL =
            "https://github.com/Malik05255/VibeApp-Fs/releases/latest/download/update-manifest.json"
        private const val RELEASE_CERT_SHA256 =
            "aa85b8be54212a633477c8f644ca783e8fdd6a5b8842b9d69675e9ec3bf96ac9"
        private const val PREFS = "almi_update_state_v1"
        private const val KEY_CACHED_RELEASE = "cached_release"
        private const val KEY_PENDING_ROLLBACK_RELEASE = "pending_rollback_release"
        private const val KEY_PENDING_ROLLBACK_TARGET = "pending_rollback_target"
        private const val KEY_ROLLED_BACK_RELEASE = "rolled_back_release"
        private const val KEY_PREPARED_APK_PATH = "prepared_apk_path"
        private const val KEY_PREPARED_TARGET_CODE = "prepared_target_code"
        private const val KEY_PREPARED_BLOCKING = "prepared_blocking"
        private const val KEY_WAITING_INSTALL_PERMISSION = "waiting_install_permission"
        private const val UPDATE_DIR = "almi_updates"
    }
}
