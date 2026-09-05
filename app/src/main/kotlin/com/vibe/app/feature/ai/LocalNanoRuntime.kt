package com.vibe.app.feature.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.generationConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

/**
 * Single process-wide owner for the ML Kit Gemini Nano client.
 *
 * Routing needs to know when a device explicitly reports Nano as unsupported
 * before it advertises the local provider. Inference uses the same client so
 * availability checks and execution cannot drift apart.
 */
@Singleton
class LocalNanoRuntime @Inject constructor() {

    private val model by lazy {
        Generation.getClient(generationConfig {})
    }

    @Volatile
    private var explicitlyUnsupported: Boolean = false

    /**
     * Returns false only when ML Kit explicitly reports UNAVAILABLE.
     * Transient check failures are treated as unknown/possibly usable so a
     * temporary service issue does not permanently hide local inference.
     */
    suspend fun isSupportedByDevice(): Boolean {
        if (explicitlyUnsupported) return false

        return try {
            val status = model.checkStatus()
            if (status == FeatureStatus.UNAVAILABLE) {
                explicitlyUnsupported = true
                false
            } else {
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            true
        }
    }

    suspend fun generateText(prompt: String): String {
        ensureReady()
        val response = model.generateContent(prompt)
        return response.candidates
            .map { it.text }
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()
    }

    private suspend fun ensureReady() {
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> return

            FeatureStatus.UNAVAILABLE -> {
                explicitlyUnsupported = true
                throw LocalNanoUnavailableException()
            }

            else -> {
                var completed = false

                model.download().collect { status ->
                    when (status) {
                        is DownloadStatus.DownloadFailed -> throw status.e
                        is DownloadStatus.DownloadCompleted -> completed = true
                        else -> Unit
                    }
                }

                if (!completed && model.checkStatus() != FeatureStatus.AVAILABLE) {
                    throw IllegalStateException(
                        "Gemini Nano could not be prepared on this device."
                    )
                }
            }
        }
    }
}

class LocalNanoUnavailableException : IllegalStateException(
    "Gemini Nano is not available on this device."
)
