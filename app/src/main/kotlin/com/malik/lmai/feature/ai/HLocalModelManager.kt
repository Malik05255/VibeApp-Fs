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
 * Owns the on-device model used by مساعد H الرقمي.
 *
 * The model stays outside the APK and outside Android AICore/Gemini Nano. It is
 * downloaded once into app-private no-backup storage and then ordinary chat can run
 * locally without consuming any cloud/provider quota.
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

    /**
     * Quietly prepares the local model on any validated network instead of waiting
     * forever for unmetered Wi-Fi. Existing partial downloads are resumed.
     *
     * v1 used UNMETERED + KEEP, which could leave devices on mobile data permanently
     * queued and therefore force ordinary chat through cloud quota. Cancel that stale
     * work and enqueue the connected-network v2 job exactly once.
     */
    fun scheduleBackgroundDownload() {
        if (isReady()) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
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
        workManager.cancelUniqueWork(LEGACY_UNIQUE_DOWNLOAD_WORK)
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
        const val UNIQUE_DOWNLOAD_WORK = "h-local-model-download-v2-connected"
        private const val LEGACY_UNIQUE_DOWNLOAD_WORK = "h-local-model-download-v1"

        const val MODEL_URL =
            "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/" +
                MODEL_FILE_NAME + "?download=true"

        // Exact file metadata for the Apache-2.0 Qwen2.5 0.5B MediaPipe bundle.
        const val EXPECTED_SIZE_BYTES = 546_660_344L
        const val EXPECTED_SHA256 =
            "e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2"
    }
}
