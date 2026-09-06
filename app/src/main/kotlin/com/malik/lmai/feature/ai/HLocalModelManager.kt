package com.malik.lmai.feature.ai

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns Mohammed's optional on-device fallback model.
 *
 * The model stays outside the APK and outside Android AICore/Gemini Nano. Because the
 * bundle is larger than 500 MiB, preparation is never allowed to compete with normal
 * chat traffic on metered/mobile connectivity.
 */
@Singleton
class HLocalModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val modelDirectory: File
        get() = File(context.noBackupFilesDir, MODEL_DIRECTORY_NAME).apply { mkdirs() }

    val modelFile: File
        get() = File(modelDirectory, MODEL_FILE_NAME)

    private val readyMarker: File
        get() = File(modelDirectory, READY_MARKER_NAME)

    fun isReady(): Boolean =
        modelFile.isFile &&
            modelFile.length() == EXPECTED_SIZE_BYTES &&
            readyMarker.isFile &&
            readyMarker.readText().trim().equals(EXPECTED_SHA256, ignoreCase = true)

    fun invalidate() {
        readyMarker.delete()
    }

    /** Cancel the previous aggressive connected-network download policy after upgrade. */
    fun cancelAggressiveBackgroundDownload() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(LEGACY_CONNECTED_DOWNLOAD_WORK)
        workManager.cancelUniqueWork(LEGACY_UNMETERED_DOWNLOAD_WORK)
    }

    /**
     * Prepare the optional offline model only on unmetered connectivity. Existing partial
     * downloads are resumed, but the transfer must not slow ordinary online replies.
     */
    fun scheduleBackgroundDownload() {
        if (isReady()) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresStorageNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<HLocalModelDownloadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS,
            )
            .build()

        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(LEGACY_CONNECTED_DOWNLOAD_WORK)
        workManager.cancelUniqueWork(LEGACY_UNMETERED_DOWNLOAD_WORK)
        workManager.enqueueUniqueWork(
            UNIQUE_DOWNLOAD_WORK,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val MODEL_ID = "qwen2.5-0.5b-instruct-q8"
        const val MODEL_FILE_NAME = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
        const val MODEL_DIRECTORY_NAME = "h_models"
        const val READY_MARKER_NAME = "qwen2.5-0.5b.ready"
        const val UNIQUE_DOWNLOAD_WORK = "h-local-model-download-v3-unmetered"
        private const val LEGACY_UNMETERED_DOWNLOAD_WORK = "h-local-model-download-v1"
        private const val LEGACY_CONNECTED_DOWNLOAD_WORK = "h-local-model-download-v2-connected"

        const val MODEL_URL =
            "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/" +
                MODEL_FILE_NAME + "?download=true"

        // Exact file metadata for the Apache-2.0 Qwen2.5 0.5B MediaPipe bundle.
        const val EXPECTED_SIZE_BYTES = 546_660_344L
        const val EXPECTED_SHA256 =
            "e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2"
    }
}
