package com.almi.ai.ui.body

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import io.github.sceneview.utils.SurfaceMirrorer
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Captures the raw Filament render, without Compose controls, so the AI try-on pipeline receives
 * the user's actual digital-twin body rather than a screenshot containing buttons and labels.
 */
suspend fun captureDigitalTwinFrame(
    context: Context,
    surfaceMirrorer: SurfaceMirrorer,
    width: Int = 1024,
    height: Int = 1536,
): String = suspendCancellableCoroutine { continuation ->
    val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    val handler = Handler(Looper.getMainLooper())
    var completed = false

    fun cleanup() {
        handler.post {
            runCatching { surfaceMirrorer.stopMirroring(reader.surface) }
            runCatching { reader.close() }
        }
    }

    reader.setOnImageAvailableListener({ source ->
        if (completed) return@setOnImageAvailableListener
        val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
        completed = true
        try {
            val plane = image.planes.first()
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            val paddedWidth = width + rowPadding / pixelStride
            val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(buffer)
            val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
            if (cropped !== padded) padded.recycle()

            val directory = File(context.filesDir, "digital_twin").apply { mkdirs() }
            val output = File(directory, "almi_twin_${System.currentTimeMillis()}.png")
            FileOutputStream(output).use { stream ->
                check(cropped.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    "digital_twin_png_encode_failed"
                }
            }
            cropped.recycle()
            image.close()
            cleanup()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                output,
            )
            if (continuation.isActive) continuation.resume(uri.toString())
        } catch (error: Throwable) {
            image.close()
            cleanup()
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }, handler)

    continuation.invokeOnCancellation { cleanup() }

    runCatching {
        surfaceMirrorer.startMirroring(reader.surface, width = width, height = height)
    }.onFailure { error ->
        completed = true
        cleanup()
        if (continuation.isActive) continuation.resumeWithException(error)
    }
}
