package com.malik.lmai.feature.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resumable, verified background download for محمد's optional offline model.
 * WorkManager constraints keep the large transfer on unmetered connectivity.
 */
class HLocalModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val directory = File(
            applicationContext.noBackupFilesDir,
            HLocalModelManager.MODEL_DIRECTORY_NAME,
        ).apply { mkdirs() }
        val finalFile = File(directory, HLocalModelManager.MODEL_FILE_NAME)
        val partFile = File(directory, HLocalModelManager.MODEL_FILE_NAME + ".part")
        val marker = File(directory, HLocalModelManager.READY_MARKER_NAME)

        if (verified(finalFile)) {
            marker.writeText(HLocalModelManager.EXPECTED_SHA256)
            return@withContext Result.success()
        }

        marker.delete()
        if (finalFile.exists()) finalFile.delete()

        try {
            downloadResumable(partFile)

            if (partFile.length() != HLocalModelManager.EXPECTED_SIZE_BYTES) {
                return@withContext Result.retry()
            }
            if (!sha256(partFile).equals(HLocalModelManager.EXPECTED_SHA256, ignoreCase = true)) {
                partFile.delete()
                return@withContext Result.retry()
            }

            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }
            marker.writeText(HLocalModelManager.EXPECTED_SHA256)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun downloadResumable(partFile: File) {
        var existingBytes = partFile.takeIf { it.exists() }?.length() ?: 0L
        if (existingBytes > HLocalModelManager.EXPECTED_SIZE_BYTES) {
            partFile.delete()
            existingBytes = 0L
        }

        val connection = (URL(HLocalModelManager.MODEL_URL).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 60_000
            requestMethod = "GET"
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "LM_AI-H-Offline/1.0")
            if (existingBytes > 0L) {
                setRequestProperty("Range", "bytes=$existingBytes-")
            }
        }

        try {
            connection.connect()
            val response = connection.responseCode
            val canAppend = existingBytes > 0L && response == HttpURLConnection.HTTP_PARTIAL
            if (response !in 200..299) {
                error("Local model HTTP $response")
            }

            if (existingBytes > 0L && !canAppend) {
                partFile.delete()
                existingBytes = 0L
            }

            FileOutputStream(partFile, canAppend).buffered(1024 * 1024).use { output ->
                connection.inputStream.buffered(1024 * 1024).use { input ->
                    val buffer = ByteArray(1024 * 1024)
                    var total = existingBytes
                    while (true) {
                        if (isStopped) error("Download stopped")
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        total += count
                        if (total > HLocalModelManager.EXPECTED_SIZE_BYTES) {
                            error("Downloaded model exceeds expected size")
                        }
                    }
                    output.flush()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun verified(file: File): Boolean =
        file.isFile &&
            file.length() == HLocalModelManager.EXPECTED_SIZE_BYTES &&
            sha256(file).equals(HLocalModelManager.EXPECTED_SHA256, ignoreCase = true)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).buffered(1024 * 1024).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
